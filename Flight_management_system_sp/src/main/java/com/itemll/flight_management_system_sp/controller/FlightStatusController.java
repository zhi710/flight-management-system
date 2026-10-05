package com.itemll.flight_management_system_sp.controller;

import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.dto.FlightSubscribeDTO;
import com.itemll.flight_management_system_sp.service.FlightStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 航班动态控制器
 */
@Tag(name = "航班动态", description = "航班动态查询、订阅")
@RestController
@RequestMapping("/flight-status")
@RequiredArgsConstructor
public class FlightStatusController {

    private final FlightStatusService flightStatusService;

    @Operation(summary = "查询航班动态")
    @GetMapping
    public Result<Map<String, Object>> getFlightStatus(
            @RequestParam(required = false) String flightNo,
            @RequestParam String date,
            @RequestParam(required = false) String departure,
            @RequestParam(required = false) String arrival) {
        return Result.ok(flightStatusService.getFlightStatus(flightNo, date, departure, arrival));
    }

    @Operation(summary = "订阅航班动态")
    @PostMapping("/subscribe")
    public Result<Void> subscribe(@Valid @RequestBody FlightSubscribeDTO dto,
                                   HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        String channels = String.join(",", dto.getChannels());
        String types = String.join(",", dto.getTypes());
        flightStatusService.subscribe(userId, dto.getFlightNo(), dto.getDate(), channels, types);
        return Result.ok();
    }

    @Operation(summary = "取消订阅")
    @DeleteMapping("/subscribe/{subscriptionId}")
    public Result<Void> unsubscribe(@PathVariable Long subscriptionId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        flightStatusService.unsubscribe(subscriptionId, userId);
        return Result.ok();
    }

    @Operation(summary = "查询我的订阅列表")
    @GetMapping("/subscriptions")
    public Result<List<Map<String, Object>>> listSubscriptions(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        return Result.ok(flightStatusService.listSubscriptions(userId));
    }
}
