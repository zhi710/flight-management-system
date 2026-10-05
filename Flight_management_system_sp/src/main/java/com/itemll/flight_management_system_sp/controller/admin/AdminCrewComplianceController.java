package com.itemll.flight_management_system_sp.controller.admin;

import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.service.CrewComplianceService;
import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "管理端-机组合规", description = "机组合规监控、飞行时间累计、资质到期预警")
@RestController
@RequestMapping("/admin/crew-compliance")
@RequiredArgsConstructor
public class AdminCrewComplianceController {

    private final CrewComplianceService crewComplianceService;

    @Operation(summary = "机组合规看板")
    @OperationLog(module = "合规审查", action = "查询合规看板", saveParams = false)
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboard() {
        return Result.ok(crewComplianceService.getDashboard());
    }

    @Operation(summary = "机组飞行时间汇总")
    @OperationLog(module = "合规审查", action = "查询机组飞行时间汇总")
    @GetMapping("/summary/{crewId}")
    public Result<List<Map<String, Object>>> getCrewSummary(@PathVariable Long crewId,
                                                             @RequestParam(defaultValue = "30") int days) {
        return Result.ok(crewComplianceService.getCrewFlightSummary(crewId, days));
    }

    @Operation(summary = "合规预警列表")
    @OperationLog(module = "合规审查", action = "查询合规预警列表", saveParams = false)
    @GetMapping("/warnings")
    public Result<List<Map<String, Object>>> getWarnings() {
        return Result.ok(crewComplianceService.getComplianceWarnings());
    }
}
