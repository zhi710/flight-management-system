package com.itemll.flight_management_system_sp.controller.admin;

import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.service.AdminSystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端系统管理控制器
 */
@Tag(name = "管理端-系统管理", description = "用户管理、角色权限、基础数据、操作日志、系统配置")
@RestController
@RequestMapping("/admin/system")
@RequiredArgsConstructor
public class AdminSystemController {

    private final AdminSystemService adminSystemService;

    // ==================== 用户管理 ====================

    @OperationLog(module = "系统管理", action = "查询用户列表", saveParams = false)
    @Operation(summary = "用户列表")
    @GetMapping("/users")
    public Result<PageResult<Map<String, Object>>> getUserList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(adminSystemService.getUserList(page, pageSize));
    }

    @OperationLog(module = "系统管理", action = "创建用户")
    @Operation(summary = "创建用户")
    @PostMapping("/users")
    public Result<Void> createUser(@RequestBody Map<String, Object> user) {
        adminSystemService.createUser(user);
        return Result.ok();
    }

    @OperationLog(module = "系统管理", action = "更新用户")
    @Operation(summary = "更新用户")
    @PutMapping("/users/{userId}")
    public Result<Void> updateUser(@PathVariable Long userId, @RequestBody Map<String, Object> user) {
        adminSystemService.updateUser(userId, user);
        return Result.ok();
    }

    @OperationLog(module = "系统管理", action = "禁用用户")
    @Operation(summary = "禁用用户")
    @PostMapping("/users/{userId}/disable")
    public Result<Void> disableUser(@PathVariable Long userId) {
        adminSystemService.disableUser(userId);
        return Result.ok();
    }

    @OperationLog(module = "系统管理", action = "启用用户")
    @Operation(summary = "启用用户")
    @PostMapping("/users/{userId}/enable")
    public Result<Void> enableUser(@PathVariable Long userId) {
        adminSystemService.enableUser(userId);
        return Result.ok();
    }

    @OperationLog(module = "系统管理", action = "重置密码")
    @Operation(summary = "重置密码")
    @PostMapping("/users/{userId}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long userId) {
        adminSystemService.resetPassword(userId);
        return Result.ok();
    }

    @OperationLog(module = "系统管理", action = "查看个人信息", saveParams = false)
    @Operation(summary = "获取当前管理员个人信息")
    @GetMapping("/profile")
    public Result<Map<String, Object>> getProfile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentAdminId");
        return Result.ok(adminSystemService.getProfile(userId));
    }

    @OperationLog(module = "系统管理", action = "更新个人信息")
    @Operation(summary = "更新当前管理员个人信息")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody Map<String, Object> data, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentAdminId");
        adminSystemService.updateProfile(userId, data);
        return Result.ok();
    }

    @OperationLog(module = "系统管理", action = "修改密码")
    @Operation(summary = "当前管理员修改密码")
    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentAdminId");
        adminSystemService.changePassword(userId, body.get("oldPassword"), body.get("newPassword"));
        return Result.ok();
    }

    // ==================== 角色权限 ====================

    @OperationLog(module = "系统管理", action = "查询角色列表", saveParams = false)
    @Operation(summary = "角色列表")
    @GetMapping("/roles")
    public Result<List<Map<String, Object>>> getRoleList() {
        return Result.ok(adminSystemService.getRoleList());
    }

    @OperationLog(module = "系统管理", action = "创建角色")
    @Operation(summary = "创建角色")
    @PostMapping("/roles")
    public Result<Void> createRole(@RequestBody Map<String, Object> role) {
        adminSystemService.createRole(role);
        return Result.ok();
    }

    @OperationLog(module = "系统管理", action = "更新角色")
    @Operation(summary = "更新角色")
    @PutMapping("/roles/{roleId}")
    public Result<Void> updateRole(@PathVariable Long roleId, @RequestBody Map<String, Object> role) {
        adminSystemService.updateRole(roleId, role);
        return Result.ok();
    }

    @OperationLog(module = "系统管理", action = "删除角色")
    @Operation(summary = "删除角色")
    @DeleteMapping("/roles/{roleId}")
    public Result<Void> deleteRole(@PathVariable Long roleId) {
        adminSystemService.deleteRole(roleId);
        return Result.ok();
    }

    @OperationLog(module = "系统管理", action = "查询权限列表", saveParams = false)
    @Operation(summary = "权限列表")
    @GetMapping("/permissions")
    public Result<List<Map<String, Object>>> getPermissionList() {
        return Result.ok(adminSystemService.getPermissionList());
    }

    // ==================== 系统配置 ====================

    @OperationLog(module = "系统管理", action = "查看系统配置", saveParams = false)
    @Operation(summary = "获取配置")
    @GetMapping("/config")
    public Result<Map<String, Object>> getConfig() {
        return Result.ok(adminSystemService.getConfig());
    }

    @OperationLog(module = "系统管理", action = "更新系统配置")
    @Operation(summary = "更新配置")
    @PutMapping("/config")
    public Result<Void> updateConfig(@RequestBody Map<String, Object> config) {
        adminSystemService.updateConfig(config);
        return Result.ok();
    }

    // ==================== 操作日志 ====================

    @OperationLog(module = "系统管理", action = "查询操作日志", saveParams = false)
    @Operation(summary = "操作日志")
    @GetMapping("/logs")
    public Result<PageResult<Map<String, Object>>> getLogs(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(adminSystemService.getLogs(operator, module, action, startDate, endDate, page, pageSize));
    }
}
