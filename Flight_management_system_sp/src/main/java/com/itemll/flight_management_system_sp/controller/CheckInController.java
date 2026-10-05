package com.itemll.flight_management_system_sp.controller;

import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.dto.*;
import com.itemll.flight_management_system_sp.service.CheckInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 在线值机控制器
 */
@Tag(name = "在线值机", description = "可值机航班查询、座位图、选座、办理值机、电子登机牌")
@RestController
@RequestMapping("/checkin")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @Operation(summary = "查询可值机航班")
    @GetMapping("/available")
    public Result<List<Map<String, Object>>> availableCheckIn(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(checkInService.getAvailableCheckIn(userId));
    }

    @Operation(summary = "获取座位图")
    @GetMapping("/seats/{flightId}")
    public Result<Map<String, Object>> seatMap(@PathVariable Long flightId) {
        return Result.ok(checkInService.getSeatMap(flightId));
    }

    @Operation(summary = "选择座位")
    @PostMapping("/seats")
    public Result<Void> selectSeat(@Valid @RequestBody SeatSelectDTO dto) {
        checkInService.selectSeat(dto.getOrderId(), dto.getPassengerIndex(), dto.getRow(), dto.getColumn());
        return Result.ok();
    }

    @Operation(summary = "办理值机")
    @PostMapping
    public Result<Map<String, Object>> doCheckIn(@Valid @RequestBody CheckInDTO dto,
                                                  HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(checkInService.doCheckIn(dto, userId));
    }

    @Operation(summary = "获取电子登机牌")
    @GetMapping("/boarding-pass/{checkinId}")
    public Result<Map<String, Object>> boardingPass(@PathVariable String checkinId) {
        return Result.ok(checkInService.getBoardingPass(checkinId));
    }

    @Operation(summary = "取消值机")
    @PostMapping("/{orderId}/cancel")
    public Result<Map<String, Object>> cancelCheckIn(@PathVariable String orderId,
                                                      HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(checkInService.cancelCheckIn(orderId, userId));
    }
}
