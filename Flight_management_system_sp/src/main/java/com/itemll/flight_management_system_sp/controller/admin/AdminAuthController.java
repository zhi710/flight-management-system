package com.itemll.flight_management_system_sp.controller.admin;

import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.dto.AdminLoginDTO;
import com.itemll.flight_management_system_sp.service.AdminAuthService;
import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端认证控制器
 */
@Tag(name = "管理端-认证", description = "管理员登录")
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @Operation(summary = "管理员登录")
    @OperationLog(module = "系统管理", action = "管理员登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody AdminLoginDTO dto) {
        return Result.ok(adminAuthService.login(dto));
    }

    @Operation(summary = "管理员登出")
    @OperationLog(module = "系统管理", action = "管理员登出")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("currentAdminId");
        adminAuthService.logout(adminId);
        return Result.ok();
    }
}
