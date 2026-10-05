package com.itemll.flight_management_system_sp.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.dto.CheckInDTO;
import com.itemll.flight_management_system_sp.entity.*;
import com.itemll.flight_management_system_sp.mapper.*;
import com.itemll.flight_management_system_sp.service.CheckInService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 值机服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {

    private final OrderMapper orderMapper;
    private final OrderPassengerMapper orderPassengerMapper;
    private final CheckInMapper checkInMapper;
    private final BoardingPassMapper boardingPassMapper;
    private final SeatMapper seatMapper;
    private final FlightMapper flightMapper;

    @Override
    public List<Map<String, Object>> getAvailableCheckIn(Long userId) {
        // 查询用户已支付/已出票/已值机的订单
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .in(Order::getStatus, "PAID", "ISSUED", "CHECKED_IN")
                        .eq(Order::getDeleted, 0)
                        .orderByDesc(Order::getCreateTime));

        LocalDateTime now = LocalDateTime.now();

        return orders.stream()
                .filter(order -> {
                    Flight f = flightMapper.selectById(order.getFlightId());
                    if (f == null) return false;
                    // 已起飞的航班不展示
                    if (f.getDepartureTime() != null && f.getDepartureTime().isBefore(now)) return false;
                    // 已值机的订单可以展示（查看登机牌/取消值机）
                    if ("CHECKED_IN".equals(order.getStatus())) return true;
                    if (f.getDepartureTime() == null) return false;
                    // 值机是否开放由 checkin_status 与时间窗口共同决定（见 Flight.isCheckinOpen）
                    return f.isCheckinOpen(now);
                })
                .map(order -> {
            Flight flight = flightMapper.selectById(order.getFlightId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderId", order.getOrderId());
            m.put("flightId", order.getFlightId() != null ? order.getFlightId().toString() : null);
            m.put("flightNo", order.getFlightNo());
            m.put("orderStatus", order.getStatus());
            // 已值机的订单带上值机号，便于再次打开电子登机牌
            if ("CHECKED_IN".equals(order.getStatus())) {
                CheckIn latest = checkInMapper.selectOne(
                        new LambdaQueryWrapper<CheckIn>()
                                .eq(CheckIn::getOrderId, order.getId())
                                .eq(CheckIn::getStatus, "CHECKED_IN")
                                .orderByDesc(CheckIn::getCreateTime)
                                .last("LIMIT 1"));
                m.put("checkinId", latest != null ? latest.getCheckinId() : null);
            }
            if (flight != null) {
                Map<String, Object> dep = new HashMap<>();
                dep.put("airport", flight.getDepartureAirport());
                dep.put("terminal", flight.getDepartureTerminal());
                dep.put("dateTime", flight.getDepartureTime());
                m.put("departure", dep);
                int openHours = flight.getCheckinOpenHours() != null ? flight.getCheckinOpenHours() : 24;
                int closeMinutes = flight.getCheckinCloseMinutes() != null ? flight.getCheckinCloseMinutes() : 30;
                m.put("checkinOpenAt", flight.getDepartureTime().minusHours(openHours));
                m.put("checkinCloseAt", flight.getDepartureTime().minusMinutes(closeMinutes));
            }

            List<OrderPassenger> passengers = orderPassengerMapper.selectList(
                    new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, order.getId()));
            List<Map<String, Object>> pList = passengers.stream().map(p -> {
                Map<String, Object> pm = new HashMap<>();
                pm.put("name", p.getPassengerName());
                pm.put("checkedIn", "CHECKED_IN".equals(p.getCheckinStatus()));
                pm.put("seat", p.getSeatRow() != null ? p.getSeatRow() + p.getSeatColumn() : null);
                return pm;
            }).collect(Collectors.toList());
            m.put("passengers", pList);

            // 标记整体值机状态
            boolean allCheckedIn = passengers.stream().allMatch(p -> "CHECKED_IN".equals(p.getCheckinStatus()));
            boolean anyCheckedIn = passengers.stream().anyMatch(p -> "CHECKED_IN".equals(p.getCheckinStatus()));
            m.put("allCheckedIn", allCheckedIn);
            m.put("anyCheckedIn", anyCheckedIn);

            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getSeatMap(Long flightId) {
        Flight flight = flightMapper.selectById(flightId);
        if (flight == null) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "航班不存在");
        }

        List<Seat> seats = seatMapper.selectList(
                new LambdaQueryWrapper<Seat>().eq(Seat::getFlightId, flightId));

        // 如果座位数据不完整（少于 28排×6列=168 个），清空并重新生成
        if (seats.size() < 28 * 6) {
            // 删除不完整的旧数据
            if (!seats.isEmpty()) {
                seatMapper.delete(new LambdaQueryWrapper<Seat>().eq(Seat::getFlightId, flightId));
            }
            seats = generateSeats(flightId);
            log.info("航班 {} 座位数据不完整，已重新生成 {} 个座位", flightId, seats.size());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("flightId", flightId.toString());

        Map<String, Object> layout = new HashMap<>();
        layout.put("rows", 28);
        layout.put("columns", List.of("A", "B", "C", "D", "E", "F"));
        layout.put("exitRows", List.of(1, 15));
        result.put("layout", layout);

        result.put("seats", seats.stream().map(s -> {
            Map<String, Object> sm = new HashMap<>();
            sm.put("row", s.getRowNum());
            sm.put("column", s.getColCode());
            sm.put("status", s.getStatus());
            sm.put("class", s.getCabinClass());
            sm.put("type", s.getSeatType());
            sm.put("extra", s.getExtraFee() != null && s.getExtraFee().doubleValue() > 0);
            sm.put("extraFee", s.getExtraFee());
            return sm;
        }).collect(Collectors.toList()));

        return result;
    }

    /**
     * 自动生成座位数据（28排×6列，前3排为公务舱，后25排为经济舱）
     * <p><b>占用状态只能由真实值机行为产生</b>（见 {@link #doCheckIn} 与
     * {@code AdminCheckInServiceImpl.manualCheckIn}）。生成阶段一律置 AVAILABLE，
     * 绝不允许伪造——早期实现曾在此用随机数把约 30% 座位标成 OCCUPIED，
     * 造成"无人选座却显示已占用"的脏数据。
     * <p>若该航班已存在真实选座记录（order_passenger.seat_row），
     * 生成后会按真实数据回填占用状态，保证座位图与旅客选座一致。
     */
    private List<Seat> generateSeats(Long flightId) {
        String[] columns = {"A", "B", "C", "D", "E", "F"};
        List<Integer> exitRows = List.of(1, 15);
        List<Seat> seatList = new ArrayList<>();

        for (int row = 1; row <= 28; row++) {
            String cabinClass = row <= 3 ? "BUSINESS" : "ECONOMY";
            for (String col : columns) {
                Seat seat = new Seat();
                seat.setFlightId(flightId);
                seat.setRowNum(row);
                seat.setColCode(col);
                seat.setCabinClass(cabinClass);
                // 座位类型：A/F 靠窗，C/D 靠走道，B/E 中间
                if ("A".equals(col) || "F".equals(col)) {
                    seat.setSeatType("WINDOW");
                } else if ("C".equals(col) || "D".equals(col)) {
                    seat.setSeatType("AISLE");
                } else {
                    seat.setSeatType("MIDDLE");
                }
                // 出口排和公务舱首排收额外费用
                if (exitRows.contains(row) || row == 1) {
                    seat.setExtraFee(new java.math.BigDecimal("50"));
                } else {
                    seat.setExtraFee(java.math.BigDecimal.ZERO);
                }
                // 初始一律可选，占用只能来自真实值机
                seat.setStatus("AVAILABLE");
                seat.setPassengerId(null);
                seatList.add(seat);
            }
        }

        // 批量插入
        for (Seat seat : seatList) {
            seatMapper.insert(seat);
        }

        // 按该航班既有的真实选座记录回填占用状态
        applyOccupiedFromPassengers(flightId, seatList);

        return seatList;
    }

    /**
     * 按订单旅客的真实选座记录回填座位占用状态。
     * <p>座位状态的真源是 {@code order_passenger.seat_row/seat_column}，
     * 而不是 seat 表自身——seat 表只是这份真源的物化视图。
     * 这样即便座位数据被重新生成，已选座的旅客也不会"丢座"。
     */
    private void applyOccupiedFromPassengers(Long flightId, List<Seat> seatList) {
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getFlightId, flightId)
                        .eq(Order::getDeleted, 0));
        if (orders.isEmpty()) {
            return;
        }
        List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
        List<OrderPassenger> selected = orderPassengerMapper.selectList(
                new LambdaQueryWrapper<OrderPassenger>()
                        .in(OrderPassenger::getOrderId, orderIds)
                        .isNotNull(OrderPassenger::getSeatRow));

        Map<String, Seat> index = seatList.stream().collect(Collectors.toMap(
                s -> s.getRowNum() + s.getColCode(), s -> s, (a, b) -> a));

        int marked = 0;
        for (OrderPassenger p : selected) {
            if (p.getSeatRow() == null || p.getSeatColumn() == null) {
                continue;
            }
            Seat seat = index.get(p.getSeatRow() + p.getSeatColumn());
            if (seat == null) {
                continue;
            }
            seat.setStatus("OCCUPIED");
            seat.setPassengerId(p.getId());
            seatMapper.updateById(seat);
            marked++;
        }
        if (marked > 0) {
            log.info("航班 {} 按真实选座记录回填 {} 个占用座位", flightId, marked);
        }
    }

    @Override
    @Transactional
    public void selectSeat(String orderId, Integer passengerIndex, Integer row, String column) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderId, orderId)
                        .eq(Order::getDeleted, 0));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }

        OrderPassenger passenger = getPassengerByIndex(order.getId(), passengerIndex);
        if (passenger == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "旅客不存在");
        }

        // 校验座位可选择性。注意 seat 表在值机（doCheckIn）时才写入 OCCUPIED，
        // 所以这里必须再查一次同航班其他旅客的选座记录，否则两人可以选中同一座位。
        Seat seat = seatMapper.selectOne(
                new LambdaQueryWrapper<Seat>()
                        .eq(Seat::getFlightId, order.getFlightId())
                        .eq(Seat::getRowNum, row)
                        .eq(Seat::getColCode, column));
        if (seat == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "座位不存在");
        }
        if (!"AVAILABLE".equals(seat.getStatus())) {
            throw new BusinessException(ErrorCode.SEAT_OCCUPIED, "该座位已被占用或锁定，请另选");
        }

        List<Order> sameFlightOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getFlightId, order.getFlightId())
                        .eq(Order::getDeleted, 0));
        if (!sameFlightOrders.isEmpty()) {
            List<Long> orderIds = sameFlightOrders.stream().map(Order::getId).collect(Collectors.toList());
            Long taken = orderPassengerMapper.selectCount(
                    new LambdaQueryWrapper<OrderPassenger>()
                            .in(OrderPassenger::getOrderId, orderIds)
                            .eq(OrderPassenger::getSeatRow, row)
                            .eq(OrderPassenger::getSeatColumn, column)
                            .ne(OrderPassenger::getId, passenger.getId()));
            if (taken != null && taken > 0) {
                throw new BusinessException(ErrorCode.SEAT_OCCUPIED, "该座位已被其他旅客选择，请另选");
            }
        }

        passenger.setSeatRow(row);
        passenger.setSeatColumn(column);
        orderPassengerMapper.updateById(passenger);
    }

    @Override
    @Transactional
    public Map<String, Object> doCheckIn(CheckInDTO dto, Long userId) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderId, dto.getOrderId())
                        .eq(Order::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }

        // 校验航班状态：值机已开放且未起飞
        Flight checkFlight = flightMapper.selectById(order.getFlightId());
        if (checkFlight == null) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "航班不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (checkFlight.getDepartureTime() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "航班时刻未定义，无法办理值机");
        }
        if (checkFlight.getDepartureTime().isBefore(now)) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "航班已起飞，无法办理值机");
        }
        // 值机是否开放由 checkin_status 与时间窗口共同决定（见 Flight.isCheckinOpen）
        if (!checkFlight.isCheckinOpen(now)) {
            throw new BusinessException(ErrorCode.CONFLICT, "值机未开放或已关闭，暂时无法办理");
        }

        // 创建值机记录
        CheckIn checkIn = new CheckIn();
        checkIn.setCheckinId("CHK" + IdUtil.getSnowflakeNextIdStr());
        checkIn.setOrderId(order.getId());
        checkIn.setFlightId(order.getFlightId());
        checkIn.setUserId(userId);
        checkIn.setStatus("CHECKED_IN");
        checkInMapper.insert(checkIn);

        // 更新旅客值机状态和座位
        List<Map<String, Object>> boardingPasses = new ArrayList<>();
        Flight flight = flightMapper.selectById(order.getFlightId());

        for (CheckInDTO.CheckInPassenger cp : dto.getPassengers()) {
            OrderPassenger passenger = getPassengerByIndex(order.getId(), cp.getPassengerIndex());
            if (passenger != null) {
                passenger.setCheckinStatus("CHECKED_IN");
                passenger.setSeatRow(cp.getSeatRow());
                passenger.setSeatColumn(cp.getSeatColumn());
                orderPassengerMapper.updateById(passenger);

                // 将 seat 表中对应座位标记为已占用
                Seat seat = seatMapper.selectOne(
                        new LambdaQueryWrapper<Seat>()
                                .eq(Seat::getFlightId, order.getFlightId())
                                .eq(Seat::getRowNum, cp.getSeatRow())
                                .eq(Seat::getColCode, cp.getSeatColumn()));
                if (seat != null) {
                    seat.setStatus("OCCUPIED");
                    seat.setPassengerId(passenger.getId());
                    seatMapper.updateById(seat);
                }

                // 生成登机牌
                BoardingPass bp = new BoardingPass();
                bp.setCheckinId(checkIn.getId());
                bp.setPassengerName(passenger.getPassengerName());
                bp.setFlightNo(order.getFlightNo());
                bp.setSeat(cp.getSeatRow() + cp.getSeatColumn());
                bp.setGate(flight != null ? flight.getDepartureGate() : null);
                bp.setBoardingTime(flight != null ? flight.getDepartureTime().minusMinutes(30) : null);
                bp.setQrCode("https://cdn.skytrip.com/boardingpass/qr/" + checkIn.getCheckinId() + ".png");
                bp.setBarcode("M1" + passenger.getPassengerName().toUpperCase() + "/E" + IdUtil.fastSimpleUUID().substring(0, 10).toUpperCase());
                boardingPassMapper.insert(bp);

                Map<String, Object> bpm = new HashMap<>();
                bpm.put("passenger", passenger.getPassengerName());
                bpm.put("flightNo", order.getFlightNo());
                bpm.put("seat", bp.getSeat());
                bpm.put("gate", bp.getGate());
                bpm.put("boardingTime", bp.getBoardingTime());
                bpm.put("qrCode", bp.getQrCode());
                bpm.put("barcode", bp.getBarcode());
                boardingPasses.add(bpm);
            }
        }

        // 更新订单状态
        order.setStatus("CHECKED_IN");
        orderMapper.updateById(order);

        Map<String, Object> result = new HashMap<>();
        result.put("checkinId", checkIn.getCheckinId());
        result.put("boardingPasses", boardingPasses);
        return result;
    }

    @Override
    public Map<String, Object> getBoardingPass(String checkinId) {
        CheckIn checkIn = checkInMapper.selectOne(
                new LambdaQueryWrapper<CheckIn>()
                        .eq(CheckIn::getCheckinId, checkinId));
        if (checkIn == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "值机记录不存在");
        }
        // 取消值机后登机牌同步失效，不能再展示
        if (!"CHECKED_IN".equals(checkIn.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "值机已取消，登机牌已失效");
        }

        Order order = orderMapper.selectById(checkIn.getOrderId());
        Flight flight = order != null ? flightMapper.selectById(order.getFlightId()) : null;

        List<BoardingPass> passes = boardingPassMapper.selectList(
                new LambdaQueryWrapper<BoardingPass>().eq(BoardingPass::getCheckinId, checkIn.getId()));
        // 按座位号索引，用于把登机牌与订单旅客（含证件号）对应起来
        Map<String, BoardingPass> bySeat = passes.stream()
                .filter(bp -> bp.getSeat() != null)
                .collect(Collectors.toMap(BoardingPass::getSeat, bp -> bp, (a, b) -> a));

        List<OrderPassenger> orderPassengers = order == null ? Collections.emptyList()
                : orderPassengerMapper.selectList(new LambdaQueryWrapper<OrderPassenger>()
                        .eq(OrderPassenger::getOrderId, order.getId())
                        .orderByAsc(OrderPassenger::getId));

        List<Map<String, Object>> passengerList = new ArrayList<>();
        for (OrderPassenger p : orderPassengers) {
            // 只返回本次已完成值机的旅客
            if (!"CHECKED_IN".equals(p.getCheckinStatus())) {
                continue;
            }
            String seat = (p.getSeatRow() != null && p.getSeatColumn() != null)
                    ? p.getSeatRow() + p.getSeatColumn() : null;
            BoardingPass bp = seat != null ? bySeat.get(seat) : null;

            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("name", p.getPassengerName());
            pm.put("ticketNo", p.getTicketNo());
            pm.put("idNumber", maskIdNumber(p.getIdNumber()));
            pm.put("seat", seat);
            pm.put("gate", bp != null && bp.getGate() != null
                    ? bp.getGate() : (flight != null ? flight.getDepartureGate() : null));
            pm.put("boardingTime", bp != null && bp.getBoardingTime() != null
                    ? bp.getBoardingTime()
                    : (flight != null && flight.getDepartureTime() != null
                        ? flight.getDepartureTime().minusMinutes(30) : null));
            pm.put("qrCode", bp != null ? bp.getQrCode() : null);
            pm.put("barcode", bp != null ? bp.getBarcode() : null);
            passengerList.add(pm);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkinId", checkIn.getCheckinId());
        result.put("flight", buildFlightBrief(flight));
        result.put("passengers", passengerList);

        // 兼容 JT 2.6.5 的平铺结构（取首位旅客），同时为多旅客订单提供 passengers 数组
        if (!passengerList.isEmpty()) {
            Map<String, Object> first = passengerList.get(0);
            Map<String, Object> flat = new LinkedHashMap<>();
            Map<String, Object> pax = new LinkedHashMap<>();
            pax.put("name", first.get("name"));
            pax.put("idNumber", first.get("idNumber"));
            flat.put("passenger", pax);
            flat.put("seat", first.get("seat"));
            flat.put("gate", first.get("gate"));
            flat.put("boardingTime", first.get("boardingTime"));
            flat.put("qrCode", first.get("qrCode"));
            flat.put("barcode", first.get("barcode"));
            result.putAll(flat);
        }
        return result;
    }

    /** 证件号脱敏：保留前 3 位与后 4 位，中间打码 */
    private String maskIdNumber(String idNumber) {
        if (idNumber == null || idNumber.length() <= 7) {
            return idNumber;
        }
        String stars = "*".repeat(idNumber.length() - 7);
        return idNumber.substring(0, 3) + stars + idNumber.substring(idNumber.length() - 4);
    }

    /** 登机牌所需的航班摘要信息 */
    private Map<String, Object> buildFlightBrief(Flight flight) {
        if (flight == null) {
            return null;
        }
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("flightNo", flight.getFlightNo());
        f.put("date", flight.getFlightDate());
        f.put("status", flight.getStatus());

        Map<String, Object> dep = new LinkedHashMap<>();
        dep.put("airport", flight.getDepartureAirport());
        dep.put("terminal", flight.getDepartureTerminal());
        dep.put("gate", flight.getDepartureGate());
        dep.put("dateTime", flight.getDepartureTime());
        f.put("departure", dep);

        Map<String, Object> arr = new LinkedHashMap<>();
        arr.put("airport", flight.getArrivalAirport());
        arr.put("terminal", flight.getArrivalTerminal());
        arr.put("dateTime", flight.getArrivalTime());
        f.put("arrival", arr);
        return f;
    }

    @Override
    @Transactional
    public Map<String, Object> cancelCheckIn(String orderId, Long userId) {
        // 查找订单
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderId, orderId)
                        .eq(Order::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!"CHECKED_IN".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前订单状态不是已值机，无法取消值机");
        }

        // 校验航班尚未起飞
        Flight checkFlight = flightMapper.selectById(order.getFlightId());
        if (checkFlight != null && checkFlight.getDepartureTime() != null
                && checkFlight.getDepartureTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "航班已起飞，无法取消值机");
        }

        // 查找值机记录
        CheckIn checkIn = checkInMapper.selectOne(
                new LambdaQueryWrapper<CheckIn>()
                        .eq(CheckIn::getOrderId, order.getId())
                        .eq(CheckIn::getStatus, "CHECKED_IN")
                        .orderByDesc(CheckIn::getCreateTime)
                        .last("LIMIT 1"));
        if (checkIn == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到值机记录");
        }

        // 更新值机记录状态为已取消
        checkIn.setStatus("CANCELLED");
        checkInMapper.updateById(checkIn);

        // 释放座位：将旅客的值机状态回退、清空座位
        List<OrderPassenger> passengers = orderPassengerMapper.selectList(
                new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, order.getId()));
        for (OrderPassenger p : passengers) {
            if ("CHECKED_IN".equals(p.getCheckinStatus())) {
                // 释放座位（将 seat 表中对应座位改回 AVAILABLE）
                if (p.getSeatRow() != null && p.getSeatColumn() != null) {
                    Seat seat = seatMapper.selectOne(
                            new LambdaQueryWrapper<Seat>()
                                    .eq(Seat::getFlightId, order.getFlightId())
                                    .eq(Seat::getRowNum, p.getSeatRow())
                                    .eq(Seat::getColCode, p.getSeatColumn()));
                    if (seat != null) {
                        seat.setStatus("AVAILABLE");
                        seat.setPassengerId(null);
                        seatMapper.updateById(seat);
                    }
                }
                // 回退旅客值机状态
                p.setCheckinStatus("NOT_CHECKED_IN");
                p.setSeatRow(null);
                p.setSeatColumn(null);
                orderPassengerMapper.updateById(p);
            }
        }

        // 删除登机牌
        boardingPassMapper.delete(
                new LambdaQueryWrapper<BoardingPass>().eq(BoardingPass::getCheckinId, checkIn.getId()));

        // 订单状态回退到已支付
        order.setStatus("PAID");
        orderMapper.updateById(order);

        log.info("取消值机成功: orderId={}, checkinId={}", orderId, checkIn.getCheckinId());

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("message", "值机已取消，座位已释放");
        return result;
    }

    private OrderPassenger getPassengerByIndex(Long orderId, Integer index) {
        List<OrderPassenger> passengers = orderPassengerMapper.selectList(
                new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, orderId));
        if (index == null || index < 0 || index >= passengers.size()) return null;
        return passengers.get(index);
    }
}
