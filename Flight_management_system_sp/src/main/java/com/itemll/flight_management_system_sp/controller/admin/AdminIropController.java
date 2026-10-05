package com.itemll.flight_management_system_sp.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.entity.Order;
import com.itemll.flight_management_system_sp.mapper.OrderMapper;
import com.itemll.flight_management_system_sp.service.AdminIropService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "管理端-不正常航班(IROPS)", description = "不正常航班记录、受影响旅客、自动改签确认、手动改签")
@RestController
@RequestMapping("/admin/irop")
@RequiredArgsConstructor
public class AdminIropController {

    private final AdminIropService adminIropService;
    private final OrderMapper orderMapper;

    @OperationLog(module = "异常管理", action = "查询操作记录", saveParams = false)
    @Operation(summary = "不正常航班操作记录列表")
    @GetMapping("/operations")
    public Result<PageResult<Map<String, Object>>> getIropList(
            @RequestParam(required = false) String flightNo,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(adminIropService.getIropList(flightNo, type, status, page, pageSize));
    }

    @OperationLog(module = "异常管理", action = "查询受影响旅客", saveParams = false)
    @Operation(summary = "查询受影响旅客列表")
    @GetMapping("/passengers/{flightId}")
    public Result<List<Map<String, Object>>> getAffectedPassengers(@PathVariable Long flightId) {
        return Result.ok(adminIropService.getAffectedPassengers(flightId));
    }

    @OperationLog(module = "异常管理", action = "查询改签结果", saveParams = false)
    @Operation(summary = "自动改签结果列表")
    @GetMapping("/rebookings")
    public Result<PageResult<Map<String, Object>>> getRebookingList(
            @RequestParam(required = false) Long flightId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(adminIropService.getRebookingList(flightId, status, page, pageSize));
    }

    @OperationLog(module = "异常管理", action = "确认/拒绝自动改签")
    @Operation(summary = "确认/拒绝自动改签")
    @PostMapping("/rebookings/{rebookingId}/confirm")
    public Result<Void> confirmRebooking(
            @PathVariable Long rebookingId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("currentAdminId");
        adminIropService.confirmRebooking(rebookingId, body.get("action"), adminId);
        return Result.ok();
    }

    @OperationLog(module = "异常管理", action = "手动改签")
    @Operation(summary = "手动改签")
    @PostMapping("/manual-rebook")
    public Result<Map<String, Object>> manualRebook(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("currentAdminId");
        String orderIdStr = body.get("orderId").toString();
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderId, orderIdStr));
        if (order == null) throw new BusinessException("订单不存在");
        Long newFlightId = Long.valueOf(body.get("newFlightId").toString());
        String newCabinClass = (String) body.get("newCabinClass");
        return Result.ok(adminIropService.manualRebook(order.getId(), newFlightId, newCabinClass, adminId));
    }

    @OperationLog(module = "异常管理", action = "重新通知旅客")
    @Operation(summary = "重新通知受影响旅客")
    @PostMapping("/operations/{operationId}/notify")
    public Result<Void> notifyPassengers(@PathVariable Long operationId) {
        adminIropService.notifyPassengers(operationId);
        return Result.ok();
    }

    @OperationLog(module = "异常管理", action = "查询补偿规则", saveParams = false)
    @Operation(summary = "补偿规则")
    @GetMapping("/compensation-rules")
    public Result<Map<String, Object>> getCompensationRules() {
        return Result.ok(adminIropService.getCompensationRules());
    }

    @OperationLog(module = "异常管理", action = "查询延误原因代码", saveParams = false)
    @Operation(summary = "IATA延误原因代码")
    @GetMapping("/delay-codes")
    public Result<List<Map<String, String>>> getIataDelayCodes() {
        return Result.ok(adminIropService.getIataDelayCodes());
    }
}
