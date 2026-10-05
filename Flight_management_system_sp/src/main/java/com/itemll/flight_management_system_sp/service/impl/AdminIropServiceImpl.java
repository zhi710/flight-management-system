package com.itemll.flight_management_system_sp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.entity.*;
import com.itemll.flight_management_system_sp.mapper.*;
import com.itemll.flight_management_system_sp.security.FlightStatusWebSocketHandler;
import com.itemll.flight_management_system_sp.service.AdminIropService;
import com.itemll.flight_management_system_sp.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminIropServiceImpl implements AdminIropService {

    private final IrregularOperationMapper irregularOperationMapper;
    private final AutoRebookingMapper autoRebookingMapper;
    private final FlightMapper flightMapper;
    private final OrderMapper orderMapper;
    private final OrderPassengerMapper orderPassengerMapper;
    private final FlightCabinMapper flightCabinMapper;
    private final SysConfigMapper sysConfigMapper;
    private final NotificationService notificationService;
    private final FlightStatusWebSocketHandler flightStatusWebSocket;

    @Override
    public PageResult<Map<String, Object>> getIropList(String flightNo, String type, String status, int page, int pageSize) {
        LambdaQueryWrapper<IrregularOperation> wrapper = new LambdaQueryWrapper<IrregularOperation>()
                .orderByDesc(IrregularOperation::getCreateTime);

        if (StrUtil.isNotBlank(type)) wrapper.eq(IrregularOperation::getType, type);
        if (StrUtil.isNotBlank(status)) wrapper.eq(IrregularOperation::getStatus, status);

        // flightNo 需要关联 flight 表，先查 flight
        if (StrUtil.isNotBlank(flightNo)) {
            List<Flight> flights = flightMapper.selectList(
                    new LambdaQueryWrapper<Flight>()
                            .eq(Flight::getFlightNo, flightNo)
                            .eq(Flight::getDeleted, 0));
            if (!flights.isEmpty()) {
                wrapper.in(IrregularOperation::getFlightId, flights.stream().map(Flight::getId).collect(Collectors.toList()));
            }
        }

        Page<IrregularOperation> pageObj = new Page<>(page, pageSize);
        Page<IrregularOperation> result = irregularOperationMapper.selectPage(pageObj, wrapper);

        List<Map<String, Object>> list = result.getRecords().stream().map(op -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", op.getId().toString());
            m.put("flightId", op.getFlightId().toString());
            m.put("type", op.getType());
            m.put("iataDelayCode", op.getIataDelayCode());
            m.put("reason", op.getReason());
            m.put("delayMinutes", op.getDelayMinutes());
            m.put("compensationType", op.getCompensationType());
            m.put("compensationAmount", op.getCompensationAmount());
            m.put("autoRebooked", op.getAutoRebooked());
            m.put("notifyPassengers", op.getNotifyPassengers());
            m.put("status", op.getStatus());
            m.put("createdAt", op.getCreateTime());

            Flight flight = flightMapper.selectById(op.getFlightId());
            if (flight != null) {
                m.put("flightNo", flight.getFlightNo());
                m.put("route", flight.getDepartureAirport() + "-" + flight.getArrivalAirport());
                m.put("flightDate", flight.getFlightDate());
            }
            return m;
        }).collect(Collectors.toList());

        return PageResult.of(list, page, pageSize, result.getTotal());
    }

    @Override
    public List<Map<String, Object>> getAffectedPassengers(Long flightId) {
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getFlightId, flightId)
                        .in(Order::getStatus, "PAID", "ISSUED", "CHECKED_IN")
                        .eq(Order::getDeleted, 0));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Order order : orders) {
            List<OrderPassenger> passengers = orderPassengerMapper.selectList(
                    new LambdaQueryWrapper<OrderPassenger>()
                            .eq(OrderPassenger::getOrderId, order.getId()));

            for (OrderPassenger p : passengers) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orderId", order.getOrderId());
                m.put("pnr", order.getPnr());
                m.put("passengerName", p.getPassengerName());
                m.put("idType", p.getIdType());
                m.put("idNumber", p.getIdNumber());
                m.put("phone", p.getPhone());
                m.put("cabinClass", order.getCabinClass());
                m.put("ticketStatus", order.getStatus());
                result.add(m);
            }
        }
        return result;
    }

    @Override
    public PageResult<Map<String, Object>> getRebookingList(Long flightId, String status, int page, int pageSize) {
        LambdaQueryWrapper<AutoRebooking> wrapper = new LambdaQueryWrapper<AutoRebooking>()
                .orderByDesc(AutoRebooking::getCreateTime);

        if (StrUtil.isNotBlank(status)) wrapper.eq(AutoRebooking::getStatus, status);

        // 如果传了flightId，通过原订单关联（无订单时返回空）
        if (flightId != null) {
            List<Order> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<Order>().eq(Order::getFlightId, flightId));
            if (!orders.isEmpty()) {
                wrapper.in(AutoRebooking::getOriginalOrderId,
                        orders.stream().map(Order::getId).collect(Collectors.toList()));
            } else {
                // 该航班无人购票，不可能有改签记录，直接返回空
                return PageResult.of(List.of(), page, pageSize, 0);
            }
        }

        Page<AutoRebooking> pageObj = new Page<>(page, pageSize);
        Page<AutoRebooking> result = autoRebookingMapper.selectPage(pageObj, wrapper);

        List<Map<String, Object>> list = result.getRecords().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rebookingId", r.getId().toString());
            m.put("originalOrderId", r.getOriginalOrderId().toString());
            m.put("newFlightId", r.getNewFlightId().toString());
            m.put("newCabinClass", r.getNewCabinClass());
            m.put("passengerName", r.getPassengerName());
            m.put("fareDiff", r.getFareDiff());
            m.put("autoProcess", r.getAutoProcess());
            m.put("status", r.getStatus());
            m.put("createdAt", r.getCreateTime());

            Flight newFlight = flightMapper.selectById(r.getNewFlightId());
            if (newFlight != null) {
                m.put("newFlightNo", newFlight.getFlightNo());
                m.put("newFlightDate", newFlight.getFlightDate());
                m.put("newRoute", newFlight.getDepartureAirport() + "-" + newFlight.getArrivalAirport());
            }

            Order originalOrder = orderMapper.selectById(r.getOriginalOrderId());
            if (originalOrder != null) {
                m.put("orderNo", originalOrder.getOrderNo());
                m.put("pnr", originalOrder.getPnr());
            }
            return m;
        }).collect(Collectors.toList());

        return PageResult.of(list, page, pageSize, result.getTotal());
    }

    @Override
    @Transactional
    public void confirmRebooking(Long rebookingId, String action, Long adminId) {
        AutoRebooking rebooking = autoRebookingMapper.selectById(rebookingId);
        if (rebooking == null) {
            throw new BusinessException("改签记录不存在");
        }

        if ("APPROVED".equals(action)) {
            rebooking.setStatus("APPROVED");
            rebooking.setExecuteTime(LocalDateTime.now());

            // 创建新订单（简化：直接更新原订单为新航班）
            Order order = orderMapper.selectById(rebooking.getOriginalOrderId());
            if (order != null) {
                order.setFlightId(rebooking.getNewFlightId());
                Flight newFlight = flightMapper.selectById(rebooking.getNewFlightId());
                if (newFlight != null) {
                    order.setFlightNo(newFlight.getFlightNo());
                }
                order.setCabinClass(rebooking.getNewCabinClass());
                order.setRebookedFromId(rebooking.getOriginalOrderId());
                orderMapper.updateById(order);
            }
        } else {
            rebooking.setStatus("REJECTED");
        }
        rebooking.setOperatorId(adminId);
        autoRebookingMapper.updateById(rebooking);
    }

    @Override
    @Transactional
    public Map<String, Object> manualRebook(Long orderId, Long newFlightId, String newCabinClass, Long adminId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        Flight newFlight = flightMapper.selectById(newFlightId);
        if (newFlight == null) {
            throw new BusinessException("新航班不存在");
        }

        FlightCabin cabin = flightCabinMapper.selectOne(
                new LambdaQueryWrapper<FlightCabin>()
                        .eq(FlightCabin::getFlightId, newFlightId)
                        .eq(FlightCabin::getCabinClass, newCabinClass)
                        .eq(FlightCabin::getDeleted, 0));
        if (cabin == null || cabin.getAvailableSeats() <= 0) {
            throw new BusinessException("新航班舱位已满");
        }

        // 创建改签记录
        AutoRebooking rebooking = new AutoRebooking();
        rebooking.setOriginalOrderId(orderId);
        rebooking.setNewFlightId(newFlightId);
        rebooking.setNewCabinClass(newCabinClass);
        rebooking.setPassengerName(order.getContactName());
        rebooking.setFareDiff(cabin.getFare().subtract(order.getFare()));
        rebooking.setAutoProcess(0);
        rebooking.setStatus("APPROVED");
        rebooking.setOperatorId(adminId);
        rebooking.setExecuteTime(LocalDateTime.now());
        autoRebookingMapper.insert(rebooking);

        // 保存原始航班号，供推送通知使用
        String originalFlightNo = order.getFlightNo();

        // 更新原订单
        order.setFlightId(newFlightId);
        order.setFlightNo(newFlight.getFlightNo());
        order.setCabinClass(newCabinClass);
        order.setRebookedFromId(orderId);
        orderMapper.updateById(order);

        // 扣减座位
        flightCabinMapper.decrementSeats(cabin.getId(), 1, cabin.getVersion());

        // 推送通知
        notificationService.pushRebookingToPassenger(order.getUserId(), Map.of(
                "rebookingId", rebooking.getId().toString(),
                "originalFlightNo", originalFlightNo,
                "newFlightNo", newFlight.getFlightNo(),
                "newFlightDate", newFlight.getFlightDate().toString(),
                "newCabinClass", newCabinClass,
                "status", "APPROVED"
        ));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rebookingId", rebooking.getId().toString());
        result.put("newFlightNo", newFlight.getFlightNo());
        result.put("newFlightDate", newFlight.getFlightDate().toString());
        result.put("newCabinClass", newCabinClass);
        result.put("status", "APPROVED");
        return result;
    }

    @Override
    public void notifyPassengers(Long operationId) {
        IrregularOperation op = irregularOperationMapper.selectById(operationId);
        if (op == null) throw new BusinessException("操作记录不存在");
        Flight flight = flightMapper.selectById(op.getFlightId());
        if (flight == null) throw new BusinessException("航班不存在");

        // 推送到旅客端（航班动态WebSocket）
        String flightJson = String.format(
                "{\"flightNo\":\"%s\",\"status\":\"%s\",\"reason\":\"%s\",\"delayMinutes\":%d}",
                flight.getFlightNo(), flight.getStatus(), op.getReason(), op.getDelayMinutes() != null ? op.getDelayMinutes() : 0);
        flightStatusWebSocket.pushFlightUpdate(flight.getFlightNo(), flightJson);

        // 遍历受影响的订单，推送通知
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>().eq(Order::getFlightId, op.getFlightId()).eq(Order::getDeleted, 0));
        Set<Long> notifiedUserIds = new HashSet<>();
        for (Order order : orders) {
            String content = String.format("您好，您乘坐的 %s 航班因 %s 已%s。", flight.getFlightNo(), op.getReason(),
                    "DELAY".equals(op.getType()) ? "延误" : "CANCEL".equals(op.getType()) ? "取消" : "调整");
            if (order.getContactPhone() != null) {
                notificationService.sendSms(order.getContactPhone(), content, "IROPS", op.getId());
            }
            if (order.getUserId() != null && notifiedUserIds.add(order.getUserId())) {
                notificationService.pushToPassenger(order.getUserId(), "NOTIFICATION",
                        Map.of("title", "航班变动通知", "content", content, "refType", "flight", "refId", flight.getId().toString()));
            }
        }
        log.info("已重新通知 IROPS 操作 {} 的所有受影响旅客", operationId);
    }

    @Override
    public Map<String, Object> getCompensationRules() {
        List<SysConfig> configs = sysConfigMapper.selectList(
                new LambdaQueryWrapper<SysConfig>()
                        .like(SysConfig::getConfigKey, "compensation_"));

        Map<String, Object> rules = new LinkedHashMap<>();
        for (SysConfig c : configs) {
            rules.put(c.getConfigKey(), c.getConfigValue());
        }
        return rules;
    }

    @Override
    public List<Map<String, String>> getIataDelayCodes() {
        return List.of(
                Map.of("code", "81", "description", "流量控制"),
                Map.of("code", "82", "description", "天气原因"),
                Map.of("code", "83", "description", "机场限制"),
                Map.of("code", "84", "description", "空域限制"),
                Map.of("code", "85", "description", "航行管制"),
                Map.of("code", "91", "description", "飞机晚到"),
                Map.of("code", "92", "description", "飞机故障"),
                Map.of("code", "93", "description", "机械故障"),
                Map.of("code", "94", "description", "维护延误"),
                Map.of("code", "95", "description", "机组超时"),
                Map.of("code", "96", "description", "机组调配"),
                Map.of("code", "97", "description", "清洁/配餐"),
                Map.of("code", "98", "description", "旅客原因"),
                Map.of("code", "99", "description", "其他原因")
        );
    }
}
