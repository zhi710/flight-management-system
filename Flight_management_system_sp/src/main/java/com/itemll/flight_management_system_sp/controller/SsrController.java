package com.itemll.flight_management_system_sp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.entity.Order;
import com.itemll.flight_management_system_sp.mapper.OrderMapper;
import com.itemll.flight_management_system_sp.service.SsrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "旅客端-特殊服务(SSR)", description = "特殊旅客服务")
@RestController
@RequestMapping("/ssr")
@RequiredArgsConstructor
public class SsrController {

    private final SsrService ssrService;
    private final OrderMapper orderMapper;

    @Operation(summary = "可用SSR代码列表")
    @GetMapping("/codes")
    public Result<List<Map<String, Object>>> getSsrCodes(@RequestParam(required = false) String category) {
        return Result.ok(ssrService.getAvailableSsrCodes(category));
    }

    @Operation(summary = "提交SSR请求")
    @PostMapping("/requests")
    public Result<Void> submitSsr(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        String orderIdStr = body.get("orderId").toString();
        // orderId 可能是 "ORD..." 或纯数字，统一按 orderId 字符串查询
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderId, orderIdStr));
        if (order == null) {
            // 降级：尝试按 id 查询（前端可能传纯数字PK）
            try {
                Long numericId = Long.valueOf(orderIdStr);
                order = orderMapper.selectById(numericId);
            } catch (NumberFormatException ignored) {}
        }
        if (order == null) throw new BusinessException("订单不存在");
        Long orderPassengerId = Long.valueOf(body.get("orderPassengerId").toString());
        String ssrCode = (String) body.get("ssrCode");
        String remark = (String) body.getOrDefault("remark", "");
        ssrService.submitSsrRequest(order.getId(), orderPassengerId, ssrCode, remark);
        return Result.ok();
    }

    @Operation(summary = "查询订单SSR申请")
    @GetMapping("/orders/{orderId}")
    public Result<List<Map<String, Object>>> getSsrByOrder(@PathVariable String orderId) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderId, orderId));
        if (order == null) return Result.ok(List.of());
        return Result.ok(ssrService.getSsrByOrder(order.getId()));
    }
}
