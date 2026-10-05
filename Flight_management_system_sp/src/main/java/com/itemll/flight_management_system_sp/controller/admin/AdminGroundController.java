package com.itemll.flight_management_system_sp.controller.admin;

import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.service.GroundHandlingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "管理端-地面保障", description = "地面保障节点管理")
@RestController
@RequestMapping("/admin/ground")
@RequiredArgsConstructor
public class AdminGroundController {

    private final GroundHandlingService groundHandlingService;

    @OperationLog(module = "机坪保障", action = "查询今日概览", saveParams = false)
    @Operation(summary = "今日地面保障概览")
    @GetMapping("/today")
    public Result<Map<String, Object>> getTodaySummary() {
        return Result.ok(groundHandlingService.getTodaySummary());
    }

    @OperationLog(module = "机坪保障", action = "查询保障节点列表", saveParams = false)
    @Operation(summary = "航班地面保障节点列表")
    @GetMapping("/nodes/{flightId}")
    public Result<List<Map<String, Object>>> getNodes(@PathVariable Long flightId) {
        return Result.ok(groundHandlingService.getNodesForFlight(flightId));
    }

    @OperationLog(module = "机坪保障", action = "查询保障记录", saveParams = false)
    @Operation(summary = "地面保障记录列表")
    @GetMapping
    public Result<PageResult<Map<String, Object>>> getList(
            @RequestParam(required = false) String flightNo,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(groundHandlingService.getGroundHandlingList(flightNo, date, status, page, pageSize));
    }

    @OperationLog(module = "机坪保障", action = "查询保障概览（按航班）", saveParams = false)
    @Operation(summary = "航班地面保障概览（按航班分组）")
    @GetMapping("/flights")
    public Result<List<Map<String, Object>>> getFlightGroundSummary(
            @RequestParam(required = false) String date) {
        return Result.ok(groundHandlingService.getFlightGroundSummary(date));
    }

    @OperationLog(module = "机坪保障", action = "标记航班起飞")
    @Operation(summary = "标记航班起飞（自动完成离港节点）")
    @PostMapping("/flights/{flightId}/depart")
    public Result<Void> departFlight(@PathVariable Long flightId, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("currentAdminId");
        groundHandlingService.departFlight(flightId, adminId);
        return Result.ok();
    }

    @OperationLog(module = "机坪保障", action = "标记航班到达")
    @Operation(summary = "标记航班到达（自动完成到港节点）")
    @PostMapping("/flights/{flightId}/arrive")
    public Result<Void> arriveFlight(@PathVariable Long flightId, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("currentAdminId");
        groundHandlingService.arriveFlight(flightId, adminId);
        return Result.ok();
    }

    @OperationLog(module = "机坪保障", action = "更新节点时间")
    @Operation(summary = "更新节点实际时间")
    @PutMapping("/nodes/{nodeId}/time")
    public Result<Void> updateNodeTime(@PathVariable Long nodeId, @RequestBody Map<String, String> body,
                                        HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("currentAdminId");
        groundHandlingService.updateNodeTime(nodeId, body.get("field"), body.get("value"), adminId);
        return Result.ok();
    }

    @OperationLog(module = "机坪保障", action = "完成保障节点")
    @Operation(summary = "完成单个保障节点（按flightId+nodeCode）")
    @PostMapping("/nodes/complete")
    public Result<Void> completeNode(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("currentAdminId");
        Long flightId = Long.valueOf(body.get("flightId").toString());
        String nodeCode = (String) body.get("nodeCode");
        groundHandlingService.completeNode(flightId, nodeCode, LocalDateTime.now(), adminId);
        return Result.ok();
    }
}
