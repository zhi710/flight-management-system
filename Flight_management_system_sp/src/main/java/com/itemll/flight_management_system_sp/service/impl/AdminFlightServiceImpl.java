package com.itemll.flight_management_system_sp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.enums.FlightStatus;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.dto.AdminFlightCreateDTO;
import com.itemll.flight_management_system_sp.entity.*;
import com.itemll.flight_management_system_sp.mapper.*;
import com.itemll.flight_management_system_sp.security.AdminIrregularWebSocketHandler;
import com.itemll.flight_management_system_sp.security.FlightStatusWebSocketHandler;
import com.itemll.flight_management_system_sp.service.AdminFlightService;
import com.itemll.flight_management_system_sp.service.AdminMonitorService;
import com.itemll.flight_management_system_sp.service.GroundHandlingService;
import com.itemll.flight_management_system_sp.service.FlightStateMachine;
import com.itemll.flight_management_system_sp.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;



@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFlightServiceImpl implements AdminFlightService {

    private final FlightMapper flightMapper;
    private final FlightCabinMapper flightCabinMapper;
    private final FlightStatusLogMapper statusLogMapper;
    private final AirlineMapper airlineMapper;
    private final AirportMapper airportMapper;
    private final AdminMonitorService adminMonitorService;
    private final IrregularOperationMapper irregularOperationMapper;
    private final AutoRebookingMapper autoRebookingMapper;
    private final OrderMapper orderMapper;
    private final OrderPassengerMapper orderPassengerMapper;
    private final FlightStatusWebSocketHandler flightStatusWebSocket;
    private final AdminIrregularWebSocketHandler adminIrregularWebSocket;
    private final NotificationService notificationService;
    private final GroundHandlingService groundHandlingService;
    private final AirlineFareMapper airlineFareMapper;
    private final GateAssignmentMapper gateAssignmentMapper;
    private final FlightStateMachine flightStateMachine;
    private final AircraftMapper aircraftMapper;
    private final AircraftTypeMapper aircraftTypeMapper;

    /** 删除航班时同步清理登机口关联 */
    private void releaseGatesForFlight(Long flightId) {
        List<GateAssignment> assignments = gateAssignmentMapper.selectList(
                new LambdaQueryWrapper<GateAssignment>().eq(GateAssignment::getFlightId, flightId));
        if (assignments.isEmpty()) return;
        gateAssignmentMapper.delete(new LambdaQueryWrapper<GateAssignment>().eq(GateAssignment::getFlightId, flightId));
        flightMapper.update(null, new LambdaUpdateWrapper<Flight>()
                .eq(Flight::getId, flightId)
                .set(Flight::getDepartureGate, null));
        log.info("航班删除/取消时自动释放登机口: flightId={}, 数量={}", flightId, assignments.size());
    }

    @Override
    public PageResult<Map<String, Object>> getFlightList(String date, String route, String status,
                                                          String airline, String keyword, int page, int pageSize) {
        LambdaQueryWrapper<Flight> wrapper = new LambdaQueryWrapper<Flight>()
                .eq(StrUtil.isNotBlank(date), Flight::getFlightDate, date != null ? LocalDate.parse(date) : null)
                .eq(status != null, Flight::getStatus, status)
                .like(keyword != null, Flight::getFlightNo, keyword)
                .eq(Flight::getDeleted, 0)
                .orderByDesc(Flight::getFlightDate)
                .orderByAsc(Flight::getDepartureTime);

        Page<Flight> pageObj = new Page<>(page, pageSize);
        Page<Flight> result = flightMapper.selectPage(pageObj, wrapper);
        List<Flight> records = result.getRecords();

        // 批量查询：flightId -> [booked(已订旅客), checkedIn(其中已值机)] + 各航班可售座位 capacity
        List<Long> flightIds = records.stream().map(Flight::getId).collect(Collectors.toList());
        Map<Long, int[]> passengerCounts = new HashMap<>();
        Map<Long, Integer> capacityMap = new HashMap<>();
        if (!flightIds.isEmpty()) {
            // 可售座位数 = 该航班所有舱位 totalSeats 之和（新航班一建即有值，分母不为 0）
            flightCabinMapper.selectList(new LambdaQueryWrapper<FlightCabin>()
                            .in(FlightCabin::getFlightId, flightIds)
                            .eq(FlightCabin::getDeleted, 0))
                    .forEach(c -> {
                        if (c.getTotalSeats() != null) {
                            capacityMap.merge(c.getFlightId(), c.getTotalSeats(), Integer::sum);
                        }
                    });
            // 旅客数口径：仅统计有效订单（剔除已取消 CANCELLED / 已退票 REFUNDED），与「已订座」一致
            List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                    .in(Order::getFlightId, flightIds)
                    .eq(Order::getDeleted, 0)
                    .notIn(Order::getStatus, List.of("CANCELLED", "REFUNDED")));
            if (!orders.isEmpty()) {
                Map<Long, Long> orderFlightMap = orders.stream().collect(Collectors.toMap(Order::getId, Order::getFlightId));
                List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
                List<OrderPassenger> passengers = orderPassengerMapper.selectList(
                        new LambdaQueryWrapper<OrderPassenger>().in(OrderPassenger::getOrderId, orderIds));
                for (OrderPassenger p : passengers) {
                    Long fid = orderFlightMap.get(p.getOrderId());
                    if (fid == null) continue;
                    int[] cnt = passengerCounts.computeIfAbsent(fid, k -> new int[2]);
                    cnt[0]++;
                    if ("CHECKED_IN".equals(p.getCheckinStatus())) {
                        cnt[1]++;
                    }
                }
            }
        }

        // 批量查询飞机信息（机型 + 机号）
        Set<Long> aircraftIds = records.stream().map(Flight::getAircraftId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Aircraft> aircraftMap = new HashMap<>();
        Map<Long, AircraftType> aircraftTypeMap = new HashMap<>();
        if (!aircraftIds.isEmpty()) {
            aircraftMapper.selectBatchIds(aircraftIds).forEach(a -> aircraftMap.put(a.getId(), a));
            Set<Long> typeIds = aircraftMap.values().stream().map(Aircraft::getAircraftTypeId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!typeIds.isEmpty()) {
                aircraftTypeMapper.selectBatchIds(typeIds).forEach(t -> aircraftTypeMap.put(t.getId(), t));
            }
        }

        List<Map<String, Object>> list = records.stream().map(flight -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("flightId", flight.getId().toString());
            m.put("flightNo", flight.getFlightNo());
            m.put("date", flight.getFlightDate());

            Map<String, Object> routeMap = new HashMap<>();
            routeMap.put("departure", flight.getDepartureAirport());
            routeMap.put("arrival", flight.getArrivalAirport());
            Airport dep = airportMapper.selectByCode(flight.getDepartureAirport());
            Airport arr = airportMapper.selectByCode(flight.getArrivalAirport());
            if (dep != null) routeMap.put("departureName", dep.getName());
            if (arr != null) routeMap.put("arrivalName", arr.getName());
            m.put("route", routeMap);

            Map<String, Object> schedule = new HashMap<>();
            schedule.put("departureTime", flight.getDepartureTime() != null ? flight.getDepartureTime().toLocalTime().toString() : null);
            schedule.put("arrivalTime", flight.getArrivalTime() != null ? flight.getArrivalTime().toLocalTime().toString() : null);
            schedule.put("duration", flight.getDuration());
            m.put("schedule", schedule);

            m.put("status", flight.getStatus());
            m.put("isPast", flight.getDepartureTime() != null && flight.getDepartureTime().isBefore(LocalDateTime.now()));
            m.put("departureDateTime", flight.getDepartureTime() != null ? flight.getDepartureTime().toString() : null);
            m.put("flightType", flight.getFlightType());
            m.put("airlineId", flight.getAirlineId());
            m.put("departureTerminal", flight.getDepartureTerminal());
            m.put("arrivalTerminal", flight.getArrivalTerminal());
            m.put("remark", flight.getRemark());
            m.put("checkinOpenHours", flight.getCheckinOpenHours());
            m.put("checkinCloseMinutes", flight.getCheckinCloseMinutes());

            int[] cnt = passengerCounts.getOrDefault(flight.getId(), new int[2]);
            // 可售座位：优先取舱位总座位之和；未配舱位时回退到机型总座位
            Integer cap = capacityMap.get(flight.getId());
            if (cap == null || cap == 0) {
                Aircraft ac0 = aircraftMap.get(flight.getAircraftId());
                AircraftType at0 = (ac0 != null && ac0.getAircraftTypeId() != null)
                        ? aircraftTypeMap.get(ac0.getAircraftTypeId()) : null;
                if (at0 != null && at0.getTotalSeats() != null) cap = at0.getTotalSeats();
            }
            Map<String, Object> pm = new HashMap<>();
            pm.put("booked", cnt[0]);                    // 已订旅客数（有效订单，剔除取消/退票）
            pm.put("total", cnt[0]);                     // 兼容旧字段
            pm.put("checkedIn", cnt[1]);                 // 其中已值机人数
            pm.put("capacity", cap != null ? cap : 0);   // 可售座位（作分母）
            m.put("passengers", pm);

            Map<String, Object> aircraftInfo = new HashMap<>();
            Aircraft aircraft = aircraftMap.get(flight.getAircraftId());
            if (aircraft != null) {
                aircraftInfo.put("registration", aircraft.getRegistration());
                AircraftType at = aircraftTypeMap.get(aircraft.getAircraftTypeId());
                aircraftInfo.put("type", at != null ? at.getCode() : null);
            }
            m.put("aircraft", aircraftInfo);

            return m;
        }).collect(Collectors.toList());

        return PageResult.of(list, page, pageSize, result.getTotal());
    }

    @Override
    @Transactional
    public Map<String, Object> createFlight(AdminFlightCreateDTO dto) {
        Flight flight = new Flight();
        flight.setFlightNo(dto.getFlightNo());
        if (StrUtil.isNotBlank(dto.getDate())) {
            flight.setFlightDate(LocalDate.parse(dto.getDate()));
        }
        if (dto.getRoute() != null) {
            flight.setDepartureAirport(dto.getRoute().getDeparture());
            flight.setArrivalAirport(dto.getRoute().getArrival());
        }
        if (dto.getSchedule() != null && flight.getFlightDate() != null) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
            LocalDate date = flight.getFlightDate();
            flight.setDepartureTime(LocalDateTime.of(date, LocalTime.parse(dto.getSchedule().getDepartureTime(), fmt)));
            flight.setArrivalTime(LocalDateTime.of(date, LocalTime.parse(dto.getSchedule().getArrivalTime(), fmt)));
            flight.setDuration((int) java.time.Duration.between(flight.getDepartureTime(), flight.getArrivalTime()).toMinutes());
        }
        if (dto.getAirlineId() != null) {
            flight.setAirlineId(dto.getAirlineId());
        }
        flight.setFlightType(dto.getFlightType());
        flight.setStatus(FlightStatus.SCHEDULED.name());
        flight.setCheckinOpenHours(dto.getCheckinOpenHours() != null ? dto.getCheckinOpenHours() : 24);
        flight.setCheckinCloseMinutes(dto.getCheckinCloseMinutes() != null ? dto.getCheckinCloseMinutes() : 30);
        flight.setStops(0);
        flight.setDepartureTerminal(dto.getDepartureTerminal());
        flight.setArrivalTerminal(dto.getArrivalTerminal());
        flight.setRemark(dto.getRemark());
        if (dto.getAircraft() != null) {
            flight.setAircraftId(resolveAircraft(dto.getAircraft().getType(),
                    dto.getAircraft().getRegistration(), dto.getAirlineId()));
        }
        flightMapper.insert(flight);
        groundHandlingService.generateNodesForFlight(flight.getId());
        // 自动生成默认舱位
        generateDefaultCabins(flight, dto);

        log.info("航班创建成功: flightNo={}, date={}", dto.getFlightNo(), dto.getDate());

        Map<String, Object> result = new HashMap<>();
        result.put("flightId", flight.getId().toString());
        result.put("flightNo", flight.getFlightNo());
        return result;
    }

    @Override
    @Transactional
    public void updateFlight(Long flightId, AdminFlightCreateDTO dto) {
        Flight flight = flightMapper.selectById(flightId);
        if (flight == null) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "航班不存在");
        }
        if (dto.getFlightNo() != null) flight.setFlightNo(dto.getFlightNo());
        if (dto.getDate() != null) flight.setFlightDate(LocalDate.parse(dto.getDate()));
        if (dto.getRoute() != null) {
            if (dto.getRoute().getDeparture() != null) flight.setDepartureAirport(dto.getRoute().getDeparture());
            if (dto.getRoute().getArrival() != null) flight.setArrivalAirport(dto.getRoute().getArrival());
        }
        if (dto.getSchedule() != null && dto.getDate() != null) {
            String date = dto.getDate();
            if (dto.getSchedule().getDepartureTime() != null) {
                flight.setDepartureTime(LocalDateTime.parse(date + "T" + dto.getSchedule().getDepartureTime() + ":00"));
            }
            if (dto.getSchedule().getArrivalTime() != null) {
                flight.setArrivalTime(LocalDateTime.parse(date + "T" + dto.getSchedule().getArrivalTime() + ":00"));
            }
        }
        if (dto.getFlightType() != null) flight.setFlightType(dto.getFlightType());
        if (dto.getAirlineId() != null) flight.setAirlineId(dto.getAirlineId());
        if (dto.getDepartureTerminal() != null) flight.setDepartureTerminal(dto.getDepartureTerminal());
        if (dto.getArrivalTerminal() != null) flight.setArrivalTerminal(dto.getArrivalTerminal());
        if (dto.getRemark() != null) flight.setRemark(dto.getRemark());
        if (dto.getCheckinOpenHours() != null) flight.setCheckinOpenHours(dto.getCheckinOpenHours());
        if (dto.getCheckinCloseMinutes() != null) flight.setCheckinCloseMinutes(dto.getCheckinCloseMinutes());
        if (dto.getAircraft() != null) {
            flight.setAircraftId(resolveAircraft(dto.getAircraft().getType(),
                    dto.getAircraft().getRegistration(), dto.getAirlineId()));
        }
        flightMapper.updateById(flight);
    }

    @Override
    @Transactional
    public void deleteFlight(Long flightId) {
        releaseGatesForFlight(flightId);
        flightMapper.deleteById(flightId);
    }

    @Override
    @Transactional
    public void batchOperate(List<Long> flightIds, String action, Map<String, Object> params) {
        for (Long id : flightIds) {
            Flight flight = flightMapper.selectById(id);
            if (flight == null) continue;
            if ("CANCEL".equals(action)) {
                // 走状态机校验并推进（已终止态的航班会被跳过）
                try {
                    flightStateMachine.transition(flight, FlightStatus.CANCELLED, "批量取消");
                } catch (BusinessException e) {
                    log.warn("航班 {} 无法取消：{}", flight.getFlightNo(), e.getMessage());
                }
                releaseGatesForFlight(id);
            } else if ("UPDATE_STATUS".equals(action) && params != null && params.containsKey("status")) {
                // 管理员强制指定状态（绕过状态机，慎用）
                flight.setStatus((String) params.get("status"));
                flightMapper.updateById(flight);
            }
        }
    }

    @Override
    public Map<String, Object> getSchedule(String view, String startDate, String endDate, String route) {
        LambdaQueryWrapper<Flight> wrapper = new LambdaQueryWrapper<Flight>()
                .eq(Flight::getDeleted, 0);

        if (StrUtil.isNotBlank(startDate)) {
            wrapper.ge(Flight::getFlightDate, LocalDate.parse(startDate));
        }
        if (StrUtil.isNotBlank(endDate)) {
            wrapper.le(Flight::getFlightDate, LocalDate.parse(endDate));
        }
        if (StrUtil.isNotBlank(route) && route.contains("-")) {
            String[] parts = route.split("-");
            wrapper.eq(Flight::getDepartureAirport, parts[0].trim())
                   .eq(Flight::getArrivalAirport, parts[1].trim());
        }

        List<Flight> flights = flightMapper.selectList(
                wrapper.orderByAsc(Flight::getFlightDate)
                        .orderByAsc(Flight::getDepartureTime));

        // 统一 isPast 判断和状态映射，与航班计划列表保持一致
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> result = new HashMap<>();
        result.put("view", view);
        result.put("list", flights.stream().map(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("flightId", f.getId().toString());
            m.put("flightNo", f.getFlightNo());
            m.put("date", f.getFlightDate());

            Map<String, Object> routeMap = new HashMap<>();
            routeMap.put("departure", f.getDepartureAirport());
            routeMap.put("arrival", f.getArrivalAirport());
            m.put("route", routeMap);

            Map<String, Object> schedule = new HashMap<>();
            schedule.put("departureTime", f.getDepartureTime() != null ? f.getDepartureTime().toLocalTime().toString() : null);
            schedule.put("arrivalTime", f.getArrivalTime() != null ? f.getArrivalTime().toLocalTime().toString() : null);
            m.put("schedule", schedule);

            // 与航班计划列表一致的 isPast 判断
            boolean isPast = f.getDepartureTime() != null && f.getDepartureTime().isBefore(now);
            m.put("status", f.getStatus());
            m.put("isPast", isPast);
            return m;
        }).collect(Collectors.toList()));
        return result;
    }

    @Override
    @Transactional
    public void handleIrregular(Long flightId, String type, String reason, Map<String, Object> newSchedule,
                                boolean notify, Map<String, Object> arrangements) {
        Flight flight = flightMapper.selectById(flightId);
        if (flight == null) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "航班不存在");
        }

        String oldStatus = flight.getStatus();
        LocalDateTime originalDepartureTime = flight.getDepartureTime(); // 保存原始起飞时间，用于计算延误分钟
        String iataDelayCode = arrangements != null ? (String) arrangements.get("iataDelayCode") : null;
        Integer delayMinutes = arrangements != null ? (Integer) arrangements.get("delayMinutes") : null; // null表示稍后自动计算

        FlightStatus targetStatus;
        switch (type) {
            case "DELAY" -> {
                targetStatus = FlightStatus.DELAYED;
                if (iataDelayCode != null) flight.setIataDelayCode(iataDelayCode);
            }
            case "CANCEL" -> targetStatus = FlightStatus.CANCELLED;
            case "DIVERSION" -> targetStatus = FlightStatus.DIVERTED;
            case "RETURN" -> targetStatus = FlightStatus.RETURNED;
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的不正常航班类型: " + type);
        }

        // 如果提供了新时刻表，更新延误后的新时间
        if (newSchedule != null && "DELAY".equals(type)) {
            LocalDate date = flight.getFlightDate() != null ? flight.getFlightDate() : LocalDate.now();
            if (newSchedule.get("departureTime") != null) {
                String dep = (String) newSchedule.get("departureTime");
                if (dep.length() > 16) {
                    // 完整日期时间（如 2026-06-25T14:30:00），直接取前19位解析
                    flight.setDepartureTime(LocalDateTime.parse(dep.substring(0, 19)));
                } else {
                    // 仅时间（如 14:30），组装日期
                    flight.setDepartureTime(LocalDateTime.parse(date + "T" + (dep.contains("T") ? dep : dep + ":00")));
                }
            }
            if (newSchedule.get("arrivalTime") != null) {
                String arr = (String) newSchedule.get("arrivalTime");
                if (arr.length() > 16) {
                    flight.setArrivalTime(LocalDateTime.parse(arr.substring(0, 19)));
                } else {
                    flight.setArrivalTime(LocalDateTime.parse(date + "T" + (arr.contains("T") ? arr : arr + ":00")));
                }
            }
            // 自动计算延误分钟数（如果前端未通过arrangements显式传入）
            if (delayMinutes == null && originalDepartureTime != null && flight.getDepartureTime() != null) {
                delayMinutes = (int) java.time.Duration.between(originalDepartureTime, flight.getDepartureTime()).toMinutes();
            }
        }

        // 回退到0，避免delayMinutes仍为null
        if (delayMinutes == null) {
            delayMinutes = 0;
        }

        flightStateMachine.transition(flight, targetStatus, reason);
        groundHandlingService.autoCompleteByFlightStatus(flightId, flight.getStatus());

        // 记录不正常航班操作
        IrregularOperation op = new IrregularOperation();
        op.setFlightId(flightId);
        op.setType(type);
        op.setIataDelayCode(iataDelayCode);
        op.setReason(reason);
        op.setDelayMinutes(delayMinutes != null ? delayMinutes : 0);
        op.setAutoRebooked(0);
        op.setNotifyPassengers(notify ? 1 : 0);
        op.setStatus("PROCESSED");
        irregularOperationMapper.insert(op);
        Long irregularOpId = op.getId();

        // 自动生成告警（关联到flight）
        String level = "DELAY".equals(type) || "CANCEL".equals(type) ? "IMPORTANT" : "NORMAL";
        String actionLabel = switch (type) {
            case "DELAY" -> "延误";
            case "CANCEL" -> "取消";
            case "DIVERSION" -> "备降";
            case "RETURN" -> "返航";
            default -> "异常";
        };
        String alertTitle = flight.getFlightNo() + " 航班" + actionLabel;
        String alertContent = flight.getFlightNo() + " " + flight.getDepartureAirport() + "-" + flight.getArrivalAirport()
                + " 航班因" + reason + actionLabel + "处理";
        adminMonitorService.createAlert(level, type, alertTitle, alertContent);

        // ---- 航班取消时自动改签 ----
        List<Map<String, Object>> rebookingResults = new ArrayList<>();
        if ("CANCEL".equals(type)) {
            rebookingResults = autoRebookPassengers(flight, notify, irregularOpId);
        }

        // ---- 推送到旅客端（航班动态WebSocket）----
        String flightJson = String.format(
                "{\"flightNo\":\"%s\",\"status\":\"%s\",\"reason\":\"%s\",\"delayMinutes\":%d}",
                flight.getFlightNo(), flight.getStatus(), reason, delayMinutes != null ? delayMinutes : 0
        );
        flightStatusWebSocket.pushFlightUpdate(flight.getFlightNo(), flightJson);

        // ---- 推送到管理端（IROPS WebSocket）----
        Map<String, Object> irregularData = new LinkedHashMap<>();
        irregularData.put("flightId", flight.getId().toString());
        irregularData.put("flightNo", flight.getFlightNo());
        irregularData.put("type", type);
        irregularData.put("reason", reason);
        irregularData.put("oldStatus", oldStatus);
        irregularData.put("newStatus", flight.getStatus());
        irregularData.put("delayMinutes", delayMinutes);
        irregularData.put("rebookingResults", rebookingResults);
        notificationService.pushIrregularToAdmin(irregularData);

        // ---- 通知旅客（每个用户最多推送一次）----
        if (notify) {
            List<Order> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<Order>().eq(Order::getFlightId, flightId).eq(Order::getDeleted, 0));
            String actionName = "DELAY".equals(type) ? "延误" : "CANCEL".equals(type) ? "已取消" : "异常";
            String msg = String.format("您的航班%s因%s%s，请登录APP查看详情。",
                    flight.getFlightNo(), reason, actionName);
            // 按 userId 去重，避免同一用户多笔订单时重复推送
            Set<Long> notified = new HashSet<>();
            for (Order order : orders) {
                if (order.getUserId() != null && notified.add(order.getUserId())) {
                    notificationService.notifyPassenger(order.getUserId(), alertTitle, msg, "FLIGHT", flightId);
                }
            }
        }

        log.info("不正常航班处理完成: flightNo={}, type={}, reason={}, iataCode={}, delay={}min, rebooked={}",
                flight.getFlightNo(), type, reason, iataDelayCode, delayMinutes, rebookingResults.size());
    }

    /**
     * 航班取消时自动查找可用航班并改签旅客
     */
    private List<Map<String, Object>> autoRebookPassengers(Flight cancelledFlight, boolean notify, Long irregularOpId) {
        List<Map<String, Object>> results = new ArrayList<>();

        // 查找同航线、今日之后、有可用舱位的替代航班
        List<Flight> alternatives = flightMapper.selectList(
                new LambdaQueryWrapper<Flight>()
                        .eq(Flight::getDepartureAirport, cancelledFlight.getDepartureAirport())
                        .eq(Flight::getArrivalAirport, cancelledFlight.getArrivalAirport())
                        .ge(Flight::getFlightDate, cancelledFlight.getFlightDate())
                        .in(Flight::getStatus, "SCHEDULED", "FLYING")
                        .eq(Flight::getDeleted, 0)
                        .ne(Flight::getId, cancelledFlight.getId())
                        .orderByAsc(Flight::getFlightDate)
                        .orderByAsc(Flight::getDepartureTime)
                        .last("LIMIT 5"));

        if (alternatives.isEmpty()) {
            log.info("无可用替代航班: route={}-{}", cancelledFlight.getDepartureAirport(), cancelledFlight.getArrivalAirport());
            return results;
        }

        // 查找该航班的所有有效订单
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getFlightId, cancelledFlight.getId())
                        .in(Order::getStatus, "PAID", "ISSUED", "CHECKED_IN")
                        .eq(Order::getDeleted, 0));

        for (Order order : orders) {
            // 尝试在当前订单舱位等级下找替代航班
            FlightCabin bestCabin = null;
            Flight bestFlight = null;

            for (Flight alt : alternatives) {
                List<FlightCabin> cabins = flightCabinMapper.selectList(
                        new LambdaQueryWrapper<FlightCabin>()
                                .eq(FlightCabin::getFlightId, alt.getId())
                                .eq(FlightCabin::getCabinClass, order.getCabinClass())
                                .eq(FlightCabin::getDeleted, 0)
                                .last("LIMIT 1"));
                if (!cabins.isEmpty() && cabins.get(0).getAvailableSeats() > 0) {
                    bestCabin = cabins.get(0);
                    bestFlight = alt;
                    break;
                }
            }

            if (bestFlight == null || bestCabin == null) {
                log.warn("未找到适合订单{}的替代航班", order.getOrderId());
                continue;
            }

            List<OrderPassenger> passengers = orderPassengerMapper.selectList(
                    new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, order.getId()));

            for (OrderPassenger p : passengers) {
                BigDecimal fareDiff = bestCabin.getFare().subtract(order.getFare().divide(BigDecimal.valueOf(Math.max(1, passengers.size())), BigDecimal.ROUND_HALF_UP));

                AutoRebooking rebooking = new AutoRebooking();
                rebooking.setOriginalOrderId(order.getId());
                rebooking.setNewFlightId(bestFlight.getId());
                rebooking.setNewCabinClass(order.getCabinClass());
                rebooking.setPassengerName(p.getPassengerName());
                rebooking.setPassengerIdType(p.getIdType());
                rebooking.setPassengerIdNumber(p.getIdNumber());
                rebooking.setFareDiff(fareDiff);
                rebooking.setAutoProcess(1);
                rebooking.setStatus("PENDING");
                autoRebookingMapper.insert(rebooking);

                Map<String, Object> r = new LinkedHashMap<>();
                r.put("rebookingId", rebooking.getId().toString());
                r.put("orderId", order.getOrderId());
                r.put("passengerName", p.getPassengerName());
                r.put("originalFlightNo", cancelledFlight.getFlightNo());
                r.put("newFlightNo", bestFlight.getFlightNo());
                r.put("newFlightDate", bestFlight.getFlightDate().toString());
                r.put("newCabinClass", order.getCabinClass());
                r.put("fareDiff", fareDiff);
                r.put("status", "PENDING");
                results.add(r);

                // 不再单独推送改签通知，统一在 handleIrregular 中通知
            }
        }

        if (irregularOpId != null) {
            // 无论有无旅客，都标记为已执行（空结果=该航班无旅客需改签）
            irregularOperationMapper.update(null, new LambdaUpdateWrapper<IrregularOperation>()
                    .eq(IrregularOperation::getId, irregularOpId)
                    .set(IrregularOperation::getAutoRebooked, 1));
        }

        return results;
    }

    @Override
    public List<Map<String, Object>> getFlightLogs(Long flightId) {
        List<FlightStatusLog> logs = statusLogMapper.selectList(
                new LambdaQueryWrapper<FlightStatusLog>()
                        .eq(FlightStatusLog::getFlightId, flightId)
                        .orderByDesc(FlightStatusLog::getCreateTime));
        return logs.stream().map(l -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", l.getId());
            m.put("oldStatus", l.getOldStatus());
            m.put("newStatus", l.getNewStatus());
            m.put("reason", l.getReason());
            // 操作人：调度人工改状态时有值，定时任务自动流转时为空（前端显示「系统自动」）
            m.put("operatorName", l.getOperatorName());
            m.put("createTime", l.getCreateTime());
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 解析飞机：按机号查找，不存在则创建；机型按 code/name 查找，不存在则创建。
     *
     * @return 飞机主键 ID，机号为空时返回 null
     */
    private Long resolveAircraft(String typeCode, String registration, Long airlineId) {
        if (StrUtil.isBlank(registration)) {
            return null;
        }
        // 1. 机型：按 code/name 查找，找不到则新建
        AircraftType aircraftType = null;
        if (StrUtil.isNotBlank(typeCode)) {
            aircraftType = aircraftTypeMapper.selectOne(
                    new LambdaQueryWrapper<AircraftType>()
                            .eq(AircraftType::getCode, typeCode)
                            .eq(AircraftType::getDeleted, 0));
            if (aircraftType == null) {
                aircraftType = aircraftTypeMapper.selectOne(
                        new LambdaQueryWrapper<AircraftType>()
                                .eq(AircraftType::getName, typeCode)
                                .eq(AircraftType::getDeleted, 0));
            }
            if (aircraftType == null) {
                AircraftType t = new AircraftType();
                t.setCode(typeCode);
                t.setName(typeCode);
                aircraftTypeMapper.insert(t);
                aircraftType = t;
            }
        }
        // 2. 飞机：按机号查找，找不到则新建
        Aircraft aircraft = aircraftMapper.selectOne(
                new LambdaQueryWrapper<Aircraft>()
                        .eq(Aircraft::getRegistration, registration)
                        .eq(Aircraft::getDeleted, 0));
        if (aircraft == null) {
            aircraft = new Aircraft();
            aircraft.setRegistration(registration);
            aircraft.setAircraftTypeId(aircraftType != null ? aircraftType.getId() : null);
            aircraft.setAirlineId(airlineId);
            aircraft.setStatus("ACTIVE");
            aircraftMapper.insert(aircraft);
        } else if (aircraftType != null && aircraft.getAircraftTypeId() == null) {
            aircraft.setAircraftTypeId(aircraftType.getId());
            aircraftMapper.updateById(aircraft);
        }
        return aircraft.getId();
    }

    /** 为航班生成默认舱位（从 AirlineFare 标准票价表读取） */
    private void generateDefaultCabins(Flight flight, AdminFlightCreateDTO dto) {
        String[][] cabinDefs = {{"ECONOMY", "经济舱", "150"}, {"BUSINESS", "商务舱", "8"}, {"FIRST", "头等舱", "4"}};
        for (String[] def : cabinDefs) {
            String cabinClass = def[0];
            String cabinName = def[1];
            int totalSeats = Integer.parseInt(def[2]);

            // 从标准票价表读取
            AirlineFare fareRule = null;
            if (flight.getAirlineId() != null) {
                fareRule = airlineFareMapper.selectOne(
                        new LambdaQueryWrapper<AirlineFare>()
                                .eq(AirlineFare::getAirlineId, flight.getAirlineId())
                                .eq(AirlineFare::getCabinClass, cabinClass));
            }

            FlightCabin cabin = new FlightCabin();
            cabin.setFlightId(flight.getId());
            cabin.setCabinClass(cabinClass);
            cabin.setCabinName(cabinName);
            cabin.setTotalSeats(totalSeats);
            cabin.setAvailableSeats(totalSeats);

            if (fareRule != null) {
                cabin.setFare(fareRule.getFare());
                cabin.setTax(fareRule.getTax());
                cabin.setTotalPrice(fareRule.getFare().add(fareRule.getTax()));
                cabin.setBaggage(fareRule.getBaggage());
                cabin.setRefundRule(fareRule.getRefundRule());
                cabin.setChangeRule(fareRule.getChangeRule());
            } else {
                // 无标准票价时按时长估算
                int duration = flight.getDuration() != null ? flight.getDuration() : 120;
                BigDecimal baseFare = BigDecimal.valueOf(duration * 0.8).max(BigDecimal.valueOf(300));
                baseFare = baseFare.divide(BigDecimal.TEN, 0, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.TEN);
                BigDecimal multiplier = "ECONOMY".equals(cabinClass) ? BigDecimal.ONE
                        : "BUSINESS".equals(cabinClass) ? BigDecimal.valueOf(2.5)
                        : BigDecimal.valueOf(4);
                cabin.setFare(baseFare.multiply(multiplier));
                cabin.setTax(BigDecimal.valueOf(50));
                cabin.setTotalPrice(cabin.getFare().add(cabin.getTax()));
                cabin.setBaggage("20KG");
                cabin.setRefundRule("起飞前24小时免费退改");
                cabin.setChangeRule("起飞前24小时免费改签");
            }
            flightCabinMapper.insert(cabin);
        }
    }
}
