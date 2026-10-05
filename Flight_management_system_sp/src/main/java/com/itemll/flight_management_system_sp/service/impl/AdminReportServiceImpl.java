package com.itemll.flight_management_system_sp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.entity.Airline;
import com.itemll.flight_management_system_sp.entity.CustomReport;
import com.itemll.flight_management_system_sp.entity.Flight;
import com.itemll.flight_management_system_sp.entity.Order;
import com.itemll.flight_management_system_sp.entity.OrderPassenger;
import com.itemll.flight_management_system_sp.mapper.AirlineMapper;
import com.itemll.flight_management_system_sp.mapper.CustomReportMapper;
import com.itemll.flight_management_system_sp.mapper.FlightMapper;
import com.itemll.flight_management_system_sp.mapper.OrderMapper;
import com.itemll.flight_management_system_sp.mapper.OrderPassengerMapper;
import com.itemll.flight_management_system_sp.mapper.IrregularOperationMapper;
import com.itemll.flight_management_system_sp.entity.IrregularOperation;
import com.itemll.flight_management_system_sp.service.AdminReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReportServiceImpl implements AdminReportService {

    private final FlightMapper flightMapper;
    private final OrderMapper orderMapper;
    private final OrderPassengerMapper orderPassengerMapper;
    private final IrregularOperationMapper irregularOperationMapper;
    private final CustomReportMapper customReportMapper;
    private final AirlineMapper airlineMapper;

    /** 计入报表的订单状态：只统计真正成交的单（待支付/已取消/已退款不计入） */
    private static final List<String> PAID_STATUSES = List.of("PAID", "ISSUED", "CHECKED_IN", "COMPLETED");

    @Override
    public Map<String, Object> getOperationReport(String type, String startDate, String endDate, String format) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        List<Map<String, Object>> data;

        switch (type) {
            case "DELAY" -> data = getDelayReport(start, end);
            case "PASSENGER" -> data = getPassengerReport(start, end);
            case "CREW" -> data = getCrewReport(start, end);
            case "AIRPORT" -> data = getAirportReport(start, end);
            default -> data = getDailyOperationReport(start, end);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("period", startDate + " ~ " + endDate);
        result.put("format", format != null ? format : "JSON");
        result.put("data", data);
        return result;
    }

    @Override
    public Map<String, Object> getRevenueReport(String type, String startDate, String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        List<Map<String, Object>> data;

        switch (type) {
            case "ROUTE" -> data = getRevenueByRoute(start, end);
            case "CABIN" -> data = getRevenueByCabin(start, end);
            case "AUXILIARY" -> data = getAuxiliaryRevenue(start, end);
            default -> data = getDailyRevenueReport(start, end);
        }

        BigDecimal totalRevenue = data.stream()
                .map(m -> new BigDecimal(m.getOrDefault("revenue", "0").toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("period", startDate + " ~ " + endDate);
        result.put("totalRevenue", totalRevenue);
        result.put("data", data);
        return result;
    }

    private static final Map<String, String> DIMENSION_LABELS = new LinkedHashMap<>() {{
        put("DATE", "按日期");
        put("ROUTE", "按航线");
        put("AIRLINE", "按航司");
        put("CABIN", "按舱位");
    }};

    @Override
    public List<Map<String, Object>> getCustomReports() {
        List<CustomReport> reports = customReportMapper.selectList(
                new LambdaQueryWrapper<CustomReport>().orderByDesc(CustomReport::getCreateTime));
        return reports.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("name", r.getName());
            m.put("dimension", r.getDimension());
            m.put("dimensionLabel", DIMENSION_LABELS.getOrDefault(r.getDimension(), r.getDimension()));
            m.put("startDate", r.getStartDate() != null ? r.getStartDate().toString() : null);
            m.put("endDate", r.getEndDate() != null ? r.getEndDate().toString() : null);
            m.put("creator", r.getCreator());
            m.put("lastGeneratedAt", r.getLastGeneratedAt());
            m.put("rowCount", r.getRowCount() != null ? r.getRowCount() : 0);
            m.put("rows", parseRows(r.getResultJson()));
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public void createCustomReport(Map<String, Object> report) {
        String name = report.get("name") != null ? String.valueOf(report.get("name")).trim() : "";
        if (name.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报表名称不能为空");
        }
        if (report.get("startDate") == null || report.get("endDate") == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择统计的时间范围");
        }
        LocalDate start = LocalDate.parse(String.valueOf(report.get("startDate")));
        LocalDate end = LocalDate.parse(String.valueOf(report.get("endDate")));
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }

        CustomReport r = new CustomReport();
        r.setName(name);
        r.setDimension(normalizeDimension(report.get("dimension")));
        r.setStartDate(start);
        r.setEndDate(end);
        r.setCreator(report.get("creator") != null ? String.valueOf(report.get("creator")) : null);
        r.setRowCount(0);
        customReportMapper.insert(r);

        // 建完立刻算一次：否则列表里每条都显示「从未生成」，用户还得逐个点「生成」
        generateReport(r.getId());
    }

    @Override
    public void generateReport(Long reportId) {
        CustomReport r = customReportMapper.selectById(reportId);
        if (r == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报表不存在");
        }
        List<Map<String, Object>> rows = buildReportRows(r.getDimension(), r.getStartDate(), r.getEndDate());
        r.setResultJson(JSONUtil.toJsonStr(rows));
        r.setRowCount(rows.size());
        r.setLastGeneratedAt(LocalDateTime.now());
        customReportMapper.updateById(r);
    }

    private String normalizeDimension(Object raw) {
        String d = raw == null ? "DATE" : String.valueOf(raw).trim().toUpperCase();
        return DIMENSION_LABELS.containsKey(d) ? d : "DATE";
    }

    /** 反序列化上次生成结果；内容损坏时退化空列表，不让整个列表接口报错 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseRows(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object o : JSONUtil.parseArray(json)) {
                if (o instanceof Map) {
                    rows.add((Map<String, Object>) o);
                }
            }
            return rows;
        } catch (Exception e) {
            log.warn("自定义报表结果解析失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 按维度聚合出报表行。
     * <p>统计口径：创建时间落在 [start, end] 内、且状态为成交态的订单
     * （PAID / ISSUED / CHECKED_IN / COMPLETED；待支付、已取消、已退款都不计入）。
     */
    private List<Map<String, Object>> buildReportRows(String dimension, LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return Collections.emptyList();
        }
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .ge(Order::getCreateTime, start.atStartOfDay())
                .lt(Order::getCreateTime, end.plusDays(1).atStartOfDay())
                .in(Order::getStatus, PAID_STATUSES));
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }

        // 航线/航司维度需回查航班：一次性批量取，避免在循环里逐条查库
        Set<Long> flightIds = orders.stream().map(Order::getFlightId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Flight> flightById = flightIds.isEmpty() ? Collections.emptyMap()
                : flightMapper.selectBatchIds(flightIds).stream()
                    .collect(Collectors.toMap(Flight::getId, f -> f, (a, b) -> a));
        Set<Long> airlineIds = flightById.values().stream().map(Flight::getAirlineId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> airlineNameById = airlineIds.isEmpty() ? Collections.emptyMap()
                : airlineMapper.selectBatchIds(airlineIds).stream()
                    .collect(Collectors.toMap(Airline::getId,
                            a -> a.getName() != null ? a.getName() : a.getCode(), (a, b) -> a));

        // 旅客数按订单汇总一次
        List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
        Map<Long, Integer> paxByOrder = new HashMap<>();
        for (OrderPassenger p : orderPassengerMapper.selectList(
                new LambdaQueryWrapper<OrderPassenger>().in(OrderPassenger::getOrderId, orderIds))) {
            paxByOrder.merge(p.getOrderId(), 1, Integer::sum);
        }

        Map<String, List<Order>> grouped = new LinkedHashMap<>();
        for (Order o : orders) {
            grouped.computeIfAbsent(dimensionKey(dimension, o, flightById, airlineNameById),
                    k -> new ArrayList<>()).add(o);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, List<Order>> e : grouped.entrySet()) {
            List<Order> group = e.getValue();
            BigDecimal revenue = group.stream()
                    .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int pax = group.stream().mapToInt(o -> paxByOrder.getOrDefault(o.getId(), 0)).sum();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("group", e.getKey());
            row.put("orders", group.size());
            row.put("passengers", pax);
            row.put("revenue", revenue);
            rows.add(row);
        }
        rows.sort((a, b) -> ((BigDecimal) b.get("revenue")).compareTo((BigDecimal) a.get("revenue")));
        return rows;
    }

    private String dimensionKey(String dimension, Order o,
                                Map<Long, Flight> flightById, Map<Long, String> airlineNameById) {
        Flight f = o.getFlightId() != null ? flightById.get(o.getFlightId()) : null;
        String dim = dimension == null ? "DATE" : dimension;
        return switch (dim) {
            case "ROUTE" -> f == null ? "未知航线"
                    : f.getDepartureAirport() + "-" + f.getArrivalAirport();
            case "AIRLINE" -> {
                if (f == null || f.getAirlineId() == null) {
                    yield "未知航司";
                }
                String name = airlineNameById.get(f.getAirlineId());
                yield name != null ? name : "航司 #" + f.getAirlineId();
            }
            case "CABIN" -> o.getCabinClass() != null ? o.getCabinClass() : "未知舱位";
            default -> o.getCreateTime() != null ? o.getCreateTime().toLocalDate().toString() : "未知日期";
        };
    }

    // ============ 运营报表 ============

    private List<Map<String, Object>> getDailyOperationReport(LocalDate start, LocalDate end) {
        List<Flight> flights = flightMapper.selectList(
                new LambdaQueryWrapper<Flight>()
                        .between(Flight::getFlightDate, start, end)
                        .eq(Flight::getDeleted, 0)
                        .orderByAsc(Flight::getFlightDate));

        Map<LocalDate, List<Flight>> byDate = flights.stream()
                .collect(Collectors.groupingBy(Flight::getFlightDate, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Flight>> entry : byDate.entrySet()) {
            List<Flight> dayFlights = entry.getValue();
            long total = dayFlights.size();
            long delayed = dayFlights.stream().filter(f -> "DELAYED".equals(f.getStatus())).count();
            long cancelled = dayFlights.stream().filter(f -> "CANCELLED".equals(f.getStatus())).count();
            long onTime = total - delayed - cancelled;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", entry.getKey().toString());
            m.put("flights", total);
            m.put("onTime", onTime);
            m.put("delayed", delayed);
            m.put("cancelled", cancelled);
            m.put("onTimeRate", total > 0 ? Math.round(onTime * 1000.0 / total) / 10.0 : 0);
            result.add(m);
        }
        return result;
    }

    private List<Map<String, Object>> getDelayReport(LocalDate start, LocalDate end) {
        List<IrregularOperation> ops = irregularOperationMapper.selectList(
                new LambdaQueryWrapper<IrregularOperation>()
                        .between(IrregularOperation::getCreateTime, start.atStartOfDay(), end.plusDays(1).atStartOfDay())
                        .orderByDesc(IrregularOperation::getDelayMinutes));

        Map<String, List<IrregularOperation>> byReason = ops.stream()
                .filter(o -> o.getReason() != null)
                .collect(Collectors.groupingBy(IrregularOperation::getReason, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<IrregularOperation>> entry : byReason.entrySet()) {
            double avgDelay = entry.getValue().stream()
                    .filter(o -> o.getDelayMinutes() != null)
                    .mapToInt(IrregularOperation::getDelayMinutes)
                    .average().orElse(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reason", entry.getKey());
            m.put("count", entry.getValue().size());
            m.put("avgDelayMinutes", Math.round(avgDelay));
            result.add(m);
        }
        if (result.isEmpty()) {
            result.add(Map.of("reason", "无延误记录", "count", 0, "avgDelayMinutes", 0));
        }
        return result;
    }

    private List<Map<String, Object>> getPassengerReport(LocalDate start, LocalDate end) {
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .between(Order::getCreateTime, start.atStartOfDay(), end.plusDays(1).atStartOfDay())
                        .in(Order::getStatus, "PAID", "ISSUED", "CHECKED_IN", "BOARDING", "DEPARTED", "ARRIVED", "COMPLETED"));

        Map<Long, List<Order>> byFlight = orders.stream()
                .filter(o -> o.getFlightId() != null)
                .collect(Collectors.groupingBy(Order::getFlightId, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<Order>> entry : byFlight.entrySet()) {
            Flight flight = flightMapper.selectById(entry.getKey());
            if (flight == null) continue;
            long passengerCount = entry.getValue().stream()
                    .mapToLong(o -> orderPassengerMapper.selectCount(
                            new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, o.getId())))
                    .sum();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", flight.getFlightDate() != null ? flight.getFlightDate().toString() : "");
            m.put("flightNo", flight.getFlightNo());
            m.put("route", flight.getDepartureAirport() + "-" + flight.getArrivalAirport());
            m.put("passengers", passengerCount);
            result.add(m);
        }
        if (result.isEmpty()) {
            result.add(Map.of("date", "", "flightNo", "无数据", "route", "", "passengers", 0));
        }
        return result;
    }

    private List<Map<String, Object>> getCrewReport(LocalDate start, LocalDate end) {
        List<Map<String, Object>> result = new ArrayList<>();
        long flightCount = flightMapper.selectCount(
                new LambdaQueryWrapper<Flight>()
                        .between(Flight::getFlightDate, start, end)
                        .eq(Flight::getDeleted, 0));
        result.add(Map.of("metric", "航班总数", "value", flightCount));
        return result;
    }

    private List<Map<String, Object>> getAirportReport(LocalDate start, LocalDate end) {
        List<Flight> flights = flightMapper.selectList(
                new LambdaQueryWrapper<Flight>()
                        .between(Flight::getFlightDate, start, end)
                        .eq(Flight::getDeleted, 0));

        Map<String, Long> depCount = flights.stream()
                .collect(Collectors.groupingBy(Flight::getDepartureAirport, LinkedHashMap::new, Collectors.counting()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : depCount.entrySet()) {
            result.add(Map.of("airport", entry.getKey(), "departures", entry.getValue()));
        }
        if (result.isEmpty()) {
            result.add(Map.of("airport", "无数据", "departures", 0));
        }
        return result;
    }

    // ============ 收入报表 ============

    private List<Map<String, Object>> getDailyRevenueReport(LocalDate start, LocalDate end) {
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .between(Order::getCreateTime, start.atStartOfDay(), end.plusDays(1).atStartOfDay())
                        .in(Order::getStatus, "PAID", "ISSUED", "CHECKED_IN", "BOARDING", "DEPARTED", "ARRIVED", "COMPLETED"));

        Map<LocalDate, List<Order>> byDate = orders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getCreateTime() != null ? o.getCreateTime().toLocalDate() : start,
                        LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Order>> entry : byDate.entrySet()) {
            BigDecimal revenue = entry.getValue().stream()
                    .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", entry.getKey().toString());
            m.put("revenue", revenue);
            m.put("tickets", entry.getValue().size());
            result.add(m);
        }
        if (result.isEmpty()) {
            result.add(Map.of("date", start.toString(), "revenue", 0, "tickets", 0));
        }
        return result;
    }

    private List<Map<String, Object>> getRevenueByRoute(LocalDate start, LocalDate end) {
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .between(Order::getCreateTime, start.atStartOfDay(), end.plusDays(1).atStartOfDay())
                        .in(Order::getStatus, "PAID", "ISSUED", "CHECKED_IN", "BOARDING", "DEPARTED", "ARRIVED", "COMPLETED"));

        Map<String, List<Order>> byFlight = orders.stream()
                .collect(Collectors.groupingBy(Order::getFlightNo, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Order>> entry : byFlight.entrySet()) {
            Flight flight = flightMapper.selectOne(
                    new LambdaQueryWrapper<Flight>()
                            .eq(Flight::getFlightNo, entry.getKey())
                            .last("LIMIT 1"));
            String route = flight != null ? flight.getDepartureAirport() + "-" + flight.getArrivalAirport() : entry.getKey();
            BigDecimal revenue = entry.getValue().stream()
                    .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("route", route);
            m.put("flightNo", entry.getKey());
            m.put("revenue", revenue);
            m.put("tickets", entry.getValue().size());
            result.add(m);
        }
        if (result.isEmpty()) {
            result.add(Map.of("route", "无数据", "flightNo", "", "revenue", 0, "tickets", 0));
        }
        return result;
    }

    private List<Map<String, Object>> getRevenueByCabin(LocalDate start, LocalDate end) {
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .between(Order::getCreateTime, start.atStartOfDay(), end.plusDays(1).atStartOfDay())
                        .in(Order::getStatus, "PAID", "ISSUED", "CHECKED_IN", "BOARDING", "DEPARTED", "ARRIVED", "COMPLETED"));

        Map<String, List<Order>> byCabin = orders.stream()
                .collect(Collectors.groupingBy(Order::getCabinClass, LinkedHashMap::new, Collectors.toList()));

        Map<String, String> cabinNames = Map.of("ECONOMY", "经济舱", "BUSINESS", "商务舱", "FIRST", "头等舱");

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Order>> entry : byCabin.entrySet()) {
            BigDecimal revenue = entry.getValue().stream()
                    .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cabin", cabinNames.getOrDefault(entry.getKey(), entry.getKey()));
            m.put("cabinClass", entry.getKey());
            m.put("revenue", revenue);
            m.put("tickets", entry.getValue().size());
            result.add(m);
        }
        if (result.isEmpty()) {
            result.add(Map.of("cabin", "无数据", "cabinClass", "", "revenue", 0, "tickets", 0));
        }
        return result;
    }

    private List<Map<String, Object>> getAuxiliaryRevenue(LocalDate start, LocalDate end) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(Map.of("type", "选座", "revenue", 0, "count", 0));
        result.add(Map.of("type", "行李", "revenue", 0, "count", 0));
        result.add(Map.of("type", "餐食", "revenue", 0, "count", 0));
        return result;
    }
}
