package com.itemll.flight_management_system_sp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itemll.flight_management_system_sp.common.enums.FlightStatus;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.entity.Flight;
import com.itemll.flight_management_system_sp.entity.GroundHandling;
import com.itemll.flight_management_system_sp.mapper.FlightMapper;
import com.itemll.flight_management_system_sp.mapper.GroundHandlingMapper;
import com.itemll.flight_management_system_sp.security.AdminIrregularWebSocketHandler;
import com.itemll.flight_management_system_sp.service.FlightStateMachine;
import com.itemll.flight_management_system_sp.service.GroundHandlingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroundHandlingServiceImpl implements GroundHandlingService {

    private final GroundHandlingMapper groundHandlingMapper;
    private final FlightMapper flightMapper;
    private final AdminIrregularWebSocketHandler adminIrregularWebSocket;
    private final FlightStateMachine flightStateMachine;

    /** 标准地面保障节点列表 */
    private static final List<Map<String, String>> STANDARD_NODES = List.of(
            Map.of("code", "PUSHBACK", "name", "推出"),
            Map.of("code", "TAXI_OUT", "name", "滑出"),
            Map.of("code", "LADDER", "name", "客梯车"),
            Map.of("code", "BAGGAGE", "name", "行李装舱"),
            Map.of("code", "CATERING", "name", "配餐"),
            Map.of("code", "FUEL", "name", "加油"),
            Map.of("code", "CLEANING", "name", "客舱清洁"),
            Map.of("code", "BOARDING", "name", "旅客登机"),
            Map.of("code", "DOOR_CLOSE", "name", "关舱门"),
            Map.of("code", "PUSHBACK_START", "name", "推出开始"),
            Map.of("code", "TAKE_OFF", "name", "起飞"),
            Map.of("code", "LANDING", "name", "着陆"),
            Map.of("code", "DOOR_OPEN", "name", "开舱门"),
            Map.of("code", "DEBOARDING", "name", "旅客下机"),
            Map.of("code", "BAGGAGE_CLAIM", "name", "行李提取")
    );

    @Override
    public List<Map<String, Object>> getNodesForFlight(Long flightId) {
        List<Map<String, Object>> result = new ArrayList<>();

        // 获取已有记录
        List<GroundHandling> existing = groundHandlingMapper.selectList(
                new LambdaQueryWrapper<GroundHandling>()
                        .eq(GroundHandling::getFlightId, flightId));

        Map<String, GroundHandling> existingMap = existing.stream()
                .collect(Collectors.toMap(GroundHandling::getNodeCode, g -> g));

        Flight flight = flightMapper.selectById(flightId);

        for (Map<String, String> nodeDef : STANDARD_NODES) {
            String code = nodeDef.get("code");
            GroundHandling g = existingMap.get(code);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodeCode", code);
            m.put("nodeName", nodeDef.get("name"));
            if (g != null) {
                m.put("id", g.getId().toString());
                m.put("planTime", g.getPlanTime());
                m.put("actualTime", g.getActualTime());
                m.put("delayMinutes", g.getDelayMinutes());
                m.put("status", g.getStatus());
                m.put("remark", g.getRemark());
            } else {
                m.put("planTime", calcPlanTime(flight, code));
                m.put("actualTime", null);
                m.put("delayMinutes", null);
                m.put("status", "PENDING");
            }
            result.add(m);
        }
        return result;
    }

    @Override
    public PageResult<Map<String, Object>> getGroundHandlingList(String flightNo, String date, String status, int page, int pageSize) {
        Page<GroundHandling> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<GroundHandling> wrapper = new LambdaQueryWrapper<GroundHandling>()
                .orderByDesc(GroundHandling::getPlanTime);

        if (StrUtil.isNotBlank(status)) wrapper.eq(GroundHandling::getStatus, status);

        if (StrUtil.isNotBlank(flightNo) || StrUtil.isNotBlank(date)) {
            List<Flight> flights = flightMapper.selectList(
                    new LambdaQueryWrapper<Flight>()
                            .eq(StrUtil.isNotBlank(flightNo), Flight::getFlightNo, flightNo)
                            .eq(StrUtil.isNotBlank(date), Flight::getFlightDate, date != null ? LocalDate.parse(date) : null)
                            .eq(Flight::getDeleted, 0));
            if (!flights.isEmpty()) {
                wrapper.in(GroundHandling::getFlightId,
                        flights.stream().map(Flight::getId).collect(Collectors.toList()));
            }
        }

        Page<GroundHandling> result = groundHandlingMapper.selectPage(pageObj, wrapper);

        List<Map<String, Object>> list = result.getRecords().stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId().toString());
            m.put("flightId", g.getFlightId().toString());
            m.put("nodeCode", g.getNodeCode());
            m.put("nodeName", g.getNodeName());
            m.put("planTime", g.getPlanTime());
            m.put("actualTime", g.getActualTime());
            m.put("delayMinutes", g.getDelayMinutes());
            m.put("status", g.getStatus());
            m.put("remark", g.getRemark());

            Flight flight = flightMapper.selectById(g.getFlightId());
            if (flight != null) {
                m.put("flightNo", flight.getFlightNo());
                m.put("route", flight.getDepartureAirport() + "-" + flight.getArrivalAirport());
            }
            return m;
        }).collect(Collectors.toList());

        return PageResult.of(list, page, pageSize, result.getTotal());
    }

    @Override
    public void updateNodeTime(Long nodeId, String field, String value, Long operatorId) {
        GroundHandling g = groundHandlingMapper.selectById(nodeId);
        if (g == null) {
            // 自动创建节点记录
            g = new GroundHandling();
            g.setNodeCode(field);
        }

        LocalDateTime time = LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        if ("actualTime".equals(field)) {
            g.setActualTime(time);
            g.setStatus("COMPLETED");
            if (g.getPlanTime() != null) {
                g.setDelayMinutes((int) java.time.Duration.between(g.getPlanTime(), time).toMinutes());
            }
        } else {
            g.setPlanTime(time);
        }
        g.setOperatorId(operatorId);

        if (g.getId() != null) {
            groundHandlingMapper.updateById(g);
        } else {
            groundHandlingMapper.insert(g);
        }

        // WebSocket推送地服更新
        Map<String, Object> wsData = Map.of(
                "nodeId", g.getId().toString(),
                "nodeCode", g.getNodeCode(),
                "field", field,
                "value", value,
                "status", g.getStatus()
        );
        adminIrregularWebSocket.pushToAdmins("GROUND_UPDATE",
                "{\"data\":" + cn.hutool.json.JSONUtil.toJsonStr(wsData) + "}");
    }

    @Override
    public Map<String, Object> getTodaySummary() {
        List<Flight> todayFlights = flightMapper.selectList(
                new LambdaQueryWrapper<Flight>()
                        .eq(Flight::getFlightDate, LocalDate.now())
                        .eq(Flight::getDeleted, 0));

        long total = todayFlights.size();
        long delayed = 0;
        long onTime;

        for (Flight f : todayFlights) {
            List<GroundHandling> nodes = groundHandlingMapper.selectList(
                    new LambdaQueryWrapper<GroundHandling>()
                            .eq(GroundHandling::getFlightId, f.getId()));
            boolean isDelayed = nodes.stream().anyMatch(n -> n.getDelayMinutes() != null && n.getDelayMinutes() > 15);
            if (isDelayed) delayed++;
        }
        onTime = total - delayed;

        return Map.of(
                "totalFlights", total,
                "onTime", onTime,
                "delayed", delayed,
                "punctualityRate", total > 0 ? (double) onTime / total * 100 : 0
        );
    }

    @Override
    @Transactional
    public void generateNodesForFlight(Long flightId) {
        Flight flight = flightMapper.selectById(flightId);
        if (flight == null) {
            log.warn("生成地面保障节点失败：航班不存在 flightId={}", flightId);
            return;
        }
        // 检查是否已生成
        Long existing = groundHandlingMapper.selectCount(
                new LambdaQueryWrapper<GroundHandling>().eq(GroundHandling::getFlightId, flightId));
        if (existing > 0) {
            log.info("地面保障节点已存在，跳过生成 flightId={}", flightId);
            return;
        }
        for (Map<String, String> nodeDef : STANDARD_NODES) {
            GroundHandling g = new GroundHandling();
            g.setFlightId(flightId);
            g.setNodeCode(nodeDef.get("code"));
            g.setNodeName(nodeDef.get("name"));
            LocalDateTime plan = calcPlanTime(flight, nodeDef.get("code"));
            g.setPlanTime(plan != null ? plan : flight.getDepartureTime());
            g.setStatus("PENDING");
            groundHandlingMapper.insert(g);
        }
        log.info("地面保障节点生成成功 flightId={}", flightId);
    }

    @Override
    @Transactional
    public void completeNode(Long flightId, String nodeCode, LocalDateTime actualTime, Long operatorId) {
        GroundHandling g = groundHandlingMapper.selectOne(
                new LambdaQueryWrapper<GroundHandling>()
                        .eq(GroundHandling::getFlightId, flightId)
                        .eq(GroundHandling::getNodeCode, nodeCode));
        if (g == null) return;
        g.setActualTime(actualTime);
        g.setStatus("COMPLETED");
        g.setOperatorId(operatorId);
        if (g.getPlanTime() != null) {
            g.setDelayMinutes((int) java.time.Duration.between(g.getPlanTime(), actualTime).toMinutes());
        }
        groundHandlingMapper.updateById(g);
        log.info("地面保障节点完成 flightId={}, nodeCode={}", flightId, nodeCode);
    }

    @Override
    @Transactional
    public void autoCompleteByFlightStatus(Long flightId, String newStatus) {
        LocalDateTime now = LocalDateTime.now();
        switch (newStatus) {
            case "BOARDING" -> {
                completeNode(flightId, "LADDER", now, null);
                completeNode(flightId, "BOARDING", now, null);
            }
            case "DEPARTED" -> {
                completeNode(flightId, "DOOR_CLOSE", now, null);
                completeNode(flightId, "PUSHBACK", now, null);
                completeNode(flightId, "PUSHBACK_START", now, null);
                completeNode(flightId, "TAXI_OUT", now, null);
                completeNode(flightId, "TAKE_OFF", now, null);
            }
            case "ARRIVED" -> {
                completeNode(flightId, "LANDING", now, null);
                completeNode(flightId, "DOOR_OPEN", now, null);
                completeNode(flightId, "DEBOARDING", now, null);
            }
            case "COMPLETED" -> {
                completeNode(flightId, "BAGGAGE_CLAIM", now, null);
            }
            case "CANCELLED" -> {
                List<GroundHandling> pending = groundHandlingMapper.selectList(
                        new LambdaQueryWrapper<GroundHandling>()
                                .eq(GroundHandling::getFlightId, flightId)
                                .eq(GroundHandling::getStatus, "PENDING"));
                for (GroundHandling g : pending) {
                    g.setStatus("FAILED");
                    g.setRemark("航班取消");
                    groundHandlingMapper.updateById(g);
                }
            }
        }
    }

    @Override
    @Transactional
    public void departFlight(Long flightId, Long operatorId) {
        Flight flight = flightMapper.selectById(flightId);
        if (flight == null) return;
        flight.setActualDepartureTime(LocalDateTime.now());
        flightStateMachine.transition(flight, FlightStatus.DEPARTED, "地面保障确认起飞");
        autoCompleteByFlightStatus(flightId, "DEPARTED");
        log.info("航班起飞 flightId={}", flightId);
    }

    @Override
    @Transactional
    public void arriveFlight(Long flightId, Long operatorId) {
        Flight flight = flightMapper.selectById(flightId);
        if (flight == null) return;
        flight.setActualArrivalTime(LocalDateTime.now());
        flightStateMachine.transition(flight, FlightStatus.ARRIVED, "地面保障确认到达");
        autoCompleteByFlightStatus(flightId, "ARRIVED");
        log.info("航班到达 flightId={}", flightId);
    }

    @Override
    public List<Map<String, Object>> getFlightGroundSummary(String date) {
        LocalDate queryDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        List<Flight> flights = flightMapper.selectList(
                new LambdaQueryWrapper<Flight>()
                        .eq(Flight::getFlightDate, queryDate)
                        .eq(Flight::getDeleted, 0)
                        .orderByAsc(Flight::getDepartureTime));

        if (flights.isEmpty()) return List.of();

        Set<Long> flightIds = flights.stream().map(Flight::getId).collect(Collectors.toSet());
        List<GroundHandling> allNodes = groundHandlingMapper.selectList(
                new LambdaQueryWrapper<GroundHandling>()
                        .in(GroundHandling::getFlightId, flightIds));
        Map<Long, List<GroundHandling>> nodesByFlight = allNodes.stream()
                .collect(Collectors.groupingBy(GroundHandling::getFlightId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Flight flight : flights) {
            List<GroundHandling> nodes = nodesByFlight.getOrDefault(flight.getId(), List.of());
            long completed = nodes.stream().filter(n -> "COMPLETED".equals(n.getStatus())).count();
            long total = nodes.size();

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("flightId", flight.getId().toString());
            m.put("flightNo", flight.getFlightNo());
            m.put("route", flight.getDepartureAirport() + "-" + flight.getArrivalAirport());
            m.put("date", flight.getFlightDate() != null ? flight.getFlightDate().toString() : null);
            m.put("status", flight.getStatus());
            m.put("departureTime", flight.getDepartureTime() != null ? flight.getDepartureTime().toString() : null);
            m.put("arrivalTime", flight.getArrivalTime() != null ? flight.getArrivalTime().toString() : null);
            m.put("totalNodes", total);
            m.put("completedNodes", completed);
            m.put("progress", total > 0 ? completed * 100 / total : 0);
            // 航班级别保障状态
            String groundStatus;
            if (total == 0) {
                groundStatus = "NOT_CONFIGURED";
            } else if (completed == total) {
                groundStatus = "COMPLETED";
            } else if (completed > 0) {
                groundStatus = "IN_PROGRESS";
            } else {
                groundStatus = "PENDING";
            }
            m.put("groundStatus", groundStatus);
            result.add(m);
        }
        return result;
    }

    private LocalDateTime calcPlanTime(Flight flight, String nodeCode) {
        if (flight == null || flight.getDepartureTime() == null) return null;
        return switch (nodeCode) {
            case "BOARDING" -> flight.getDepartureTime().minusMinutes(30);
            case "DOOR_CLOSE" -> flight.getDepartureTime().minusMinutes(10);
            case "PUSHBACK" -> flight.getDepartureTime().minusMinutes(5);
            case "TAKE_OFF" -> flight.getDepartureTime();
            case "LANDING" -> flight.getArrivalTime();
            case "DOOR_OPEN" -> flight.getArrivalTime() != null ? flight.getArrivalTime().plusMinutes(5) : null;
            default -> null;
        };
    }
}
