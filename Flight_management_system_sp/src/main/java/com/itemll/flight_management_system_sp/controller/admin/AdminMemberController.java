package com.itemll.flight_management_system_sp.controller.admin;

import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.service.AdminMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端前台用户（旅客会员）管理控制器
 */
@Tag(name = "管理端-前台用户", description = "前台旅客用户列表、编辑、启用/禁用、重置密码")
@RestController
@RequestMapping("/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @OperationLog(module = "前台用户", action = "查询用户列表", saveParams = false)
    @Operation(summary = "前台用户列表")
    @GetMapping
    public Result<PageResult<Map<String, Object>>> getMemberList(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String memberLevel,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(adminMemberService.getMemberList(phone, name, memberLevel, status, page, pageSize));
    }

    @OperationLog(module = "前台用户", action = "更新用户")
    @Operation(summary = "更新前台用户")
    @PutMapping("/{id}")
    public Result<Void> updateMember(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        adminMemberService.updateMember(id, data);
        return Result.ok();
    }

    @OperationLog(module = "前台用户", action = "禁用用户")
    @Operation(summary = "禁用前台用户")
    @PostMapping("/{id}/disable")
    public Result<Void> disableMember(@PathVariable Long id) {
        adminMemberService.disableMember(id);
        return Result.ok();
    }

    @OperationLog(module = "前台用户", action = "启用用户")
    @Operation(summary = "启用前台用户")
    @PostMapping("/{id}/enable")
    public Result<Void> enableMember(@PathVariable Long id) {
        adminMemberService.enableMember(id);
        return Result.ok();
    }

    @OperationLog(module = "前台用户", action = "重置密码")
    @Operation(summary = "重置前台用户密码")
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetMemberPassword(@PathVariable Long id) {
        adminMemberService.resetMemberPassword(id);
        return Result.ok();
    }
}
