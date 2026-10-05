package com.itemll.flight_management_system_sp.controller.admin;

import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.service.AdminFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端投诉建议控制器
 */
@Tag(name = "管理端-投诉建议", description = "旅客反馈查询与回复")
@RestController
@RequestMapping("/admin/feedback")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final AdminFeedbackService adminFeedbackService;

    @Operation(summary = "反馈列表")
    @GetMapping
    public Result<List<Map<String, Object>>> listFeedback(
            @RequestParam(required = false) String status) {
        return Result.ok(adminFeedbackService.listFeedback(status));
    }

    @Operation(summary = "回复反馈")
    @PostMapping("/{id}/reply")
    public Result<Void> replyFeedback(@PathVariable Long id, @RequestBody Map<String, String> body) {
        adminFeedbackService.replyFeedback(id, body.get("reply"));
        return Result.ok();
    }
}
