package com.itemll.flight_management_system_sp.controller;

import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.dto.FeedbackDTO;
import com.itemll.flight_management_system_sp.service.HelpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 帮助中心控制器
 */
@Tag(name = "帮助中心", description = "FAQ、退改政策、行李规定、投诉建议")
@RestController
@RequestMapping("/help")
@RequiredArgsConstructor
public class HelpController {

    private final HelpService helpService;

    @Operation(summary = "获取FAQ列表")
    @GetMapping("/faq")
    public Result<List<Map<String, Object>>> getFaq(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return Result.ok(helpService.getFaq(category, keyword));
    }

    @Operation(summary = "获取退改政策")
    @GetMapping("/refund-policy")
    public Result<Map<String, Object>> getRefundPolicy() {
        return Result.ok(helpService.getRefundPolicy());
    }

    @Operation(summary = "获取行李规定")
    @GetMapping("/baggage-rules")
    public Result<Map<String, Object>> getBaggageRules() {
        return Result.ok(helpService.getBaggageRules());
    }

    @Operation(summary = "提交投诉建议")
    @PostMapping("/feedback")
    public Result<Void> submitFeedback(@Valid @RequestBody FeedbackDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        if (userId == null) {
            // 兜底：反馈必须绑定用户，否则落库 user_id=NULL，旅客端查不到、回复也无法通知
            return Result.fail(401, "未登录");
        }
        helpService.submitFeedback(userId, dto);
        return Result.ok();
    }

    @Operation(summary = "查询我的反馈列表")
    @GetMapping("/feedback")
    public Result<List<Map<String, Object>>> listFeedback(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        return Result.ok(helpService.listFeedback(userId));
    }
}
