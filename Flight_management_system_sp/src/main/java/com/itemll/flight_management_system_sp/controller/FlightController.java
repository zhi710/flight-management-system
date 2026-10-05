package com.itemll.flight_management_system_sp.controller;

import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.dto.FlightSearchDTO;
import com.itemll.flight_management_system_sp.service.FlightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 航班搜索控制器
 */
@Tag(name = "航班搜索", description = "航班搜索、详情、热门航线、特价机票")
@RestController
@RequestMapping("/flights")
@RequiredArgsConstructor
public class FlightController {

    private final FlightService flightService;

    @Operation(summary = "搜索航班")
    @GetMapping("/search")
    public Result<Map<String, Object>> search(@Valid FlightSearchDTO dto) {
        return Result.ok(flightService.searchFlights(dto));
    }

    @Operation(summary = "航班大屏（指定日期的航班动态）")
    @GetMapping("/board")
    public Result<List<Map<String, Object>>> board(@RequestParam(required = false) String date) {
        return Result.ok(flightService.getFlightBoard(date));
    }

    @Operation(summary = "机场列表（机场大屏选择器）")
    @GetMapping("/airports")
    public Result<List<Map<String, Object>>> airports() {
        return Result.ok(flightService.listAirports());
    }

    @Operation(summary = "航班详情")
    @GetMapping("/{flightId}")
    public Result<Map<String, Object>> detail(@PathVariable Long flightId) {
        return Result.ok(flightService.getFlightDetail(flightId));
    }

    @Operation(summary = "热门航线")
    @GetMapping("/hot-routes")
    public Result<List<Map<String, Object>>> hotRoutes(
            @RequestParam(required = false, defaultValue = "北京") String city,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return Result.ok(flightService.getHotRoutes(city, limit));
    }

    @Operation(summary = "特价机票")
    @GetMapping("/deals")
    public Result<List<Map<String, Object>>> deals(
            @RequestParam(required = false) String city,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return Result.ok(flightService.getDeals(city, limit));
    }
}
