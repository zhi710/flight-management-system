package com.itemll.flight_management_system_sp.controller.admin;

import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.dto.CancelCheckInDTO;
import com.itemll.flight_management_system_sp.service.AdminCheckInService;
import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端值机控制器
 */
@Tag(name = "管理端-值机管理", description = "值机开放/关闭、手动值机、自动分配座位")
@RestController
@RequestMapping("/admin/checkin")
@RequiredArgsConstructor
public class AdminCheckInController {

    private final AdminCheckInService adminCheckInService;

    @Operation(summary = "按航班号+日期搜索航班")
    @OperationLog(module = "值机管理", action = "搜索航班")
    @GetMapping("/search")
    public Result<Map<String, Object>> searchFlight(
            @RequestParam String flightNo,
            @RequestParam String date) {
        return Result.ok(adminCheckInService.searchFlight(flightNo, date));
    }

    @Operation(summary = "获取航班值机信息")
    @OperationLog(module = "值机管理", action = "查询值机信息")
    @GetMapping("/{flightId}")
    public Result<Map<String, Object>> getCheckInInfo(@PathVariable Long flightId) {
        return Result.ok(adminCheckInService.getCheckInInfo(flightId));
    }

    @Operation(summary = "开放值机")
    @OperationLog(module = "值机管理", action = "开放值机")
    @PostMapping("/{flightId}/open")
    public Result<Void> openCheckIn(@PathVariable Long flightId) {
        adminCheckInService.openCheckIn(flightId);
        return Result.ok();
    }

    @Operation(summary = "关闭值机")
    @OperationLog(module = "值机管理", action = "关闭值机")
    @PostMapping("/{flightId}/close")
    public Result<Void> closeCheckIn(@PathVariable Long flightId) {
        adminCheckInService.closeCheckIn(flightId);
        return Result.ok();
    }

    @Operation(summary = "手动值机")
    @OperationLog(module = "值机管理", action = "手动值机")
    @PostMapping("/{flightId}/checkin")
    public Result<Void> manualCheckIn(@PathVariable Long flightId, @RequestBody Map<String, Object> params) {
        Integer passengerIndex = (Integer) params.get("passengerIndex");
        Integer seatRow = (Integer) params.get("seatRow");
        String seatColumn = (String) params.get("seatColumn");
        adminCheckInService.manualCheckIn(flightId, passengerIndex, seatRow, seatColumn);
        return Result.ok();
    }

    @Operation(summary = "取消值机")
    @OperationLog(module = "值机管理", action = "取消值机")
    @PostMapping("/{flightId}/cancel-checkin")
    public Result<Void> cancelCheckIn(@PathVariable Long flightId, @Valid @RequestBody CancelCheckInDTO dto) {
        adminCheckInService.cancelCheckIn(flightId, dto.getPassengerIndex());
        return Result.ok();
    }

    @Operation(summary = "自动分配座位")
    @OperationLog(module = "值机管理", action = "自动分配座位")
    @PostMapping("/{flightId}/auto-assign")
    public Result<Void> autoAssign(@PathVariable Long flightId) {
        adminCheckInService.autoAssignSeats(flightId);
        return Result.ok();
    }

    @Operation(summary = "导出值机名单")
    @OperationLog(module = "值机管理", action = "导出值机名单")
    @GetMapping("/{flightId}/export")
    public Result<Map<String, Object>> exportCheckInList(@PathVariable Long flightId) {
        Map<String, Object> info = adminCheckInService.getCheckInInfo(flightId);
        info.put("exportUrl", "/api/admin/checkin/" + flightId + "/export/file");
        info.put("format", "EXCEL");
        info.put("message", "导出任务已生成，请下载");
        return Result.ok(info);
    }
}
