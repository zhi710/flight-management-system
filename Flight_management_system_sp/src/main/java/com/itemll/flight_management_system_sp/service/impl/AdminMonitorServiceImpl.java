package com.itemll.flight_management_system_sp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itemll.flight_management_system_sp.common.enums.FlightStatus;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.entity.*;
import com.itemll.flight_management_system_sp.mapper.*;
import com.itemll.flight_management_system_sp.service.AdminMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端监控服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMonitorServiceImpl implements AdminMonitorService {

    private final FlightMapper flightMapper;
    private final OrderMapper orderMapper;
    private final OrderPassengerMapper orderPassengerMapper;
    private final SpecialServiceRequestMapper specialServiceRequestMapper;
    private final AlertMapper alertMapper;
    private final IrregularOperationMapper irregularOperationMapper;
    private final FlightStatusLogMapper flightStatusLogMapper;

    @Override
    public Map<String, Object> getDashboard() {
        LocalDate today = LocalDate.now();
        LocalDate nextWeek = today.plusDays(7);
        // 统计今天到未来7天的航班（覆盖近期运力全景）
        List<Flight> allFlights = flightMapper.selectList(
                new LambdaQueryWrapper<Flight>()
                        .between(Flight::getFlightDate, today, nextWeek)
                        .eq(Flight::getDeleted, 0));

        // ── 统计卡片 ──
        long scheduledCount = allFlights.stream().filter(f -> "SCHEDULED".equals(f.getStatus())).count();
        long delayedCount = allFlights.stream().filter(f -> "DELAYED".equals(f.getStatus())).count();
        long cancelledCount = allFlights.stream().filter(f -> "CANCELLED".equals(f.getStatus())).count();
        long departedCount = allFlights.stream().filter(f -> "DEPARTED".equals(f.getStatus())).count();
        long arrivedCount = allFlights.stream().filter(f -> "ARRIVED".equals(f.getStatus())).count();
        long boardingCount = allFlights.stream().filter(f -> "BOARDING".equals(f.getStatus())).count();
        long completedCount = allFlights.stream().filter(f -> "COMPLETED".equals(f.getStatus())).count();

        // 旅客数：这些航班上的所有订单旅客数
        long passengerCount = 0;
        List<Long> flightIds = allFlights.stream().map(Flight::getId).collect(Collectors.toList());
        if (!flightIds.isEmpty()) {
            List<Order> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<Order>()
                            .in(Order::getFlightId, flightIds)
                            .eq(Order::getDeleted, 0));
            List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
            if (!orderIds.isEmpty()) {
                passengerCount = orderPassengerMapper.selectCount(
                        new LambdaQueryWrapper<OrderPassenger>().in(OrderPassenger::getOrderId, orderIds));
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("planned", allFlights.size());
        summary.put("executed", departedCount + arrivedCount + completedCount);
        summary.put("delayed", delayedCount);
        summary.put("cancelled", cancelledCount);
        summary.put("passengers", passengerCount);

        // ── 航班状态分布（所有非零状态）──
        Map<String, Object> statusDist = new LinkedHashMap<>();
        putIfPositive(statusDist, "SCHEDULED", scheduledCount);
        putIfPositive(statusDist, "BOARDING", boardingCount);
        putIfPositive(statusDist, "DEPARTED", departedCount);
        putIfPositive(statusDist, "ARRIVED", arrivedCount);
        putIfPositive(statusDist, "COMPLETED", completedCount);
        putIfPositive(statusDist, "DELAYED", delayedCount);
        putIfPositive(statusDist, "CANCELLED", cancelledCount);

        // ── 延误原因分析（从 irregular_operation 取今日 + 最近7天）──
        Map<String, Long> delayAnalysis = new LinkedHashMap<>();
        List<IrregularOperation> irops = irregularOperationMapper.selectList(
                new LambdaQueryWrapper<IrregularOperation>()
                        .eq(IrregularOperation::getType, "DELAY")
                        .ge(IrregularOperation::getCreateTime, today.atStartOfDay().minusDays(7)));
        Map<String, Long> reasonCount = irops.stream()
                .collect(Collectors.groupingBy(
                        op -> op.getReason() != null ? op.getReason() : "未知",
                        LinkedHashMap::new,
                        Collectors.counting()));
        delayAnalysis.putAll(reasonCount);

        // ── 实时航班动态（今日+未来7天 非终止状态的航班，按起飞时间）──
        List<Map<String, Object>> realtimeList = allFlights.stream()
                .filter(f -> List.of("SCHEDULED", "BOARDING", "DEPARTED", "DELAYED", "FLYING", "ARRIVED")
                        .contains(f.getStatus()))
                .sorted(Comparator.comparing(Flight::getDepartureTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("flightId", f.getId().toString());
                    m.put("flightNo", f.getFlightNo());
                    m.put("status", f.getStatus());
                    m.put("route", f.getDepartureAirport() + "→" + f.getArrivalAirport());
                    m.put("departureTime", f.getDepartureTime() != null ? f.getDepartureTime().toString() : null);
                    return m;
                }).collect(Collectors.toList());

        // ── 最近事件（最近 20 条航班状态变更日志，含航班号）──
        List<FlightStatusLog> logs = flightStatusLogMapper.selectList(
                new LambdaQueryWrapper<FlightStatusLog>()
                        .orderByDesc(FlightStatusLog::getCreateTime)
                        .last("LIMIT 20"));
        // 批量查航班号（含已删除航班：日志是历史记录，航班删了也要能显示航班号）
        Set<Long> logFlightIds = logs.stream()
                .map(FlightStatusLog::getFlightId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> flightNoMap = new HashMap<>();
        if (!logFlightIds.isEmpty()) {
            flightMapper.selectFlightNosByIds(logFlightIds).forEach(m ->
                    flightNoMap.put(((Number) m.get("flightId")).longValue(), (String) m.get("flightNo")));
        }
        List<Map<String, Object>> events = logs.stream()
                // 航班已彻底不存在（日志残留）→ 跳过，避免出现「?」
                .filter(log -> flightNoMap.containsKey(log.getFlightId()))
                .map(log -> {
                    String reason = log.getReason();
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("time", log.getCreateTime());
                    e.put("event", flightNoMap.get(log.getFlightId()) + " " + fmtStatus(log.getNewStatus()) +
                            (reason != null && !reason.isBlank() ? " — " + reason : ""));
                    return e;
                }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("statusDistribution", statusDist);
        result.put("delayAnalysis", delayAnalysis);
        result.put("realtimeFlights", realtimeList);
        result.put("recentEvents", events);
        return result;
    }

    private void putIfPositive(Map<String, Object> map, String key, long count) {
        if (count > 0) map.put(key, count);
    }

    private String fmtStatus(String code) {
        return FlightStatus.labelOf(code);
    }

    @Override
    public PageResult<Map<String, Object>> getAlerts(String level, String type, String status, int page, int pageSize) {
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<Alert>()
                .eq(level != null && !level.isEmpty(), Alert::getLevel, level)
                .eq(type != null && !type.isEmpty(), Alert::getType, type)
                .eq(status != null && !status.isEmpty(), Alert::getStatus, status)
                .orderByDesc(Alert::getCreateTime);

        Page<Alert> pageObj = new Page<>(page, pageSize);
        Page<Alert> result = alertMapper.selectPage(pageObj, wrapper);

        List<Map<String, Object>> list = result.getRecords().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("alertId", String.valueOf(a.getId()));
            m.put("level", a.getLevel());
            m.put("type", a.getType());
            m.put("title", a.getTitle());
            m.put("description", a.getContent());
            m.put("status", a.getStatus());
            m.put("createdAt", a.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        return PageResult.of(list, page, pageSize, result.getTotal());
    }

    @Override
    public Map<String, Object> getAlertStats() {
        long pending = alertMapper.selectCount(new LambdaQueryWrapper<Alert>().eq(Alert::getStatus, "PENDING"));
        long resolved = alertMapper.selectCount(new LambdaQueryWrapper<Alert>().eq(Alert::getStatus, "RESOLVED"));
        long urgentPending = alertMapper.selectCount(
                new LambdaQueryWrapper<Alert>()
                        .eq(Alert::getLevel, "URGENT")
                        .eq(Alert::getStatus, "PENDING"));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("urgentCount", urgentPending);
        stats.put("pendingCount", pending);
        stats.put("resolvedCount", resolved);
        stats.put("totalAlerts", pending + resolved);
        return stats;
    }

    @Override
    public void createAlert(String level, String type, String title, String content) {
        Alert alert = new Alert();
        alert.setLevel(level);
        alert.setType(type);
        alert.setTitle(title);
        alert.setContent(content);
        alert.setStatus("PENDING");
        alertMapper.insert(alert);
        log.info("告警已生成: level={}, type={}, title={}", level, type, title);
    }

    @Override
    public void resolveAlert(Long alertId, Long adminId) {
        Alert alert = alertMapper.selectById(alertId);
        if (alert != null) {
            alert.setStatus("RESOLVED");
            alert.setResolverId(adminId);
            alertMapper.updateById(alert);
        }
    }

    @Override
    public Map<String, Object> getStatistics(String dimension, String startDate, String endDate, String groupBy) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        switch (dimension) {
            case "FLIGHT":
                return getFlightStatistics(start, end, groupBy);
            case "PASSENGER":
                return getPassengerStatistics(start, end, groupBy);
            case "REVENUE":
                return getRevenueStatistics(start, end, groupBy);
            case "SERVICE":
                return getServiceStatistics(start, end, groupBy);
            default:
                Map<String, Object> empty = new HashMap<>();
                empty.put("labels", Collections.emptyList());
                empty.put("datasets", Collections.emptyList());
                empty.put("columns", Collections.emptyList());
                empty.put("tableData", Collections.emptyList());
                return empty;
        }
    }

    private Map<String, Object> getFlightStatistics(LocalDate start, LocalDate end, String groupBy) {
        List<Flight> flights = flightMapper.selectList(
                new LambdaQueryWrapper<Flight>()
                        .between(Flight::getFlightDate, start, end)
                        .eq(Flight::getDeleted, 0)
                        .orderByAsc(Flight::getFlightDate));

        Map<String, List<Flight>> grouped = groupFlightsByPeriod(flights, groupBy);
        List<String> labels = new ArrayList<>(grouped.keySet()).stream().sorted().collect(Collectors.toList());

        // 按航班全生命周期状态统计，避免"已完成(COMPLETED)"等终态航班永远进不了图表
        List<Long> scheduledData = new ArrayList<>();
        List<Long> inflightData = new ArrayList<>();
        List<Long> arrivedData = new ArrayList<>();
        List<Long> delayedData = new ArrayList<>();
        List<Long> cancelledData = new ArrayList<>();
        List<Map<String, Object>> tableData = new ArrayList<>();

        for (String label : labels) {
            List<Flight> group = grouped.get(label);
            long scheduled = group.stream().filter(f -> "SCHEDULED".equals(f.getStatus())).count();
            long inflight = group.stream().filter(f -> List.of("BOARDING", "DEPARTED", "FLYING").contains(f.getStatus())).count();
            long arrived = group.stream().filter(f -> List.of("ARRIVED", "COMPLETED").contains(f.getStatus())).count();
            long delayed = group.stream().filter(f -> "DELAYED".equals(f.getStatus())).count();
            long cancelled = group.stream().filter(f -> List.of("CANCELLED", "DIVERTED", "RETURNED").contains(f.getStatus())).count();

            scheduledData.add(scheduled);
            inflightData.add(inflight);
            arrivedData.add(arrived);
            delayedData.add(delayed);
            cancelledData.add(cancelled);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("period", label);
            row.put("total", group.size());
            row.put("scheduled", scheduled);
            row.put("inflight", inflight);
            row.put("arrived", arrived);
            row.put("delayed", delayed);
            row.put("cancelled", cancelled);
            tableData.add(row);
        }

        List<Map<String, Object>> datasets = new ArrayList<>();
        datasets.add(Map.of("label", "计划", "data", scheduledData));
        datasets.add(Map.of("label", "执行中", "data", inflightData));
        datasets.add(Map.of("label", "到达(含完成)", "data", arrivedData));
        datasets.add(Map.of("label", "延误", "data", delayedData));
        datasets.add(Map.of("label", "取消", "data", cancelledData));

        List<Map<String, Object>> columns = List.of(
                Map.of("prop", "period", "label", "日期", "width", 120),
                Map.of("prop", "total", "label", "总计"),
                Map.of("prop", "scheduled", "label", "计划"),
                Map.of("prop", "inflight", "label", "执行中"),
                Map.of("prop", "arrived", "label", "到达(含完成)"),
                Map.of("prop", "delayed", "label", "延误"),
                Map.of("prop", "cancelled", "label", "取消"));

        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("datasets", datasets);
        result.put("columns", columns);
        result.put("tableData", tableData);
        return result;
    }

    private Map<String, Object> getPassengerStatistics(LocalDate start, LocalDate end, String groupBy) {
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .between(Order::getCreateTime, start.atStartOfDay(), end.plusDays(1).atStartOfDay())
                        .eq(Order::getDeleted, 0)
                        .orderByAsc(Order::getCreateTime));

        Map<String, List<Order>> grouped = groupOrdersByPeriod(orders, groupBy);
        List<String> labels = new ArrayList<>(grouped.keySet()).stream().sorted().collect(Collectors.toList());

        List<Long> bookingData = new ArrayList<>();
        List<Long> passengerData = new ArrayList<>();
        List<Long> checkedInData = new ArrayList<>();
        List<Map<String, Object>> tableData = new ArrayList<>();

        for (String label : labels) {
            List<Order> group = grouped.get(label);
            long bookings = group.size();

            List<Long> orderIds = group.stream().map(Order::getId).collect(Collectors.toList());
            long passengers = 0;
            long checkedIn = 0;
            if (!orderIds.isEmpty()) {
                passengers = orderPassengerMapper.selectCount(
                        new LambdaQueryWrapper<OrderPassenger>().in(OrderPassenger::getOrderId, orderIds));
                checkedIn = orderPassengerMapper.selectCount(
                        new LambdaQueryWrapper<OrderPassenger>()
                                .in(OrderPassenger::getOrderId, orderIds)
                                .eq(OrderPassenger::getCheckinStatus, "CHECKED_IN"));
            }

            bookingData.add(bookings);
            passengerData.add(passengers);
            checkedInData.add(checkedIn);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("period", label);
            row.put("bookings", bookings);
            row.put("passengers", passengers);
            row.put("checkedIn", checkedIn);
            tableData.add(row);
        }

        List<Map<String, Object>> datasets = new ArrayList<>();
        datasets.add(Map.of("label", "订单数", "data", bookingData));
        datasets.add(Map.of("label", "旅客数", "data", passengerData));
        datasets.add(Map.of("label", "已值机", "data", checkedInData));

        List<Map<String, Object>> columns = List.of(
                Map.of("prop", "period", "label", "日期", "width", 120),
                Map.of("prop", "bookings", "label", "订单数"),
                Map.of("prop", "passengers", "label", "旅客数"),
                Map.of("prop", "checkedIn", "label", "已值机"));

        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("datasets", datasets);
        result.put("columns", columns);
        result.put("tableData", tableData);
        return result;
    }

    private Map<String, Object> getRevenueStatistics(LocalDate start, LocalDate end, String groupBy) {
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .between(Order::getPaidTime, start.atStartOfDay(), end.plusDays(1).atStartOfDay())
                        .eq(Order::getDeleted, 0)
                        .eq(Order::getStatus, "PAID")
                        .orderByAsc(Order::getPaidTime));

        Map<String, List<Order>> grouped = groupOrdersByPaidPeriod(orders, groupBy);
        List<String> labels = new ArrayList<>(grouped.keySet()).stream().sorted().collect(Collectors.toList());

        List<BigDecimal> revenueData = new ArrayList<>();
        List<Long> orderCountData = new ArrayList<>();
        List<Map<String, Object>> tableData = new ArrayList<>();

        for (String label : labels) {
            List<Order> group = grouped.get(label);
            BigDecimal total = group.stream()
                    .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            revenueData.add(total);
            orderCountData.add((long) group.size());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("period", label);
            row.put("revenue", total);
            row.put("orderCount", group.size());
            tableData.add(row);
        }

        List<Map<String, Object>> datasets = new ArrayList<>();
        datasets.add(Map.of("label", "收入", "data", revenueData));

        List<Map<String, Object>> columns = List.of(
                Map.of("prop", "period", "label", "日期", "width", 120),
                Map.of("prop", "revenue", "label", "收入"),
                Map.of("prop", "orderCount", "label", "订单数"));

        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("datasets", datasets);
        result.put("columns", columns);
        result.put("tableData", tableData);
        return result;
    }

    private Map<String, Object> getServiceStatistics(LocalDate start, LocalDate end, String groupBy) {
        List<SpecialServiceRequest> ssrs = specialServiceRequestMapper.selectList(
                new LambdaQueryWrapper<SpecialServiceRequest>()
                        .between(SpecialServiceRequest::getCreateTime, start.atStartOfDay(), end.plusDays(1).atStartOfDay())
                        .orderByAsc(SpecialServiceRequest::getCreateTime));

        Map<String, List<SpecialServiceRequest>> grouped = groupSsrByPeriod(ssrs, groupBy);
        List<String> labels = new ArrayList<>(grouped.keySet()).stream().sorted().collect(Collectors.toList());

        List<Long> totalData = new ArrayList<>();
        Map<String, List<Long>> codeDataMap = new LinkedHashMap<>();
        List<Map<String, Object>> tableData = new ArrayList<>();

        // Collect distinct SSR codes
        Set<String> allCodes = ssrs.stream()
                .map(SpecialServiceRequest::getSsrCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (String code : allCodes) {
            codeDataMap.put(code, new ArrayList<>());
        }

        for (String label : labels) {
            List<SpecialServiceRequest> group = grouped.get(label);
            totalData.add((long) group.size());

            for (String code : allCodes) {
                long count = group.stream()
                        .filter(s -> code.equals(s.getSsrCode()))
                        .count();
                codeDataMap.get(code).add(count);
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("period", label);
            row.put("total", group.size());
            for (String code : allCodes) {
                row.put(code, group.stream().filter(s -> code.equals(s.getSsrCode())).count());
            }
            tableData.add(row);
        }

        List<Map<String, Object>> datasets = new ArrayList<>();
        datasets.add(Map.of("label", "总数", "data", totalData));
        for (Map.Entry<String, List<Long>> entry : codeDataMap.entrySet()) {
            datasets.add(Map.of("label", entry.getKey(), "data", entry.getValue()));
        }

        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(Map.of("prop", "period", "label", "日期", "width", 120));
        columns.add(Map.of("prop", "total", "label", "总数"));
        for (String code : allCodes) {
            columns.add(Map.of("prop", code, "label", code));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("datasets", datasets);
        result.put("columns", columns);
        result.put("tableData", tableData);
        return result;
    }

    private Map<String, List<Flight>> groupFlightsByPeriod(List<Flight> flights, String groupBy) {
        Map<String, List<Flight>> map = new LinkedHashMap<>();
        for (Flight f : flights) {
            String key = formatPeriodKey(f.getFlightDate(), groupBy);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
        }
        return map;
    }

    private Map<String, List<Order>> groupOrdersByPeriod(List<Order> orders, String groupBy) {
        Map<String, List<Order>> map = new LinkedHashMap<>();
        for (Order o : orders) {
            LocalDate date = o.getCreateTime() != null ? o.getCreateTime().toLocalDate() : LocalDate.now();
            String key = formatPeriodKey(date, groupBy);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        }
        return map;
    }

    private Map<String, List<Order>> groupOrdersByPaidPeriod(List<Order> orders, String groupBy) {
        Map<String, List<Order>> map = new LinkedHashMap<>();
        for (Order o : orders) {
            LocalDate date = o.getPaidTime() != null ? o.getPaidTime().toLocalDate() : LocalDate.now();
            String key = formatPeriodKey(date, groupBy);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        }
        return map;
    }

    private Map<String, List<SpecialServiceRequest>> groupSsrByPeriod(List<SpecialServiceRequest> ssrs, String groupBy) {
        Map<String, List<SpecialServiceRequest>> map = new LinkedHashMap<>();
        for (SpecialServiceRequest s : ssrs) {
            LocalDate date = s.getCreateTime() != null ? s.getCreateTime().toLocalDate() : LocalDate.now();
            String key = formatPeriodKey(date, groupBy);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }
        return map;
    }

    private String formatPeriodKey(LocalDate date, String groupBy) {
        if ("WEEK".equals(groupBy)) {
            return date.toString().substring(0, 7) + "-W" + (date.getDayOfMonth() / 7 + 1);
        } else if ("MONTH".equals(groupBy)) {
            return date.toString().substring(0, 7);
        }
        return date.toString();
    }
}
