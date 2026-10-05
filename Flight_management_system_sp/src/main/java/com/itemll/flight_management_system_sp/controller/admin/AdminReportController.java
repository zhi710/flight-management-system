package com.itemll.flight_management_system_sp.controller.admin;

import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.service.AdminReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端报表中心控制器
 */
@Tag(name = "管理端-报表中心", description = "运营报表、收入报表、自定义报表")
@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService adminReportService;

    @OperationLog(module = "报表统计", action = "查询运营报表", saveParams = false)
    @Operation(summary = "运营报表")
    @GetMapping("/operation")
    public Result<Map<String, Object>> getOperationReport(
            @RequestParam String type,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String format) {
        return Result.ok(adminReportService.getOperationReport(type, startDate, endDate, format));
    }

    @OperationLog(module = "报表统计", action = "查询收入报表", saveParams = false)
    @Operation(summary = "收入报表")
    @GetMapping("/revenue")
    public Result<Map<String, Object>> getRevenueReport(
            @RequestParam String type,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        return Result.ok(adminReportService.getRevenueReport(type, startDate, endDate));
    }

    @OperationLog(module = "报表统计", action = "查询自定义报表", saveParams = false)
    @Operation(summary = "自定义报表列表")
    @GetMapping("/custom")
    public Result<List<Map<String, Object>>> getCustomReports() {
        return Result.ok(adminReportService.getCustomReports());
    }

    @OperationLog(module = "报表统计", action = "创建自定义报表")
    @Operation(summary = "创建自定义报表")
    @PostMapping("/custom")
    public Result<Void> createCustomReport(@RequestBody Map<String, Object> report) {
        adminReportService.createCustomReport(report);
        return Result.ok();
    }

    @OperationLog(module = "报表统计", action = "生成报表")
    @Operation(summary = "生成报表")
    @PostMapping("/custom/{reportId}/generate")
    public Result<Void> generateReport(@PathVariable Long reportId) {
        adminReportService.generateReport(reportId);
        return Result.ok();
    }
}
