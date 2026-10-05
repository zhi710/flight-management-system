package com.itemll.flight_management_system_sp.controller.admin;

import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.service.SsrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "管理端-特殊服务(SSR)", description = "特殊旅客服务管理")
@RestController
@RequestMapping("/admin/ssr")
@RequiredArgsConstructor
public class AdminSsrController {

    private final SsrService ssrService;

    @OperationLog(module = "SSR服务", action = "查询SSR代码", saveParams = false)
    @Operation(summary = "可用SSR代码列表")
    @GetMapping("/codes")
    public Result<List<Map<String, Object>>> getSsrCodes(@RequestParam(required = false) String category) {
        return Result.ok(ssrService.getAvailableSsrCodes(category));
    }

    @OperationLog(module = "SSR服务", action = "查询SSR请求列表", saveParams = false)
    @Operation(summary = "SSR请求列表")
    @GetMapping("/requests")
    public Result<PageResult<Map<String, Object>>> getSsrList(
            @RequestParam(required = false) Long flightId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(ssrService.getSsrList(flightId, status, page, pageSize));
    }

    @OperationLog(module = "SSR服务", action = "提交SSR请求")
    @Operation(summary = "提交SSR请求")
    @PostMapping("/submit")
    public Result<Void> submitSsr(@RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(body.get("orderId").toString());
        Long orderPassengerId = Long.valueOf(body.get("orderPassengerId").toString());
        String ssrCode = (String) body.get("ssrCode");
        String remark = (String) body.getOrDefault("remark", "");
        ssrService.submitSsrRequest(orderId, orderPassengerId, ssrCode, remark);
        return Result.ok();
    }

    @OperationLog(module = "SSR服务", action = "处理SSR请求")
    @Operation(summary = "处理SSR请求（通过/拒绝）")
    @PostMapping("/requests/{requestId}/process")
    public Result<Void> processSsr(@PathVariable Long requestId, @RequestBody Map<String, String> body,
                                    HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("currentAdminId");
        ssrService.processSsrRequest(requestId, body.get("action"), adminId);
        return Result.ok();
    }

    @OperationLog(module = "SSR服务", action = "查询订单SSR申请", saveParams = false)
    @Operation(summary = "查询订单的SSR申请")
    @GetMapping("/orders/{orderId}")
    public Result<List<Map<String, Object>>> getSsrByOrder(@PathVariable Long orderId) {
        return Result.ok(ssrService.getSsrByOrder(orderId));
    }
}
