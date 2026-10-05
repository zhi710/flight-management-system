package com.itemll.flight_management_system_sp.controller.admin;

import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.service.AdminTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端客票管理控制器
 */
@Tag(name = "管理端-客票管理", description = "订座管理、票价管理、退改审核")
@RestController
@RequestMapping("/admin/tickets")
@RequiredArgsConstructor
public class AdminTicketController {

    private final AdminTicketService adminTicketService;

    @OperationLog(module = "票务管理", action = "查询订座列表", saveParams = false)
    @Operation(summary = "订座管理")
    @GetMapping("/bookings")
    public Result<PageResult<Map<String, Object>>> getBookings(
            @RequestParam(required = false) String pnr,
            @RequestParam(required = false) String passengerName,
            @RequestParam(required = false) String flightNo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(adminTicketService.getBookings(pnr, passengerName, flightNo, page, pageSize));
    }

    @OperationLog(module = "票务管理", action = "查看订座详情", saveParams = false)
    @Operation(summary = "订座详情")
    @GetMapping("/bookings/{pnr}")
    public Result<Map<String, Object>> getBookingDetail(@PathVariable String pnr) {
        return Result.ok(adminTicketService.getBookingDetail(pnr));
    }

    @OperationLog(module = "票务管理", action = "查询运价列表", saveParams = false)
    @Operation(summary = "运价列表")
    @GetMapping("/fares")
    public Result<PageResult<Map<String, Object>>> getFares(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(adminTicketService.getFares(page, pageSize));
    }

    @OperationLog(module = "票务管理", action = "创建运价")
    @Operation(summary = "创建运价")
    @PostMapping("/fares")
    public Result<Void> createFare(@RequestBody Map<String, Object> fare) {
        adminTicketService.createFare(fare);
        return Result.ok();
    }

    @OperationLog(module = "票务管理", action = "更新运价")
    @Operation(summary = "更新运价")
    @PutMapping("/fares/{fareId}")
    public Result<Void> updateFare(@PathVariable Long fareId, @RequestBody Map<String, Object> fare) {
        adminTicketService.updateFare(fareId, fare);
        return Result.ok();
    }

    @OperationLog(module = "票务管理", action = "查询退票列表", saveParams = false)
    @Operation(summary = "退票列表")
    @GetMapping("/refunds")
    public Result<List<Map<String, Object>>> getRefunds() {
        return Result.ok(adminTicketService.getRefunds());
    }

    @OperationLog(module = "票务管理", action = "审核退票")
    @Operation(summary = "审核退票")
    @PostMapping("/refunds/{refundId}/approve")
    public Result<Void> approveRefund(@PathVariable String refundId, @RequestBody Map<String, Object> body) {
        adminTicketService.approveRefund(refundId, body);
        return Result.ok();
    }

    @OperationLog(module = "票务管理", action = "查询改签列表", saveParams = false)
    @Operation(summary = "改签列表")
    @GetMapping("/changes")
    public Result<List<Map<String, Object>>> getChanges() {
        return Result.ok(adminTicketService.getChanges());
    }

    @OperationLog(module = "票务管理", action = "审核改签")
    @Operation(summary = "审核改签")
    @PostMapping("/changes/{changeId}/approve")
    public Result<Void> approveChange(@PathVariable String changeId, @RequestBody Map<String, Object> body) {
        adminTicketService.approveChange(changeId, body);
        return Result.ok();
    }
}
