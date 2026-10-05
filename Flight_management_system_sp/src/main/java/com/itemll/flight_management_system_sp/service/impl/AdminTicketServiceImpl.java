package com.itemll.flight_management_system_sp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.entity.*;
import com.itemll.flight_management_system_sp.mapper.AirlineMapper;
import com.itemll.flight_management_system_sp.mapper.FlightCabinMapper;
import com.itemll.flight_management_system_sp.mapper.FlightMapper;
import com.itemll.flight_management_system_sp.mapper.OrderChangeMapper;
import com.itemll.flight_management_system_sp.mapper.OrderMapper;
import com.itemll.flight_management_system_sp.mapper.OrderPassengerMapper;
import com.itemll.flight_management_system_sp.mapper.OrderRefundMapper;
import com.itemll.flight_management_system_sp.pay.RefundResult;
import com.itemll.flight_management_system_sp.service.AdminTicketService;
import com.itemll.flight_management_system_sp.service.NotificationService;
import com.itemll.flight_management_system_sp.service.OrderChangeSettlementService;
import com.itemll.flight_management_system_sp.service.PaymentRefundService;
import com.itemll.flight_management_system_sp.service.SeatInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端客票服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTicketServiceImpl implements AdminTicketService {

    private final OrderMapper orderMapper;
    private final OrderPassengerMapper passengerMapper;
    private final OrderRefundMapper refundMapper;
    private final OrderChangeMapper changeMapper;
    private final FlightMapper flightMapper;
    private final FlightCabinMapper flightCabinMapper;
    private final AirlineMapper airlineMapper;
    private final NotificationService notificationService;
    /** 退款的唯一出口：真正调用渠道退款，不再"只改状态不动钱" */
    private final PaymentRefundService paymentRefundService;
    /** 库存与座位的唯一回退实现（退票、改签共用） */
    private final SeatInventoryService seatInventoryService;
    /** 改签生效的唯一实现（免补款与付款成功两条路共用） */
    private final OrderChangeSettlementService orderChangeSettlementService;

    /** 退票单状态 */
    private static final String REFUND_PENDING = "PENDING";
    private static final String REFUND_REJECTED = "REJECTED";
    private static final String REFUND_COMPLETED = "COMPLETED";
    /** 改签单状态 */
    private static final String CHANGE_PENDING = "PENDING";
    private static final String CHANGE_REJECTED = "REJECTED";
    private static final String CHANGE_PENDING_PAYMENT = "PENDING_PAYMENT";

    @Override
    public PageResult<Map<String, Object>> getBookings(String pnr, String passengerName, String flightNo, int page, int pageSize) {
        // 分页查询订单
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(pnr != null && !pnr.isEmpty(), Order::getPnr, pnr)
                .like(flightNo != null && !flightNo.isEmpty(), Order::getFlightNo, flightNo)
                .eq(Order::getDeleted, 0)
                .orderByDesc(Order::getCreateTime);

        IPage<Order> orderPage = orderMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<Order> orders = orderPage.getRecords();

        // 批量获取航班信息（departureAirport / arrivalAirport）
        Set<Long> flightIds = orders.stream().map(Order::getFlightId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Flight> flightMap = new HashMap<>();
        if (!flightIds.isEmpty()) {
            flightMapper.selectBatchIds(flightIds).forEach(f -> flightMap.put(f.getId(), f));
        }

        // 批量获取旅客姓名
        Set<Long> orderIdSet = orders.stream().map(Order::getId).collect(Collectors.toSet());
        Map<Long, List<OrderPassenger>> passengerMap = new HashMap<>();
        if (!orderIdSet.isEmpty()) {
            passengerMapper.selectList(
                    new LambdaQueryWrapper<OrderPassenger>().in(OrderPassenger::getOrderId, orderIdSet)
            ).forEach(p -> passengerMap.computeIfAbsent(p.getOrderId(), k -> new ArrayList<>()).add(p));
        }

        // 构建结果
        List<Map<String, Object>> list = new ArrayList<>();
        for (Order o : orders) {
            // 如果按旅客姓名筛选，检查当前订单是否包含匹配的旅客
            if (passengerName != null && !passengerName.isEmpty()) {
                List<OrderPassenger> passengers = passengerMap.get(o.getId());
                boolean match = passengers != null && passengers.stream()
                        .anyMatch(p -> p.getPassengerName() != null && p.getPassengerName().contains(passengerName));
                if (!match) continue;
            }

            Flight flight = flightMap.get(o.getFlightId());
            List<OrderPassenger> passengers = passengerMap.get(o.getId());

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pnr", o.getPnr());
            m.put("passengerName", passengers != null ? passengers.stream()
                    .map(OrderPassenger::getPassengerName).collect(Collectors.joining(", ")) : "");
            m.put("flightNo", o.getFlightNo());
            m.put("departure", flight != null ? flight.getDepartureAirport() : "");
            m.put("arrival", flight != null ? flight.getArrivalAirport() : "");
            m.put("cabinClass", o.getCabinClass());
            m.put("status", o.getStatus());
            m.put("totalPrice", o.getTotalAmount());
            m.put("createdAt", o.getCreateTime());
            m.put("orderId", o.getOrderId());
            list.add(m);
        }

        // 如果按旅客姓名筛选，总记录数需重新计算
        long total = orderPage.getTotal();
        if (passengerName != null && !passengerName.isEmpty()) {
            total = list.size();
        }

        return PageResult.of(list, page, pageSize, total);
    }

    @Override
    public Map<String, Object> getBookingDetail(String pnr) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getPnr, pnr));
        if (order == null) return Collections.emptyMap();

        List<OrderPassenger> passengers = passengerMapper.selectList(
                new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, order.getId()));

        Flight flight = flightMapper.selectById(order.getFlightId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pnr", order.getPnr());
        result.put("flightNo", order.getFlightNo());
        result.put("status", order.getStatus());
        result.put("cabinClass", order.getCabinClass());
        result.put("departure", flight != null ? flight.getDepartureAirport() : "");
        result.put("arrival", flight != null ? flight.getArrivalAirport() : "");
        result.put("totalPrice", order.getTotalAmount());
        result.put("createdAt", order.getCreateTime());
        result.put("passengerName", passengers.stream()
                .map(OrderPassenger::getPassengerName).collect(Collectors.joining(", ")));
        result.put("idNumber", passengers.stream()
                .map(OrderPassenger::getIdNumber).filter(Objects::nonNull)
                .collect(Collectors.joining(", ")));
        return result;
    }

    @Override
    public PageResult<Map<String, Object>> getFares(int page, int pageSize) {
        IPage<FlightCabin> cabinPage = flightCabinMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<FlightCabin>().orderByAsc(FlightCabin::getId));

        // 批量获取航班和航司信息
        Set<Long> flightIds = cabinPage.getRecords().stream()
                .map(FlightCabin::getFlightId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Flight> flightMap = flightMapper.selectBatchIds(flightIds).stream()
                .collect(Collectors.toMap(Flight::getId, f -> f));
        Set<Long> airlineIds = flightMap.values().stream()
                .map(Flight::getAirlineId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Airline> airlineMap = airlineMapper.selectBatchIds(airlineIds).stream()
                .collect(Collectors.toMap(Airline::getId, a -> a));

        List<Map<String, Object>> list = cabinPage.getRecords().stream().map(fc -> {
            Flight flight = flightMap.get(fc.getFlightId());
            Airline airline = flight != null ? airlineMap.get(flight.getAirlineId()) : null;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fareId", String.valueOf(fc.getId()));
            m.put("airline", airline != null ? airline.getCode() : "");
            m.put("departure", flight != null ? flight.getDepartureAirport() : "");
            m.put("arrival", flight != null ? flight.getArrivalAirport() : "");
            m.put("cabinClass", fc.getCabinClass());
            m.put("fare", fc.getFare());
            m.put("tax", fc.getTax());
            m.put("effectiveDate", flight != null && flight.getFlightDate() != null ? flight.getFlightDate().toString() : "");
            m.put("expireDate", "");
            m.put("status", "ACTIVE");
            return m;
        }).collect(Collectors.toList());

        return PageResult.of(list, page, pageSize, cabinPage.getTotal());
    }

    @Override
    public void createFare(Map<String, Object> fare) {
        log.info("创建运价: {}", fare);
    }

    @Override
    public void updateFare(Long fareId, Map<String, Object> fare) {
        log.info("更新运价: fareId={}", fareId);
    }

    @Override
    public List<Map<String, Object>> getRefunds() {
        List<OrderRefund> refunds = refundMapper.selectList(
                new LambdaQueryWrapper<OrderRefund>().orderByDesc(OrderRefund::getCreateTime)
                        .last("LIMIT 50"));
        // 批量获取关联订单
        Set<Long> orderIdSet = refunds.stream().map(OrderRefund::getOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Order> orderMap = orderMapper.selectBatchIds(orderIdSet).stream()
                .collect(Collectors.toMap(Order::getId, o -> o));
        // 批量获取旅客姓名
        Map<Long, String> passengerNameMap = new HashMap<>();
        if (!orderIdSet.isEmpty()) {
            passengerMapper.selectList(
                    new LambdaQueryWrapper<OrderPassenger>().in(OrderPassenger::getOrderId, orderIdSet)
            ).forEach(p -> passengerNameMap.merge(p.getOrderId(), p.getPassengerName(),
                    (a, b) -> a + ", " + b));
        }
        return refunds.stream().map(r -> {
            Order order = orderMap.get(r.getOrderId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("refundId", r.getRefundId());
            m.put("orderId", r.getOrderId());
            m.put("passengerName", passengerNameMap.getOrDefault(r.getOrderId(), ""));
            m.put("flightNo", order != null ? order.getFlightNo() : "");
            m.put("refundAmount", r.getRefundAmount());
            m.put("refundFee", r.getRefundFee());
            m.put("reason", r.getReason());
            m.put("status", r.getStatus());
            // 退款留痕：运营可以据此确认"钱到底退没退、退到哪笔渠道流水"
            m.put("channelRefundNo", r.getChannelRefundNo());
            m.put("refundTime", r.getRefundTime());
            m.put("failReason", r.getFailReason());
            m.put("createTime", r.getCreateTime());
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getChanges() {
        List<OrderChange> changes = changeMapper.selectList(
                new LambdaQueryWrapper<OrderChange>().orderByDesc(OrderChange::getCreateTime)
                        .last("LIMIT 50"));
        // 批量获取关联订单和航班
        Set<Long> orderIdSet = changes.stream().map(OrderChange::getOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Order> orderMap = orderMapper.selectBatchIds(orderIdSet).stream()
                .collect(Collectors.toMap(Order::getId, o -> o));
        Set<Long> flightIdSet = new HashSet<>();
        changes.forEach(c -> {
            if (c.getOriginalFlightId() != null) flightIdSet.add(c.getOriginalFlightId());
            if (c.getNewFlightId() != null) flightIdSet.add(c.getNewFlightId());
        });
        Map<Long, Flight> flightMap = flightMapper.selectBatchIds(flightIdSet).stream()
                .collect(Collectors.toMap(Flight::getId, f -> f));
        // 批量获取旅客姓名
        Map<Long, String> passengerNameMap = new HashMap<>();
        if (!orderIdSet.isEmpty()) {
            passengerMapper.selectList(
                    new LambdaQueryWrapper<OrderPassenger>().in(OrderPassenger::getOrderId, orderIdSet)
            ).forEach(p -> passengerNameMap.merge(p.getOrderId(), p.getPassengerName(),
                    (a, b) -> a + ", " + b));
        }
        return changes.stream().map(c -> {
            Order order = orderMap.get(c.getOrderId());
            Flight originalFlight = flightMap.get(c.getOriginalFlightId());
            Flight newFlight = flightMap.get(c.getNewFlightId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("changeId", c.getChangeId());
            m.put("orderId", c.getOrderId());
            m.put("passengerName", passengerNameMap.getOrDefault(c.getOrderId(), ""));
            m.put("originalFlightNo", originalFlight != null ? originalFlight.getFlightNo() : "");
            m.put("newFlightNo", newFlight != null ? newFlight.getFlightNo() : "");
            m.put("changeFee", c.getChangeFee());
            m.put("fareDiff", c.getFareDiff());
            // total_fee 是「旅客尚需补款的金额」，恒 >= 0；净额为负时前端展示"应退差价"
            m.put("totalFee", c.getTotalFee());
            m.put("originalCabinClass", c.getOriginalCabinClass());
            m.put("newCabinClass", c.getNewCabinClass());
            m.put("status", c.getStatus());
            m.put("createTime", c.getCreateTime());
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 审核退票申请。
     *
     * <p><b>改造前的问题：</b>这里只把 {@code order_refund.status} 改成 APPROVED、
     * 订单改成 REFUNDED 并发一条通知 —— <b>钱一分没退</b>（{@code PaymentGateway.refund}
     * 全项目零调用方），座位和库存也没回补。也就是"审核通过"只是改了个状态字。</p>
     *
     * <p><b>现在的顺序：</b>先真正向渠道退款（失败则整体回滚，状态留在待审核，管理员可重试），
     * 再回补库存、释放座位、作废值机与登机牌，最后才把订单置为已退款。
     * 顺序刻意如此 —— 钱没退成功之前绝不提前把状态改掉。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveRefund(String refundId, Map<String, Object> body) {
        OrderRefund refund = refundMapper.selectOne(
                new LambdaQueryWrapper<OrderRefund>()
                        .eq(OrderRefund::getRefundId, refundId));
        if (refund == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "退票申请不存在：" + refundId);
        }

        // 幂等：只有 PENDING 才处理。否则管理员重复点两次「通过」就会退两笔钱。
        if (!REFUND_PENDING.equals(refund.getStatus())) {
            log.warn("退票申请已处理过，跳过重复审核: refundId={}, status={}", refundId, refund.getStatus());
            return;
        }

        boolean approved = body != null && Boolean.TRUE.equals(body.get("approved"));
        Order order = orderMapper.selectById(refund.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "退票申请关联的订单不存在");
        }

        if (!approved) {
            refund.setStatus(REFUND_REJECTED);
            refundMapper.updateById(refund);
            notifyRefundResult(order, refund, false, null);
            log.info("退票申请已拒绝: refundId={}, orderId={}", refundId, order.getOrderNo());
            return;
        }

        // ---------- ① 真正退款（渠道不受理则抛异常，整个审核回滚） ----------
        Payment payment = paymentRefundService.requireSuccessfulPayment(order.getId());
        RefundResult result = paymentRefundService.refund(payment, refund.getRefundAmount(),
                "退票：" + (refund.getReason() == null ? "用户申请" : refund.getReason()));
        refund.setChannelRefundNo(result.getChannelTradeNo());
        refund.setRefundTime(LocalDateTime.now());
        refund.setFailReason(null);

        // ---------- ② 回补库存 + 释放座位 + 作废值机与登机牌 ----------
        int passengerCount = seatInventoryService.countPassengers(order.getId());
        seatInventoryService.voidCheckInAndReleaseSeats(order.getId(), order.getFlightId());
        seatInventoryService.restoreCabinSeats(order.getFlightId(), order.getCabinClass(), passengerCount);

        // ---------- ③ 订单与退票单收口 ----------
        order.setStatus("REFUNDED");
        orderMapper.updateById(order);

        refund.setStatus(REFUND_COMPLETED);
        refundMapper.updateById(refund);

        notifyRefundResult(order, refund, true, payment.getPayMethod());
        log.info("退票已完成: refundId={}, orderId={}, amount={}, channel={}, passengers={}",
                refundId, order.getOrderNo(), refund.getRefundAmount(), payment.getPayMethod(), passengerCount);
    }

    /**
     * 审核改签申请。
     *
     * <p><b>改造前的问题：</b>审核通过后只是把订单的 {@code flightId/flightNo} 一改了之 ——
     * 不释放原航班座位、不扣减新航班库存、不收改签费与差价（{@code fareDiff} 在申请时就写死为 0）。
     * 等于"行程改了，钱和座位都没动"。</p>
     *
     * <p><b>现在的分支：</b></p>
     * <ul>
     *   <li>需要补款（{@code total_fee > 0}）：置为 {@code PENDING_PAYMENT}，
     *       等旅客支付成功后由 {@code PaymentServiceImpl#markPaid} 识别
     *       {@code biz_type=CHANGE} 再调用生效服务。<b>钱没到不改行程。</b></li>
     *   <li>无需补款（手续费与差价合计 &le; 0）：直接调用生效服务，
     *       由它释放原座位、扣新库存、按新舱位重算票面金额，并在净额为负时退还差价。</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveChange(String changeId, Map<String, Object> body) {
        OrderChange change = changeMapper.selectOne(
                new LambdaQueryWrapper<OrderChange>()
                        .eq(OrderChange::getChangeId, changeId));
        if (change == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "改签申请不存在：" + changeId);
        }

        // 幂等：只有 PENDING 待审状态才处理，避免重复点「通过」导致重复扣库存、重复改行程
        if (!CHANGE_PENDING.equals(change.getStatus())) {
            log.warn("改签申请已处理过，跳过重复审核: changeId={}, status={}", changeId, change.getStatus());
            return;
        }

        boolean approved = body != null && Boolean.TRUE.equals(body.get("approved"));
        Order order = orderMapper.selectById(change.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "改签申请关联的订单不存在");
        }

        if (!approved) {
            change.setStatus(CHANGE_REJECTED);
            changeMapper.updateById(change);
            notifyChangeResult(order, change, false, null);
            log.info("改签申请已拒绝: changeId={}, orderId={}", changeId, order.getOrderNo());
            return;
        }

        BigDecimal payable = change.getTotalFee() == null ? BigDecimal.ZERO : change.getTotalFee();
        if (payable.signum() > 0) {
            // 需要旅客补款：只放行到「待支付」，行程保持不变
            change.setStatus(CHANGE_PENDING_PAYMENT);
            changeMapper.updateById(change);
            notifyChangeResult(order, change, true, payable);
            log.info("改签审核通过，等待旅客补款: changeId={}, orderId={}, payable={}",
                    changeId, order.getOrderNo(), payable);
            return;
        }

        // 无需补款：直接生效（settle 内部会做幂等与状态校验）
        boolean settled = orderChangeSettlementService.settle(changeId);
        if (!settled) {
            throw new BusinessException(ErrorCode.CONFLICT, "改签未能生效，请刷新后重试");
        }
    }

    // ==================== 内部方法 ====================

    /** 退票结果通知 */
    private void notifyRefundResult(Order order, OrderRefund refund, boolean approved, String payMethod) {
        if (order.getUserId() == null) {
            return;
        }
        String title = approved ? "退票成功" : "退票申请被拒绝";
        String content = approved
                ? "您的退票已受理，退款金额 ¥" + refund.getRefundAmount()
                  + " 已原路退回（" + (payMethod == null ? "原支付渠道" : payMethod) + "），预计 1-7 个工作日到账"
                : "您的退票申请未通过审核，如有疑问请联系客服";
        notificationService.notifyPassenger(order.getUserId(), title, content, "REFUND", order.getId());
    }

    /** 改签结果通知 */
    private void notifyChangeResult(Order order, OrderChange change, boolean approved, BigDecimal payable) {
        if (order.getUserId() == null) {
            return;
        }
        String title = approved ? "改签申请已通过审核" : "改签申请被拒绝";
        String content;
        if (!approved) {
            content = "您的改签申请未通过审核，如有疑问请联系客服";
        } else if (payable != null && payable.signum() > 0) {
            content = "您的改签申请已通过审核，需补款 ¥" + payable
                    + "，请前往「我的订单 - 改签」完成支付后行程才会变更";
        } else {
            content = "您的改签申请已通过，行程已变更，请重新办理值机选座";
        }
        notificationService.notifyPassenger(order.getUserId(), title, content, "CHANGE", order.getId());
    }
}
