package com.itemll.flight_management_system_sp.controller.admin;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.entity.*;
import com.itemll.flight_management_system_sp.mapper.*;
import com.itemll.flight_management_system_sp.service.CheckInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端旅客与座位管理控制器
 */
@Tag(name = "管理端-旅客与座位", description = "旅客查询、座位图、锁定/解锁座位")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminPassengerController {

    private final OrderMapper orderMapper;
    private final OrderPassengerMapper passengerMapper;
    private final SeatMapper seatMapper;
    private final UserMapper userMapper;
    private final FlightMapper flightMapper;
    private final CheckInService checkInService;

    // ==================== 旅客查询 ====================

    @OperationLog(module = "旅客管理", action = "查询旅客")
    @Operation(summary = "旅客查询")
    @GetMapping("/passengers")
    public Result<PageResult<Map<String, Object>>> getPassengers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String idNumber,
            @RequestParam(required = false) String flightNo,
            @RequestParam(required = false) String pnr,
            @RequestParam(required = false) String phone,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {

        // 先按条件查订单 (分页)
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<Order>()
                .eq(StrUtil.isNotBlank(flightNo), Order::getFlightNo, flightNo)
                .eq(StrUtil.isNotBlank(pnr), Order::getPnr, pnr)
                .eq(Order::getDeleted, 0)
                .orderByDesc(Order::getCreateTime);

        Page<Order> orderPage = orderMapper.selectPage(new Page<>(page, pageSize), orderWrapper);

        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (Order order : orderPage.getRecords()) {
            // 如果没传条件，默认查该订单的所有旅客
            LambdaQueryWrapper<OrderPassenger> pWrapper = new LambdaQueryWrapper<OrderPassenger>()
                    .eq(OrderPassenger::getOrderId, order.getId())
                    .like(StrUtil.isNotBlank(name), OrderPassenger::getPassengerName, name)
                    .like(StrUtil.isNotBlank(idNumber), OrderPassenger::getIdNumber, idNumber)
                    .eq(StrUtil.isNotBlank(phone), OrderPassenger::getPhone, phone);
            List<OrderPassenger> passengers = passengerMapper.selectList(pWrapper);
            for (OrderPassenger p : passengers) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("passengerId", p.getId());
                m.put("name", p.getPassengerName());
                m.put("gender", p.getGender());
                m.put("idType", p.getIdType());
                m.put("idNumber", maskId(p.getIdNumber()));
                m.put("phone", p.getPhone());
                m.put("pnr", order.getPnr());
                m.put("orderId", order.getOrderId());
                m.put("flightNo", order.getFlightNo());

                // 用户会员信息（通过订单关联 user）
                if (order.getUserId() != null) {
                    User user = userMapper.selectById(order.getUserId());
                    if (user != null) {
                        m.put("memberLevel", user.getMemberLevel());
                        m.put("blacklist", false);
                    } else {
                        m.put("memberLevel", null);
                        m.put("blacklist", false);
                    }
                } else {
                    m.put("memberLevel", null);
                    m.put("blacklist", false);
                }

                // 最近行程
                List<Map<String, Object>> recentTrips = new ArrayList<>();
                Flight flight = flightMapper.selectById(order.getFlightId());
                if (flight != null) {
                    Map<String, Object> trip = new LinkedHashMap<>();
                    trip.put("flightNo", flight.getFlightNo());
                    trip.put("route", flight.getDepartureAirport() + "-" + flight.getArrivalAirport());
                    trip.put("date", flight.getFlightDate() != null ? flight.getFlightDate().toString() : null);
                    recentTrips.add(trip);
                }
                m.put("recentTrips", recentTrips);

                result.add(m);
            }
        }

        return Result.ok(PageResult.of(result, page, pageSize, orderPage.getTotal()));
    }

    // ==================== 座位管理 ====================

    @OperationLog(module = "旅客管理", action = "查看座位图", saveParams = false)
    @Operation(summary = "获取航班座位图")
    @GetMapping("/seats/{flightId}")
    public Result<List<Map<String, Object>>> getSeatMap(@PathVariable Long flightId) {
        List<Seat> seats = seatMapper.selectList(
                new LambdaQueryWrapper<Seat>().eq(Seat::getFlightId, flightId));

        // 座位数据缺失时按需生成：复用前台同一套生成逻辑，
        // 避免"前台能看到座位图、后台却是空的"这类前后台不一致。
        if (seats.size() < 28 * 6) {
            checkInService.getSeatMap(flightId);
            seats = seatMapper.selectList(
                    new LambdaQueryWrapper<Seat>().eq(Seat::getFlightId, flightId));
        }

        return Result.ok(seats.stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId()); m.put("row", s.getRowNum()); m.put("column", s.getColCode());
            m.put("cabinClass", s.getCabinClass()); m.put("seatType", s.getSeatType());
            m.put("status", s.getStatus()); m.put("extraFee", s.getExtraFee());
            return m;
        }).collect(Collectors.toList()));
    }

    @OperationLog(module = "旅客管理", action = "锁定座位")
    @Operation(summary = "锁定座位")
    @PostMapping("/seats/{flightId}/lock")
    public Result<Void> lockSeat(@PathVariable Long flightId, @RequestBody Map<String, Object> params) {
        Seat seat = findSeat(flightId, params);
        if (seat == null) {
            return Result.notFound("座位不存在");
        }
        if ("OCCUPIED".equals(seat.getStatus())) {
            return Result.fail("该座位已被旅客占用，无法锁定");
        }
        if ("LOCKED".equals(seat.getStatus())) {
            return Result.ok();   // 幂等：已锁定则直接返回成功
        }
        seat.setStatus("LOCKED");
        seatMapper.updateById(seat);
        return Result.ok();
    }

    @OperationLog(module = "旅客管理", action = "解锁座位")
    @Operation(summary = "解锁座位")
    @PostMapping("/seats/{flightId}/unlock")
    public Result<Void> unlockSeat(@PathVariable Long flightId, @RequestBody Map<String, Object> params) {
        Seat seat = findSeat(flightId, params);
        if (seat == null) {
            return Result.notFound("座位不存在");
        }
        // 关键：占用状态来源于旅客的真实选座（order_passenger），
        // 后台无权把旅客的座位改回可选，否则会出现"旅客有座、座位图却显示空着"。
        if ("OCCUPIED".equals(seat.getStatus())) {
            return Result.fail("该座位已被旅客占用，需取消旅客值机后才能释放");
        }
        if ("AVAILABLE".equals(seat.getStatus())) {
            return Result.ok();   // 幂等
        }
        seat.setStatus("AVAILABLE");
        seat.setPassengerId(null);
        seatMapper.updateById(seat);
        return Result.ok();
    }

    /** 按航班 + 行列定位座位 */
    private Seat findSeat(Long flightId, Map<String, Object> params) {
        Object rowObj = params.get("row");
        Object colObj = params.get("column");
        if (rowObj == null || colObj == null) {
            return null;
        }
        Integer row = Integer.valueOf(String.valueOf(rowObj));
        String column = String.valueOf(colObj);
        return seatMapper.selectOne(
                new LambdaQueryWrapper<Seat>()
                        .eq(Seat::getFlightId, flightId)
                        .eq(Seat::getRowNum, row)
                        .eq(Seat::getColCode, column));
    }

    /**
     * 旅客详情：单条订单旅客记录 + 该旅客的历史行程。
     * <p>历史行程按<b>证件号</b>匹配（证件号是"人"的标识）。用姓名或手机号会串人：
     * 同名旅客、家人共用手机号的情况都真实存在，证件号才是唯一可靠的锚点。
     */
    @OperationLog(module = "旅客管理", action = "查看旅客详情", saveParams = false)
    @Operation(summary = "旅客详情（含历史行程）")
    @GetMapping("/passengers/{passengerId}")
    public Result<Map<String, Object>> getPassengerDetail(@PathVariable Long passengerId) {
        OrderPassenger p = passengerMapper.selectById(passengerId);
        if (p == null) {
            return Result.notFound("旅客不存在");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passengerId", p.getId());
        result.put("name", p.getPassengerName());
        result.put("gender", p.getGender());
        result.put("idType", p.getIdType());
        result.put("idNumber", maskId(p.getIdNumber()));
        result.put("phone", p.getPhone());
        result.put("birthday", p.getBirthday() != null ? p.getBirthday().toString() : null);
        result.put("passengerType", p.getPassengerType());
        result.put("frequentFlyerNo", p.getFrequentFlyerNo());
        result.put("ticketNo", p.getTicketNo());
        result.put("seat", p.getSeatRow() != null ? p.getSeatRow() + p.getSeatColumn() : null);
        result.put("checkinStatus", p.getCheckinStatus());

        Order order = p.getOrderId() != null ? orderMapper.selectById(p.getOrderId()) : null;
        if (order != null) {
            result.put("orderId", order.getOrderId());
            result.put("orderNo", order.getOrderNo());
            result.put("pnr", order.getPnr());
            result.put("orderStatus", order.getStatus());
            result.put("flightNo", order.getFlightNo());
            if (order.getUserId() != null) {
                User user = userMapper.selectById(order.getUserId());
                result.put("memberLevel", user != null ? user.getMemberLevel() : null);
                result.put("memberNo", user != null ? user.getMemberNo() : null);
            }
        }
        // 黑名单目前是固定值（业务侧未接入判定规则），保留字段以便前端一致展示
        result.put("blacklist", false);

        // 历史行程
        List<Map<String, Object>> history = new ArrayList<>();
        if (StrUtil.isNotBlank(p.getIdNumber())) {
            List<OrderPassenger> samePerson = passengerMapper.selectList(
                    new LambdaQueryWrapper<OrderPassenger>()
                            .eq(OrderPassenger::getIdNumber, p.getIdNumber()));
            for (OrderPassenger x : samePerson) {
                Order o = x.getOrderId() != null ? orderMapper.selectById(x.getOrderId()) : null;
                if (o == null || (o.getDeleted() != null && o.getDeleted() == 1)) {
                    continue;
                }
                Map<String, Object> h = new LinkedHashMap<>();
                h.put("orderId", o.getOrderId());
                h.put("orderNo", o.getOrderNo());
                h.put("status", o.getStatus());
                h.put("cabinClass", o.getCabinClass());
                h.put("ticketNo", x.getTicketNo());
                h.put("seat", x.getSeatRow() != null ? x.getSeatRow() + x.getSeatColumn() : null);
                h.put("createTime", o.getCreateTime());
                Flight f = o.getFlightId() != null ? flightMapper.selectById(o.getFlightId()) : null;
                if (f != null) {
                    h.put("flightNo", f.getFlightNo());
                    h.put("route", f.getDepartureAirport() + "-" + f.getArrivalAirport());
                    h.put("date", f.getFlightDate() != null ? f.getFlightDate().toString() : null);
                }
                history.add(h);
            }
            // 最近的在最上面
            history.sort((a, b) -> String.valueOf(b.get("createTime")).compareTo(String.valueOf(a.get("createTime"))));
        }
        result.put("history", history);

        return Result.ok(result);
    }

    private String maskId(String id) {
        if (id == null || id.length() < 6) return id;
        return id.substring(0, 3) + "***********" + id.substring(id.length() - 4);
    }
}
