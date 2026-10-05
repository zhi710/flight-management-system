package com.itemll.flight_management_system_sp.controller.admin;

import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.dto.AdminFlightCreateDTO;
import com.itemll.flight_management_system_sp.entity.Airport;
import com.itemll.flight_management_system_sp.entity.Flight;
import com.itemll.flight_management_system_sp.entity.FlightCabin;
import com.itemll.flight_management_system_sp.mapper.AirportMapper;
import com.itemll.flight_management_system_sp.mapper.FlightCabinMapper;
import com.itemll.flight_management_system_sp.mapper.FlightMapper;
import com.itemll.flight_management_system_sp.service.AdminFlightService;
import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端航班调度控制器
 */
@Tag(name = "管理端-航班调度", description = "航班计划、创建、编辑、删除、批量操作、不正常航班处理")
@RestController
@RequestMapping("/admin/flights")
@RequiredArgsConstructor
public class AdminFlightController {

    private final AdminFlightService adminFlightService;
    private final FlightCabinMapper flightCabinMapper;
    private final FlightMapper flightMapper;
    private final AirportMapper airportMapper;

    @Operation(summary = "航班计划列表")
    @OperationLog(module = "航班管理", action = "查询航班列表")
    @GetMapping
    public Result<PageResult<Map<String, Object>>> getFlightList(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String route,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String airline,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(adminFlightService.getFlightList(date, route, status, airline, keyword, page, pageSize));
    }

    @Operation(summary = "创建航班")
    @OperationLog(module = "航班管理", action = "创建航班")
    @PostMapping
    public Result<Map<String, Object>> createFlight(@Valid @RequestBody AdminFlightCreateDTO dto) {
        return Result.ok(adminFlightService.createFlight(dto));
    }

    @Operation(summary = "编辑航班")
    @OperationLog(module = "航班管理", action = "编辑航班")
    @PutMapping("/{flightId}")
    public Result<Void> updateFlight(@PathVariable Long flightId, @RequestBody AdminFlightCreateDTO dto) {
        adminFlightService.updateFlight(flightId, dto);
        return Result.ok();
    }

    @OperationLog(module = "航班管理", action = "删除航班")
    @Operation(summary = "删除航班")
    @DeleteMapping("/{flightId}")
    public Result<Void> deleteFlight(@PathVariable Long flightId) {
        adminFlightService.deleteFlight(flightId);
        return Result.ok();
    }

    @OperationLog(module = "航班管理", action = "批量操作")
    @Operation(summary = "批量操作")
    @PostMapping("/batch")
    public Result<Void> batchOperate(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Long> flightIds = ((List<Number>) params.get("flightIds")).stream().map(Number::longValue).toList();
        String action = (String) params.get("action");
        @SuppressWarnings("unchecked")
        Map<String, Object> opParams = (Map<String, Object>) params.get("params");
        adminFlightService.batchOperate(flightIds, action, opParams);
        return Result.ok();
    }

    @OperationLog(module = "航班管理", action = "查询时刻表", saveParams = false)
    @Operation(summary = "航班时刻表")
    @GetMapping("/schedule")
    public Result<Map<String, Object>> getSchedule(
            @RequestParam String view,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String route) {
        return Result.ok(adminFlightService.getSchedule(view, startDate, endDate, route));
    }

