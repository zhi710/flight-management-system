package com.itemll.flight_management_system_sp.controller;

import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "通知", description = "旅客通知查询")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "查询通知历史")
    @GetMapping
    public Result<List<Map<String, Object>>> getNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        return Result.ok(notificationService.getPassengerNotifications(userId, page, pageSize));
    }

    @Operation(summary = "标记单条通知已读")
    @PostMapping("/read/{id}")
    public Result<Void> markRead(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        notificationService.markRead(userId, id);
        return Result.ok();
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public Result<Void> markAllRead(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        notificationService.markAllRead(userId);
        return Result.ok();
    }
}
