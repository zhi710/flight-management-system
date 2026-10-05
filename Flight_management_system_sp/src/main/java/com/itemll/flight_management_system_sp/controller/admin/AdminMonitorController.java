package com.itemll.flight_management_system_sp.controller.admin;

import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.service.AdminMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端运营监控控制器
 */
@Tag(name = "管理端-运营监控", description = "监控大屏、告警中心、统计概览")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminMonitorController {

    private final AdminMonitorService adminMonitorService;

    @OperationLog(module = "监控告警", action = "查询监控大屏", saveParams = false)
    @Operation(summary = "监控大屏数据")
    @GetMapping("/monitor/dashboard")
    public Result<Map<String, Object>> getDashboard() {
        return Result.ok(adminMonitorService.getDashboard());
    }

    @OperationLog(module = "监控告警", action = "查询告警列表", saveParams = false)
    @Operation(summary = "告警中心")
    @GetMapping("/alerts")
    public Result<PageResult<Map<String, Object>>> getAlerts(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(adminMonitorService.getAlerts(level, type, status, page, pageSize));
    }

    @OperationLog(module = "监控告警", action = "查询告警统计", saveParams = false)
    @Operation(summary = "告警统计")
    @GetMapping("/alerts/stats")
    public Result<Map<String, Object>> getAlertStats() {
        return Result.ok(adminMonitorService.getAlertStats());
    }

    @OperationLog(module = "监控告警", action = "处理告警")
    @Operation(summary = "处理告警")
    @PostMapping("/alerts/{alertId}/resolve")
    public Result<Void> resolveAlert(@PathVariable Long alertId, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("currentAdminId");
        adminMonitorService.resolveAlert(alertId, adminId);
        return Result.ok();
    }

    @OperationLog(module = "监控告警", action = "查询统计概览", saveParams = false)
    @Operation(summary = "统计概览")
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics(
            @RequestParam String dimension,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String groupBy) {
        return Result.ok(adminMonitorService.getStatistics(dimension, startDate, endDate, groupBy));
    }
}