    @OperationLog(module = "航班管理", action = "处理不正常航班")
    @Operation(summary = "不正常航班处理")
    @PostMapping("/{flightId}/irregular")
    public Result<Void> handleIrregular(@PathVariable Long flightId, @RequestBody Map<String, Object> params) {
        String type = (String) params.get("type");
        String reason = (String) params.get("reason");
        @SuppressWarnings("unchecked")
        Map<String, Object> newSchedule = (Map<String, Object>) params.get("newSchedule");
        Boolean notifyPassengers = (Boolean) params.getOrDefault("notifyPassengers", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> arrangements = (Map<String, Object>) params.get("arrangements");
        adminFlightService.handleIrregular(flightId, type, reason, newSchedule, notifyPassengers, arrangements);
        return Result.ok();
    }

    @OperationLog(module = "航班管理", action = "查询航班日志", saveParams = false)
    @Operation(summary = "航班日志")
    @GetMapping("/{flightId}/logs")
    public Result<List<Map<String, Object>>> getFlightLogs(@PathVariable Long flightId) {
        return Result.ok(adminFlightService.getFlightLogs(flightId));
    }

    @OperationLog(module = "航班管理", action = "查询舱位列表", saveParams = false)
    @Operation(summary = "获取航班舱位列表")
    @GetMapping("/{flightId}/cabins")
    public Result<List<Map<String, Object>>> getFlightCabins(@PathVariable Long flightId) {
        List<FlightCabin> cabins = flightCabinMapper.selectList(
                new LambdaQueryWrapper<FlightCabin>()
                        .eq(FlightCabin::getFlightId, flightId)
                        .eq(FlightCabin::getDeleted, 0));
        List<Map<String, Object>> result = cabins.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cabinId", c.getId().toString());
            m.put("cabinClass", c.getCabinClass());
            m.put("cabinName", c.getCabinName());
            m.put("fare", c.getFare());
            m.put("tax", c.getTax());
            m.put("totalPrice", c.getTotalPrice());
            m.put("totalSeats", c.getTotalSeats());
            m.put("availableSeats", c.getAvailableSeats());
            m.put("baggage", c.getBaggage());
            m.put("refundRule", c.getRefundRule());
            m.put("changeRule", c.getChangeRule());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(result);
    }

    @OperationLog(module = "航班管理", action = "更新舱位")
    @Operation(summary = "更新航班舱位")
    @PutMapping("/{flightId}/cabins/{cabinId}")
    public Result<Void> updateFlightCabin(@PathVariable Long flightId, @PathVariable Long cabinId,
                                           @RequestBody Map<String, Object> body) {
        FlightCabin cabin = flightCabinMapper.selectById(cabinId);
        if (cabin == null) return Result.fail("舱位不存在");
        if (body.containsKey("fare")) cabin.setFare(new java.math.BigDecimal(body.get("fare").toString()));
        if (body.containsKey("tax")) cabin.setTax(new java.math.BigDecimal(body.get("tax").toString()));
        if (body.containsKey("totalPrice")) {
            cabin.setTotalPrice(new java.math.BigDecimal(body.get("totalPrice").toString()));
        } else if (body.containsKey("fare") || body.containsKey("tax")) {
            cabin.setTotalPrice(cabin.getFare().add(cabin.getTax() != null ? cabin.getTax() : java.math.BigDecimal.ZERO));
        }
        if (body.containsKey("totalSeats")) cabin.setTotalSeats(Integer.valueOf(body.get("totalSeats").toString()));
        if (body.containsKey("availableSeats")) cabin.setAvailableSeats(Integer.valueOf(body.get("availableSeats").toString()));
        if (body.containsKey("baggage")) cabin.setBaggage((String) body.get("baggage"));
        if (body.containsKey("refundRule")) cabin.setRefundRule((String) body.get("refundRule"));
        if (body.containsKey("changeRule")) cabin.setChangeRule((String) body.get("changeRule"));
        flightCabinMapper.updateById(cabin);
        return Result.ok();
    }

    @OperationLog(module = "航班管理", action = "查询航班班次", saveParams = false)
    @Operation(summary = "按航班号+日期查询所有班次（同航班号同一天可能有多个时段）")
    @GetMapping("/lookup")
    public Result<List<Map<String, Object>>> lookupFlight(
            @RequestParam String flightNo,
            @RequestParam(required = false) String date) {
        LambdaQueryWrapper<Flight> wrapper = new LambdaQueryWrapper<Flight>()
                .eq(Flight::getFlightNo, flightNo)
                .eq(Flight::getDeleted, 0);
        if (date != null && !date.isEmpty()) {
            wrapper.eq(Flight::getFlightDate, LocalDate.parse(date));
        }
        // 不传日期时，返回该航班号所有班次（日期倒序，最近的在前）
        wrapper.orderByDesc(Flight::getFlightDate)
                .orderByAsc(Flight::getDepartureTime);
        List<Flight> flights = flightMapper.selectList(wrapper);
        List<Map<String, Object>> result = flights.stream().map(flight -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("flightId", flight.getId().toString());
            m.put("flightNo", flight.getFlightNo());
            m.put("date", flight.getFlightDate() != null ? flight.getFlightDate().toString() : null);
            m.put("departureTime", flight.getDepartureTime());
            m.put("arrivalTime", flight.getArrivalTime());
            m.put("route", flight.getDepartureAirport() + "-" + flight.getArrivalAirport());
            m.put("departureTerminal", flight.getDepartureTerminal());
            m.put("arrivalTerminal", flight.getArrivalTerminal());
            m.put("status", flight.getStatus());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(result);
    }

    @OperationLog(module = "航班管理", action = "查询航线地图", saveParams = false)
    @Operation(summary = "航线地图数据（机场点位 + 航线连线聚合）")
    @GetMapping("/route-map")
    public Result<Map<String, Object>> getRouteMap(@RequestParam(required = false) String date) {
        LambdaQueryWrapper<Flight> wrapper = new LambdaQueryWrapper<Flight>()
                .eq(Flight::getDeleted, 0);
        if (date != null && !date.isEmpty()) {
            wrapper.eq(Flight::getFlightDate, LocalDate.parse(date));
        }
        List<Flight> flights = flightMapper.selectList(wrapper);

        // 按航线聚合航班数，同时统计每个机场的航班数
        Map<String, Map<String, Object>> routeAgg = new LinkedHashMap<>();
        Map<String, Integer> airportCount = new LinkedHashMap<>();
        for (Flight f : flights) {
            String dep = f.getDepartureAirport();
            String arr = f.getArrivalAirport();
            if (dep == null || arr == null) continue;
            airportCount.merge(dep, 1, Integer::sum);
            airportCount.merge(arr, 1, Integer::sum);
            String key = dep + "-" + arr;
            Map<String, Object> r = routeAgg.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("route", key);
                m.put("from", dep);
                m.put("to", arr);
                m.put("flightCount", 0);
                return m;
            });
            r.put("flightCount", ((Integer) r.get("flightCount")) + 1);
        }

        // 机场坐标（WGS-84，前端转 GCJ-02）
        List<Airport> airportList = airportMapper.selectList(
                new LambdaQueryWrapper<Airport>()
                        .eq(Airport::getDeleted, 0)
                        .isNotNull(Airport::getLongitude)
                        .isNotNull(Airport::getLatitude));
        Map<String, Airport> airportMap = airportList.stream()
                .collect(Collectors.toMap(Airport::getCode, a -> a, (a, b) -> a));

        List<Map<String, Object>> points = new ArrayList<>();
        for (Map.Entry<String, Integer> e : airportCount.entrySet()) {
            Airport a = airportMap.get(e.getKey());
            if (a == null) continue;
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("code", a.getCode());
            p.put("name", a.getName());
            p.put("city", a.getCity());
            p.put("longitude", a.getLongitude());
            p.put("latitude", a.getLatitude());
            p.put("flightCount", e.getValue());
            points.add(p);
        }

        List<Map<String, Object>> routes = new ArrayList<>();
        for (Map<String, Object> r : routeAgg.values()) {
            Airport from = airportMap.get((String) r.get("from"));
            Airport to = airportMap.get((String) r.get("to"));
            if (from == null || to == null) continue;
            Map<String, Object> m = new LinkedHashMap<>(r);
            m.put("fromLng", from.getLongitude());
            m.put("fromLat", from.getLatitude());
            m.put("toLng", to.getLongitude());
            m.put("toLat", to.getLatitude());
            routes.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("airports", points);
        result.put("routes", routes);
        result.put("flightCount", flights.size());
        result.put("airportCount", points.size());
        result.put("routeCount", routes.size());
        return Result.ok(result);
    }
}
