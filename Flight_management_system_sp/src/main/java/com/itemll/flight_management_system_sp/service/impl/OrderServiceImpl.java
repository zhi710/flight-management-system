package com.itemll.flight_management_system_sp.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.common.util.FfpNoResolver;
import com.itemll.flight_management_system_sp.common.util.IdNumberValidator;
import com.itemll.flight_management_system_sp.dto.OrderChangeDTO;
import com.itemll.flight_management_system_sp.dto.OrderCreateDTO;
import com.itemll.flight_management_system_sp.dto.OrderRefundDTO;
import com.itemll.flight_management_system_sp.entity.*;
import com.itemll.flight_management_system_sp.mapper.*;
import com.itemll.flight_management_system_sp.security.ConcurrencyHelper;
import com.itemll.flight_management_system_sp.service.FeeRuleService;
import com.itemll.flight_management_system_sp.service.OrderExpiryService;
import com.itemll.flight_management_system_sp.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单服务实现
 *
 * <h3>防高并发方案（三重保障）：</h3>
 * <ol>
 *   <li><b>Redis 分布式锁</b> — 同一航班+舱位的并发请求串行化，锁粒度 flight:{id}:{cabinClass}，
 *       超时 5 秒，获取失败直接返回"请稍后重试"</li>
 *   <li><b>数据库乐观锁</b> — flight_cabin 表的 version 字段，CAS 更新座位数</li>
 *   <li><b>SQL 条件扣减</b> — UPDATE ... SET available_seats = available_seats - N
 *       WHERE available_seats >= N AND version = #{oldVersion}，数据库层面保证不会扣成负数</li>
 * </ol>
 * <p>三层防护确保在高并发场景下不会超卖。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderPassengerMapper orderPassengerMapper;
    private final OrderServiceItemMapper orderServiceItemMapper;
    private final FlightCabinMapper flightCabinMapper;
    private final FlightMapper flightMapper;
    private final OrderChangeMapper orderChangeMapper;
    private final OrderRefundMapper orderRefundMapper;
    private final ConcurrencyHelper concurrencyHelper;
    private final SsrCodeMapper ssrCodeMapper;
    private final SpecialServiceRequestMapper ssrRequestMapper;
    /** 订单取消/超时过期的唯一实现，见该类注释说明为何独立成 Spring Bean */
    private final OrderExpiryService orderExpiryService;
    /** 退票/改签手续费的计算真源（sys_config 的结构化规则），不要再在业务里写死比例 */
    private final FeeRuleService feeRuleService;
    /** 常旅客号只由服务端解析，前端传入值与里程归属无关 */
    private final FfpNoResolver ffpNoResolver;
    /** 实名档案的读取方：未实名不允许购票，且订单必须含本人 */
    private final UserMapper userMapper;

    /** 改签单状态：待审核 */
    private static final String CHANGE_STATUS_PENDING = "PENDING";
    /** 改签单状态：审核已通过、等待旅客支付改签费用/差价 */
    private static final String CHANGE_STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
    /** 退票单状态：待审核 */
    private static final String REFUND_STATUS_PENDING = "PENDING";
    /** 允许发起退票的订单状态 */
    private static final Set<String> REFUNDABLE_ORDER_STATUS =
            Set.of("PAID", "ISSUED", "CHECKED_IN");
    /** 允许发起改签的订单状态 */
    private static final Set<String> CHANGEABLE_ORDER_STATUS =
            Set.of("PAID", "ISSUED", "CHECKED_IN");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createOrder(OrderCreateDTO dto, Long userId) {
        Long flightId = Long.parseLong(dto.getFlightId());
        String cabinClass = dto.getCabinClass();
        int passengerCount = dto.getPassengers().size();

        // 证件号前置校验：必须早于扣座位，否则非法证件号会白白消耗一次库存与锁
        for (OrderCreateDTO.PassengerDTO p : dto.getPassengers()) {
            String docError = IdNumberValidator.validate(p.getIdType(), p.getIdNumber());
            if (docError != null) {
                throw new BusinessException(ErrorCode.DOCUMENT_INVALID,
                        "旅客「" + p.getName() + "」的" + docError);
            }
        }

        // ===== 实名前置校验（同样早于扣座位）=====
        // 1) 账号必须先完成实名（在个人中心填写姓名 + 证件号），否则无法购票；
        // 2) 订单中必须包含本人，即存在一位乘客的姓名 + 证件类型 + 证件号
        //    与实名档案完全一致。其余乘客允许代亲友购买，但已各自通过上面的格式校验。
        User buyer = userMapper.selectById(userId);
        if (buyer == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        if (buyer.getIdNumber() == null || buyer.getIdNumber().isBlank()) {
            throw new BusinessException(ErrorCode.REALNAME_REQUIRED,
                    "请先在个人中心完成实名认证，再购买机票");
        }
        String selfType = buyer.getIdType() == null ? "" : buyer.getIdType().trim();
        String selfDoc = buyer.getIdNumber().trim();
        String selfName = buyer.getName() == null ? "" : buyer.getName().trim();
        boolean containsSelf = false;
        for (OrderCreateDTO.PassengerDTO p : dto.getPassengers()) {
            String pType = p.getIdType() == null ? "" : p.getIdType().trim();
            String pDoc = p.getIdNumber() == null ? "" : p.getIdNumber().trim();
            String pName = p.getName() == null ? "" : p.getName().trim();
            // 三者同时一致才算本人：只比证件号会放过姓名填错（票面姓名错上不了机），
            // 只比姓名又会把同名不同人当成同一个人。
            if (selfDoc.equalsIgnoreCase(pDoc)
                    && selfType.equalsIgnoreCase(pType)
                    && selfName.equals(pName)) {
                containsSelf = true;
                break;
            }
        }
        if (!containsSelf) {
            throw new BusinessException(ErrorCode.SELF_PASSENGER_REQUIRED,
                    "订单中必须包含本人，请填写与实名信息一致的姓名和证件号");
        }

        // ===== 第一层：Redis 分布式锁 =====
        return concurrencyHelper.executeWithLock(flightId, cabinClass, () -> {
            // 查询航班舱位
            FlightCabin cabin = flightCabinMapper.selectOne(
                    new LambdaQueryWrapper<FlightCabin>()
                            .eq(FlightCabin::getFlightId, flightId)
                            .eq(FlightCabin::getCabinClass, cabinClass)
                            .eq(FlightCabin::getDeleted, 0));

            if (cabin == null) {
                throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "舱位不存在");
            }

            // 库存预检
            if (cabin.getAvailableSeats() < passengerCount) {
                throw new BusinessException(ErrorCode.FLIGHT_FULL, "座位不足，剩余 " + cabin.getAvailableSeats() + " 个");
            }

            // ===== 第二层 + 第三层：乐观锁 + SQL 条件扣减 =====
            int rows = flightCabinMapper.decrementSeats(cabin.getId(), passengerCount, cabin.getVersion());
            if (rows == 0) {
                throw new BusinessException(ErrorCode.FLIGHT_FULL, "座位已被其他用户抢占，请重新选择");
            }

            // 查询航班信息
            Flight flight = flightMapper.selectById(flightId);

            // 计算金额
            BigDecimal fare = cabin.getFare().multiply(BigDecimal.valueOf(passengerCount));
            BigDecimal tax = cabin.getTax().multiply(BigDecimal.valueOf(passengerCount));
            BigDecimal serviceFee = BigDecimal.ZERO;

            // 计算附加服务费（SSR 类付费服务不在此处计费，需后台审核后追加）
            if (dto.getServices() != null && !dto.getServices().isEmpty()) {
                for (OrderCreateDTO.ServiceDTO svc : dto.getServices()) {
                    if ("SSR".equals(svc.getType())) {
                        // SSR 服务：查阅 ssr_code 表获取费用
                        SsrCode ssrCodeEntity = ssrCodeMapper.selectOne(
                                new LambdaQueryWrapper<SsrCode>().eq(SsrCode::getCode, svc.getCode()));
                        if (ssrCodeEntity != null && ssrCodeEntity.getExtraFee().compareTo(BigDecimal.ZERO) > 0) {
                            // 付费 SSR → 不加入订单总额，等管理员审核通过后再追加
                            continue;
                        }
                    }
                    BigDecimal unitPrice = getServicePrice(svc.getCode());
                    serviceFee = serviceFee.add(unitPrice.multiply(
                            BigDecimal.valueOf(svc.getQuantity() != null ? svc.getQuantity() : 1)));
                }
            }

            BigDecimal totalAmount = fare.add(tax).add(serviceFee);

            // 生成订单号
            String orderId = "ORD" + IdUtil.getSnowflakeNextIdStr();
            String orderNo = "ST" + System.currentTimeMillis();
            String pnr = RandomUtil.randomStringUpper(6);

            // 创建订单
            Order order = new Order();
            order.setOrderId(orderId);
            order.setOrderNo(orderNo);
            order.setPnr(pnr);
            order.setUserId(userId);
            order.setFlightId(flightId);
            order.setFlightNo(flight.getFlightNo());
            order.setCabinClass(cabinClass);
            order.setSearchId(dto.getSearchId());
            order.setContactName(dto.getContactInfo().getName());
            order.setContactPhone(dto.getContactInfo().getPhone());
            order.setContactEmail(dto.getContactInfo().getEmail());
            order.setStatus("PENDING_PAYMENT");
            order.setFare(fare);
            order.setTax(tax);
            order.setServiceFee(serviceFee);
            order.setTotalAmount(totalAmount);
            order.setExpireTime(LocalDateTime.now().plusMinutes(15)); // 15 分钟内支付
            orderMapper.insert(order);

            // 保存旅客信息
            List<Map<String, Object>> passengerResults = new ArrayList<>();
            for (int i = 0; i < dto.getPassengers().size(); i++) {
                OrderCreateDTO.PassengerDTO p = dto.getPassengers().get(i);
                OrderPassenger op = new OrderPassenger();
                op.setOrderId(order.getId());
                op.setPassengerName(p.getName());
                op.setGender(p.getGender());
                op.setIdType(p.getIdType());
                op.setIdNumber(p.getIdNumber());
                op.setPhone(p.getPhone());
                op.setEmail(p.getEmail());
                op.setPassengerType(p.getPassengerType());
                // 常旅客号由服务端解析（证件号 → 手机号 → 本人姓名 依次命中已有号），忽略前端传值：
                // 该号决定这单里程累积给谁，允许用户填写等于允许把里程刷到任意账号。
                // 下单流程刻意「只复用、不签发」—— 旅客若从未建档，说明其号尚未落库，
                // 此刻凭空发号既没地方存、又会消耗序号，导致后续发号撞车。
                op.setFrequentFlyerNo(ffpNoResolver.lookup(
                        userId, p.getName(), p.getPhone(), p.getIdNumber()));
                op.setCheckinStatus("NOT_CHECKED_IN");
                // 票号必须落库：此前只塞进下面的响应 Map，order_passenger.ticket_no 一直是 NULL，
                // 导致订单详情与电子登机牌都取不到票号（只能显示占位符）
                op.setTicketNo("999-" + RandomUtil.randomNumbers(10));
                orderPassengerMapper.insert(op);

                Map<String, Object> pr = new HashMap<>();
                pr.put("orderPassengerId", String.valueOf(op.getId()));
                pr.put("name", p.getName());
                pr.put("ticketNo", op.getTicketNo());
                passengerResults.add(pr);
            }

            // 保存附加服务（付费 SSR 类不计入 service_item，单独创建审核记录）
            boolean hasPaidSsr = false;
            if (dto.getServices() != null) {
                for (OrderCreateDTO.ServiceDTO svc : dto.getServices()) {
                    if ("SSR".equals(svc.getType())) {
                        SsrCode ssc = ssrCodeMapper.selectOne(
                                new LambdaQueryWrapper<SsrCode>().eq(SsrCode::getCode, svc.getCode()));
                        if (ssc != null && ssc.getExtraFee().compareTo(BigDecimal.ZERO) > 0) {
                            // 付费 SSR → 创建 PENDING 审核记录，不加入订单服务项
                            SpecialServiceRequest ssrReq = new SpecialServiceRequest();
                            ssrReq.setOrderId(order.getId());
                            ssrReq.setPassengerName(dto.getContactInfo().getName());
                            ssrReq.setSsrCode(svc.getCode());
                            ssrReq.setStatus("PENDING");
                            ssrReq.setRemark("下单时申请，费用¥" + ssc.getExtraFee() + "，待管理员审核");
                            ssrRequestMapper.insert(ssrReq);
                            hasPaidSsr = true;
                            continue;
                        }
                        // 免费 SSR → 自动通过
                        SpecialServiceRequest ssrReq = new SpecialServiceRequest();
                        ssrReq.setOrderId(order.getId());
                        ssrReq.setPassengerName(dto.getContactInfo().getName());
                        ssrReq.setSsrCode(svc.getCode());
                        ssrReq.setStatus("APPROVED");
                        ssrReq.setRemark("免费服务，自动确认");
                        ssrRequestMapper.insert(ssrReq);
                    }
                    // 非 SSR 或免费 SSR → 正常写入 order_service_item
                    OrderServiceItem item = new OrderServiceItem();
                    item.setOrderId(order.getId());
                    item.setServiceType(svc.getType());
                    item.setServiceCode(svc.getCode());
                    item.setQuantity(svc.getQuantity() != null ? svc.getQuantity() : 1);
                    item.setUnitPrice(getServicePrice(svc.getCode()));
                    item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                    item.setPassengerIndex(svc.getPassengerIndex() != null ? svc.getPassengerIndex() : 0);
                    orderServiceItemMapper.insert(item);
                }
            }
            if (hasPaidSsr) {
                order.setSsrFlag(1);
                orderMapper.updateById(order);
            }

            log.info("订单创建成功: orderId={}, flightNo={}, passengers={}, total={}",
                    orderId, flight.getFlightNo(), passengerCount, totalAmount);

            // 组装返回
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", order.getId());
            result.put("orderId", orderId);
            result.put("orderNo", orderNo);
            result.put("pnr", pnr);
            result.put("status", "PENDING_PAYMENT");
            result.put("expireAt", order.getExpireTime());

            Map<String, Object> flightInfo = new HashMap<>();
            flightInfo.put("flightNo", flight.getFlightNo());
            Map<String, Object> dep = new HashMap<>();
            dep.put("airport", flight.getDepartureAirport());
            dep.put("terminal", flight.getDepartureTerminal());
            dep.put("dateTime", flight.getDepartureTime());
            flightInfo.put("departure", dep);
            Map<String, Object> arr = new HashMap<>();
            arr.put("airport", flight.getArrivalAirport());
            arr.put("terminal", flight.getArrivalTerminal());
            arr.put("dateTime", flight.getArrivalTime());
            flightInfo.put("arrival", arr);
            result.put("flight", flightInfo);

            result.put("passengers", passengerResults);

            Map<String, Object> price = new HashMap<>();
            price.put("fare", fare);
            price.put("tax", tax);
            price.put("services", serviceFee);
            price.put("total", totalAmount);
            result.put("price", price);

            return result;
        });
    }

    @Override
    public Map<String, Object> getOrderDetail(String orderId, Long userId) {
        Order order = findOrderByIdAndUser(orderId, userId);

        // 自动过期：待支付订单超时后自动取消并回退座位
        orderExpiryService.expireIfNeeded(order);

        List<OrderPassenger> passengers = orderPassengerMapper.selectList(
                new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, order.getId()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getOrderId());
        result.put("orderNo", order.getOrderNo());
        result.put("pnr", order.getPnr());
        result.put("status", order.getStatus());
        result.put("createdAt", order.getCreateTime());
        result.put("paidAt", order.getPaidTime());
        result.put("expireAt", order.getExpireTime());

        // 航班信息
        Flight flight = flightMapper.selectById(order.getFlightId());
        if (flight != null) {
            Map<String, Object> fi = new HashMap<>();
            fi.put("flightId", flight.getId().toString());
            fi.put("flightNo", flight.getFlightNo());
            fi.put("status", flight.getStatus());

            Map<String, Object> dep = new HashMap<>();
            dep.put("airport", flight.getDepartureAirport());
            dep.put("airportName", getAirportName(flight.getDepartureAirport()));
            dep.put("terminal", flight.getDepartureTerminal());
            dep.put("gate", flight.getDepartureGate());
            dep.put("dateTime", flight.getDepartureTime());
            fi.put("departure", dep);

            Map<String, Object> arr = new HashMap<>();
            arr.put("airport", flight.getArrivalAirport());
            arr.put("airportName", getAirportName(flight.getArrivalAirport()));
            arr.put("terminal", flight.getArrivalTerminal());
            arr.put("dateTime", flight.getArrivalTime());
            fi.put("arrival", arr);

            result.put("flight", fi);
        }

        // 旅客信息
        result.put("passengers", passengers.stream().map(p -> {
            Map<String, Object> pm = new HashMap<>();
            pm.put("name", p.getPassengerName());
            pm.put("idType", p.getIdType());
            pm.put("idNumber", maskIdNumber(p.getIdNumber()));
            pm.put("ticketNo", p.getTicketNo());
            pm.put("seat", p.getSeatRow() != null ? p.getSeatRow() + p.getSeatColumn() : null);
            pm.put("checkinStatus", p.getCheckinStatus());
            // 常旅客号：支付成功页据此提示「本单里程将累积到哪个号」。
            // 号由服务端在解析时写入订单旅客，不是用户填的，本人自看无隐私问题。
            pm.put("frequentFlyerNo", p.getFrequentFlyerNo());
            return pm;
        }).collect(Collectors.toList()));

        // 价格
        Map<String, Object> price = new LinkedHashMap<>();
        price.put("fare", order.getFare());
        price.put("tax", order.getTax());
        price.put("serviceFee", order.getServiceFee());
        price.put("total", order.getTotalAmount());
        result.put("price", price);

        // 可用操作
        List<String> actions = new ArrayList<>();
        if ("PAID".equals(order.getStatus()) || "ISSUED".equals(order.getStatus())) {
            actions.addAll(List.of("CHECKIN", "CHANGE", "REFUND", "INVOICE"));
        }
        if ("CHECKED_IN".equals(order.getStatus())) {
            actions.addAll(List.of("CANCEL_CHECKIN", "CHANGE", "REFUND", "INVOICE"));
        }
        result.put("actions", actions);

        return result;
    }

    @Override
    public PageResult<Map<String, Object>> getOrderList(Long userId, String status, String startDate,
                                                         String endDate, String keyword, int page, int pageSize) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getDeleted, 0)
                .like(keyword != null, Order::getFlightNo, keyword)
                .orderByDesc(Order::getCreateTime);

        // 状态筛选：支持组合状态
        if (status != null && !status.isEmpty()) {
            switch (status) {
                case "PAID" -> wrapper.in(Order::getStatus, "PAID", "ISSUED", "CHECKED_IN", "BOARDING", "DEPARTED", "ARRIVED");
                case "CANCELLED" -> wrapper.in(Order::getStatus, "CANCELLED", "REFUNDED");
                default -> wrapper.eq(Order::getStatus, status);
            }
        }

        Page<Order> pageObj = new Page<>(page, pageSize);
        Page<Order> result = orderMapper.selectPage(pageObj, wrapper);

        // 自动过期：列表中待支付超时的订单自动取消并回退座位
        if (status == null || status.isEmpty() || "PENDING_PAYMENT".equals(status)) {
            result.getRecords().forEach(orderExpiryService::expireIfNeeded);
        }

        List<Map<String, Object>> list = result.getRecords().stream().map(order -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderId", order.getOrderId());
            m.put("orderNo", order.getOrderNo());
            m.put("flightNo", order.getFlightNo());
            m.put("cabinClass", order.getCabinClass());
            m.put("status", order.getStatus());
            m.put("expireAt", order.getExpireTime());
            m.put("createdAt", order.getCreateTime());
            m.put("paidAt", order.getPaidTime());

            // 价格信息
            Map<String, Object> price = new LinkedHashMap<>();
            price.put("fare", order.getFare());
            price.put("total", order.getTotalAmount());
            price.put("serviceFee", order.getServiceFee());
            m.put("price", price);

            // 航班信息
            Flight flight = flightMapper.selectById(order.getFlightId());
            if (flight != null) {
                Map<String, Object> fi = new HashMap<>();
                fi.put("flightNo", flight.getFlightNo());
                Map<String, Object> dep = new HashMap<>();
                dep.put("airport", flight.getDepartureAirport());
                fi.put("departure", dep);
                Map<String, Object> arr = new HashMap<>();
                arr.put("airport", flight.getArrivalAirport());
                fi.put("arrival", arr);
                m.put("flight", fi);
            }

            // 旅客姓名
            List<OrderPassenger> paxList = orderPassengerMapper.selectList(
                    new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, order.getId()));
            m.put("passengers", paxList.stream().map(p -> {
                Map<String, Object> pm = new HashMap<>();
                pm.put("name", p.getPassengerName());
                return pm;
            }).collect(Collectors.toList()));

            return m;
        }).collect(Collectors.toList());

        return PageResult.of(list, page, pageSize, result.getTotal());
    }

    @Override
    @Transactional
    public void cancelOrder(String orderId, Long userId, String reason) {
        Order order = findOrderByIdAndUser(orderId, userId);
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.REFUND_CHANGE_NOT_ALLOWED, "当前状态不允许取消");
        }

        // 取消与座位回退统一交给 OrderExpiryService：
        // 原实现在这里自行 updateById 后读改写座位，并发取消会把座位重复加回库存（虚增）；
        // 现在用条件更新 + 原子回退，只有真正完成取消的那次调用才回退座位。
        String cancelReason = (reason == null || reason.isBlank()) ? "用户主动取消" : reason;
        if (!orderExpiryService.cancelPendingOrder(order, cancelReason)) {
            // 极小概率：与超时清理任务或支付回调并发，订单已不处于待支付
            throw new BusinessException(ErrorCode.CONFLICT, "订单状态已变更，请刷新后重试");
        }
    }

    @Override
    @Transactional
    public Map<String, Object> changeOrder(String orderId, Long userId, OrderChangeDTO dto) {
        Order order = findOrderByIdAndUser(orderId, userId);
        if (!CHANGEABLE_ORDER_STATUS.contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.REFUND_CHANGE_NOT_ALLOWED, "当前状态不允许改签");
        }

        // 改签旅客数：部分改签按传入的旅客数，否则按整单人数
        int changeCount = (dto.getPassengers() != null && !dto.getPassengers().isEmpty())
                ? dto.getPassengers().size()
                : orderPassengerMapper.selectList(
                        new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, order.getId())).size();
        if (changeCount <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单没有可改签的旅客");
        }

        // 同一订单同时只允许一笔处理中的改签申请：否则多笔申请审核通过后会各自扣一次
        // 新航班库存、各自改一次航班，造成库存与行程错乱。
        Long pendingChanges = orderChangeMapper.selectCount(new LambdaQueryWrapper<OrderChange>()
                .eq(OrderChange::getOrderId, order.getId())
                .in(OrderChange::getStatus, CHANGE_STATUS_PENDING, CHANGE_STATUS_PENDING_PAYMENT));
        if (pendingChanges != null && pendingChanges > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该订单已有处理中的改签申请，请等待处理完成");
        }

        // 支持输入航班号（如 CA1501）或数字 ID，自动解析
        Long newFlightId = parseFlightId(dto.getNewFlightId());
        Flight newFlight = flightMapper.selectById(newFlightId);
        if (newFlight == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "改签目标航班不存在");
        }
        String newCabinClass = dto.getNewCabinClass();
        if (newFlightId.equals(order.getFlightId()) && newCabinClass != null
                && newCabinClass.equals(order.getCabinClass())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "改签目标与原行程相同，无需改签");
        }

        FlightCabin newCabin = flightCabinMapper.selectOne(new LambdaQueryWrapper<FlightCabin>()
                .eq(FlightCabin::getFlightId, newFlightId)
                .eq(FlightCabin::getCabinClass, newCabinClass));
        if (newCabin == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "改签目标航班不存在该舱位：" + newCabinClass);
        }
        if (newCabin.getAvailableSeats() == null || newCabin.getAvailableSeats() < changeCount) {
            throw new BusinessException(ErrorCode.FLIGHT_FULL, "改签目标航班该舱位余票不足，请更换航班或舱位");
        }

        // ---------- 手续费：按配置规则分档，不再写死 5% ----------
        Flight originalFlight = flightMapper.selectById(order.getFlightId());
        BigDecimal changeRatio = feeRuleService.changeFeeRatio(
                originalFlight == null ? null : originalFlight.getDepartureTime());
        if (changeRatio.compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessException(ErrorCode.REFUND_CHANGE_NOT_ALLOWED,
                    "按改签规则，该航班当前时间不可改签");
        }
        BigDecimal changeFee = feeRuleService.feeOf(zeroIfNull(order.getTotalAmount()), changeRatio);

        // ---------- 差价：新舱位票面总价 − 已支付的票面总额（fare + tax） ----------
        // 注意用「票面总额」而不是 order.totalAmount：后者含服务费，服务费不随舱位变化，
        // 计入差价会凭空多收一笔钱。
        BigDecimal paidTicketAmount = zeroIfNull(order.getFare()).add(zeroIfNull(order.getTax()));
        BigDecimal newTicketAmount = zeroIfNull(newCabin.getTotalPrice())
                .multiply(BigDecimal.valueOf(changeCount));
        BigDecimal fareDiff = newTicketAmount.subtract(paidTicketAmount);

        // 净额可为负：手续费 + 差价 < 0 表示本次改签反而应退钱给旅客
        BigDecimal netAmount = changeFee.add(fareDiff);
        BigDecimal payableAmount = netAmount.max(BigDecimal.ZERO);

        OrderChange change = new OrderChange();
        change.setChangeId("CHG" + IdUtil.getSnowflakeNextIdStr());
        change.setOrderId(order.getId());
        change.setOriginalFlightId(order.getFlightId());
        change.setNewFlightId(newFlightId);
        change.setOriginalCabinClass(order.getCabinClass());
        change.setNewCabinClass(newCabinClass);
        change.setSegmentIndex(dto.getSegmentIndex() != null ? dto.getSegmentIndex() : 0);
        change.setChangeFee(changeFee);
        change.setFareDiff(fareDiff);
        // total_fee 存「旅客尚需支付的金额」，恒 >= 0；应退金额由 -(changeFee + fareDiff) 推出
        change.setTotalFee(payableAmount);
        change.setStatus(CHANGE_STATUS_PENDING);
        orderChangeMapper.insert(change);

        log.info("改签申请已提交: changeId={}, orderId={}, passengers={}, changeFee={}, fareDiff={}, payable={}",
                change.getChangeId(), orderId, changeCount, changeFee, fareDiff, payableAmount);

        Map<String, Object> result = new HashMap<>();
        result.put("changeId", change.getChangeId());
        result.put("status", CHANGE_STATUS_PENDING);
        Map<String, Object> originalFlightInfo = new HashMap<>();
        originalFlightInfo.put("flightNo", order.getFlightNo());
        result.put("originalFlight", originalFlightInfo);
        Map<String, Object> fee = new HashMap<>();
        fee.put("changeFee", changeFee);
        fee.put("fareDiff", fareDiff);
        fee.put("total", payableAmount);
        // 净额为负时告知前端应退金额，旅客端可据此提示"改签后另退差价 ¥x"
        fee.put("refundable", netAmount.signum() < 0 ? netAmount.negate() : BigDecimal.ZERO);
        result.put("fee", fee);
        return result;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @Override
    @Transactional
    public Map<String, Object> refundOrder(String orderId, Long userId, OrderRefundDTO dto) {
        Order order = findOrderByIdAndUser(orderId, userId);
        if (!REFUNDABLE_ORDER_STATUS.contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.REFUND_CHANGE_NOT_ALLOWED, "当前状态不允许退票");
        }

        // 同一订单同时只允许一笔处理中的退票申请。否则两笔申请都审核通过时会各退一次钱 ——
        // 这是资金侧最危险的一类漏洞，必须在申请入口就挡住。
        Long pendingRefunds = orderRefundMapper.selectCount(new LambdaQueryWrapper<OrderRefund>()
                .eq(OrderRefund::getOrderId, order.getId())
                .in(OrderRefund::getStatus, REFUND_STATUS_PENDING, "APPROVED"));
        if (pendingRefunds != null && pendingRefunds > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该订单已有处理中的退票申请，请等待处理完成");
        }

        // 手续费按 sys_config.default_refund_rule 分档计算（改造前是写死的 10%，
        // 配好的规则表从未被读取过）。退票费在「申请时」锁定，审核时不再重算，
        // 避免旅客申请时看到 5%、排队几小时后被按 100% 扣光。
        Flight flight = flightMapper.selectById(order.getFlightId());
        BigDecimal refundRatio = feeRuleService.refundFeeRatio(
                flight == null ? null : flight.getDepartureTime());
        if (refundRatio.compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessException(ErrorCode.REFUND_CHANGE_NOT_ALLOWED,
                    "按退票规则，该航班当前时间不可退票");
        }

        BigDecimal totalAmount = zeroIfNull(order.getTotalAmount());
        BigDecimal refundFee = feeRuleService.feeOf(totalAmount, refundRatio);
        BigDecimal refundAmount = totalAmount.subtract(refundFee);

        OrderRefund refund = new OrderRefund();
        refund.setRefundId("REF" + IdUtil.getSnowflakeNextIdStr());
        refund.setOrderId(order.getId());
        refund.setReason(dto.getReason());
        refund.setRefundAmount(refundAmount);
        refund.setRefundFee(refundFee);
        refund.setStatus(REFUND_STATUS_PENDING);
        orderRefundMapper.insert(refund);

        log.info("退票申请已提交: refundId={}, orderId={}, ratio={}, refundFee={}, refundAmount={}",
                refund.getRefundId(), orderId, refundRatio, refundFee, refundAmount);

        Map<String, Object> result = new HashMap<>();
        result.put("refundId", refund.getRefundId());
        result.put("status", REFUND_STATUS_PENDING);
        result.put("refundAmount", refundAmount);
        result.put("refundFee", refundFee);
        result.put("refundAccount", "原路返回");
        result.put("estimatedArrival", "1-7个工作日");
        return result;
    }

    @Override
    public Map<String, Object> getChangeStatus(String orderId, String changeId, Long userId) {
        Order order = findOrderByIdAndUser(orderId, userId);

        OrderChange change = orderChangeMapper.selectOne(
                new LambdaQueryWrapper<OrderChange>()
                        .eq(OrderChange::getChangeId, changeId));
        if (change == null || !change.getOrderId().equals(order.getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "改签记录不存在");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changeId", change.getChangeId());
        result.put("status", change.getStatus());
        result.put("originalFlightId", change.getOriginalFlightId());
        result.put("newFlightId", change.getNewFlightId());
        result.put("changeFee", change.getChangeFee());
        result.put("fareDiff", change.getFareDiff());
        result.put("totalFee", change.getTotalFee());
        BigDecimal netAmount = zeroIfNull(change.getChangeFee()).add(zeroIfNull(change.getFareDiff()));
        result.put("refundable", netAmount.signum() < 0 ? netAmount.negate() : BigDecimal.ZERO);
        result.put("createTime", change.getCreateTime());
        return result;
    }

    @Override
    public Map<String, Object> getRefundStatus(String orderId, String refundId, Long userId) {
        Order order = findOrderByIdAndUser(orderId, userId);

        OrderRefund refund = orderRefundMapper.selectOne(
                new LambdaQueryWrapper<OrderRefund>()
                        .eq(OrderRefund::getRefundId, refundId));
        if (refund == null || !refund.getOrderId().equals(order.getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "退票记录不存在");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("refundId", refund.getRefundId());
        result.put("status", refund.getStatus());
        result.put("refundAmount", refund.getRefundAmount());
        result.put("refundFee", refund.getRefundFee());
        result.put("refundAccount", "原路返回");
        result.put("estimatedArrival", "1-7个工作日");
        result.put("createTime", refund.getCreateTime());
        return result;
    }

    @Override
    public List<Map<String, Object>> listChanges(Long userId) {
        List<Long> orderIds = getUserOrderIds(userId);
        if (orderIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<OrderChange> changes = orderChangeMapper.selectList(
                new LambdaQueryWrapper<OrderChange>()
                        .in(OrderChange::getOrderId, orderIds)
                        .orderByDesc(OrderChange::getCreateTime));

        Map<Long, Order> orderMap = orderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, o -> o));

        List<Map<String, Object>> result = new ArrayList<>();
        for (OrderChange c : changes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("changeId", c.getChangeId());
            m.put("orderId", String.valueOf(c.getOrderId()));
            Order o = orderMap.get(c.getOrderId());
            m.put("orderNo", o != null ? o.getOrderNo() : null);
            m.put("newFlightNo", resolveFlightNo(c.getNewFlightId()));
            m.put("newCabinClass", c.getNewCabinClass());
            m.put("changeFee", c.getChangeFee());
            m.put("fareDiff", c.getFareDiff());
            m.put("totalFee", c.getTotalFee());
            // 净额 = 手续费 + 差价，为负表示本次改签不但不用补款、还应退旅客钱。
            // total_fee 只在「需补款」时为正，应退金额必须由前端单独展示，否则旅客看不到这笔退款。
            BigDecimal netAmount = zeroIfNull(c.getChangeFee()).add(zeroIfNull(c.getFareDiff()));
            m.put("refundable", netAmount.signum() < 0 ? netAmount.negate() : BigDecimal.ZERO);
            m.put("status", c.getStatus());
            m.put("createTime", c.getCreateTime() != null ? c.getCreateTime().toString() : null);
            result.add(m);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> listRefunds(Long userId) {
        List<Long> orderIds = getUserOrderIds(userId);
        if (orderIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<OrderRefund> refunds = orderRefundMapper.selectList(
                new LambdaQueryWrapper<OrderRefund>()
                        .in(OrderRefund::getOrderId, orderIds)
                        .orderByDesc(OrderRefund::getCreateTime));

        Map<Long, Order> orderMap = orderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, o -> o));

        List<Map<String, Object>> result = new ArrayList<>();
        for (OrderRefund r : refunds) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("refundId", r.getRefundId());
            m.put("orderId", String.valueOf(r.getOrderId()));
            Order o = orderMap.get(r.getOrderId());
            m.put("orderNo", o != null ? o.getOrderNo() : null);
            m.put("flightNo", o != null ? o.getFlightNo() : null);
            m.put("reason", r.getReason());
            m.put("refundAmount", r.getRefundAmount());
            m.put("refundFee", r.getRefundFee());
            m.put("status", r.getStatus());
            // 退款到账凭证：让旅客自己也能看到「钱确实退了、渠道流水是多少」
            m.put("refundTime", r.getRefundTime() != null ? r.getRefundTime().toString() : null);
            m.put("channelRefundNo", r.getChannelRefundNo());
            m.put("failReason", r.getFailReason());
            m.put("createTime", r.getCreateTime() != null ? r.getCreateTime().toString() : null);
            result.add(m);
        }
        return result;
    }

    // ==================== 私有方法 ====================

    /** 查询用户全部订单 ID */
    private List<Long> getUserOrderIds(Long userId) {
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .eq(Order::getDeleted, 0));
        return orders.stream().map(Order::getId).collect(Collectors.toList());
    }

    /** 根据航班 ID 解析航班号 */
    private String resolveFlightNo(Long flightId) {
        if (flightId == null) return null;
        Flight flight = flightMapper.selectById(flightId);
        return flight != null ? flight.getFlightNo() : null;
    }

    /**
     * 根据业务订单号 + 用户ID 查询订单（统一入口，避免重复代码）
     */
    private Order findOrderByIdAndUser(String orderId, Long userId) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderId, orderId)
                        .eq(Order::getUserId, userId)
                        .eq(Order::getDeleted, 0));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private BigDecimal getServicePrice(String code) {
        // 简化：固定价格映射，生产环境应查服务定价表
        return switch (code != null ? code : "") {
            case "BAG20" -> new BigDecimal("100");
            case "BAG30" -> new BigDecimal("200");
            case "INS_ALL" -> new BigDecimal("30");
            case "MEAL_VIP" -> new BigDecimal("80");
            case "LOUNGE" -> new BigDecimal("200");
            default -> new BigDecimal("50");
        };
    }

    private String getAirportName(String code) {
        if (code == null) return null;
        return switch (code) {
            case "PEK" -> "北京首都国际机场";
            case "PKX" -> "北京大兴国际机场";
            case "SHA" -> "上海虹桥国际机场";
            case "PVG" -> "上海浦东国际机场";
            case "CAN" -> "广州白云国际机场";
            case "CTU" -> "成都天府国际机场";
            case "SZX" -> "深圳宝安国际机场";
            case "HGH" -> "杭州萧山国际机场";
            case "WUH" -> "武汉天河国际机场";
            case "XIY" -> "西安咸阳国际机场";
            default -> code;
        };
    }

    /**
     * 自动过期：如果订单是待支付状态且已超时，将其置为已取消
     * <p>实现已迁移到 {@link OrderExpiryService#expireIfNeeded} —— 那里同时处理座位回退，
     * 并与定时清理任务共用同一套条件更新逻辑，避免两处实现口径不一致。</p>
     */

    private String maskIdNumber(String idNumber) {
        if (idNumber == null || idNumber.length() < 6) return idNumber;
        return idNumber.substring(0, 3) + "***********" + idNumber.substring(idNumber.length() - 4);
    }

    /**
     * 解析新航班 ID：支持数字 ID 或航班号
     */
    private Long parseFlightId(String flightIdStr) {
        try {
            return Long.parseLong(flightIdStr);
        } catch (NumberFormatException e) {
            // 非数字→按航班号查
            List<Flight> flights = flightMapper.selectList(
                    new LambdaQueryWrapper<Flight>()
                            .eq(Flight::getFlightNo, flightIdStr)
                            .eq(Flight::getDeleted, 0)
                            .ge(Flight::getFlightDate, LocalDate.now())
                            .orderByAsc(Flight::getFlightDate));
            if (!flights.isEmpty()) return flights.get(0).getId();
            throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "未找到航班: " + flightIdStr + "（请确认航班号并确保日期未过）");
        }
    }
}
