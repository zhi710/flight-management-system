package com.itemll.flight_management_system_sp.controller;

import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.dto.OrderChangeDTO;
import com.itemll.flight_management_system_sp.dto.OrderCreateDTO;
import com.itemll.flight_management_system_sp.dto.OrderRefundDTO;
import com.itemll.flight_management_system_sp.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 机票预订控制器
 * <p>核心接口：创建订单使用 Redis 分布式锁 + 乐观锁 + SQL 条件扣减三重并发控制。</p>
 */
@Tag(name = "机票预订", description = "订单创建、查询、取消、改签、退票")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "创建订单（含并发控制）", description = "Redis分布式锁 + 乐观锁 + SQL条件扣减，防止高并发超卖")
    @PostMapping
    public Result<Map<String, Object>> createOrder(@Valid @RequestBody OrderCreateDTO dto,
                                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(orderService.createOrder(dto, userId));
    }

    @Operation(summary = "查询订单详情")
    @GetMapping("/{orderId}")
    public Result<Map<String, Object>> getOrder(@PathVariable String orderId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(orderService.getOrderDetail(orderId, userId));
    }

    @Operation(summary = "订单列表")
    @GetMapping
    public Result<PageResult<Map<String, Object>>> getOrderList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(orderService.getOrderList(userId, status, startDate, endDate, keyword, page, pageSize));
    }

    @Operation(summary = "取消订单")
    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancelOrder(@PathVariable String orderId,
                                     @RequestBody(required = false) Map<String, Object> body,
                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        String reason = body != null ? (String) body.get("reason") : null;
        orderService.cancelOrder(orderId, userId, reason);
        return Result.ok();
    }

    @Operation(summary = "申请改签")
    @PostMapping("/{orderId}/change")
    public Result<Map<String, Object>> changeOrder(@PathVariable String orderId,
                                                    @Valid @RequestBody OrderChangeDTO dto,
                                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(orderService.changeOrder(orderId, userId, dto));
    }

    @Operation(summary = "申请退票")
    @PostMapping("/{orderId}/refund")
    public Result<Map<String, Object>> refundOrder(@PathVariable String orderId,
                                                    @Valid @RequestBody OrderRefundDTO dto,
                                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(orderService.refundOrder(orderId, userId, dto));
    }

    @Operation(summary = "查询改签状态")
    @GetMapping("/{orderId}/change/{changeId}")
    public Result<Map<String, Object>> getChangeStatus(@PathVariable String orderId,
                                                        @PathVariable String changeId,
                                                        HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(orderService.getChangeStatus(orderId, changeId, userId));
    }

    @Operation(summary = "查询退票状态")
    @GetMapping("/{orderId}/refund/{refundId}")
    public Result<Map<String, Object>> getRefundStatus(@PathVariable String orderId,
                                                        @PathVariable String refundId,
                                                        HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(orderService.getRefundStatus(orderId, refundId, userId));
    }

    @Operation(summary = "我的改签记录列表")
    @GetMapping("/changes")
    public Result<List<Map<String, Object>>> listChanges(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(orderService.listChanges(userId));
    }

    @Operation(summary = "我的退票记录列表")
    @GetMapping("/refunds")
    public Result<List<Map<String, Object>>> listRefunds(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(orderService.listRefunds(userId));
    }
}
