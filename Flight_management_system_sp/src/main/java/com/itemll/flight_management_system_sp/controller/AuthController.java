package com.itemll.flight_management_system_sp.controller;

import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.common.util.ClientIpUtil;
import com.itemll.flight_management_system_sp.dto.*;
import com.itemll.flight_management_system_sp.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 * <p>处理用户注册、登录、验证码、Token 刷新等认证相关接口。</p>
 */
@Tag(name = "认证模块", description = "用户注册、登录、验证码、Token刷新")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(authService.register(dto));
    }

    @Operation(summary = "用户登录（手机号+密码）")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    @Operation(summary = "短信验证码登录")
    @PostMapping("/login/sms")
    public Result<Map<String, Object>> loginBySms(@Valid @RequestBody SmsLoginDTO dto) {
        return Result.ok(authService.loginBySms(dto.getPhone(), dto.getSmsCode()));
    }

    @Operation(summary = "发送短信验证码")
    @PostMapping("/sms/send")
    public Result<Map<String, Object>> sendSmsCode(@Valid @RequestBody SmsSendDTO dto,
                                                  HttpServletRequest request) {
        // 传入客户端 IP 用于「单 IP 每小时发送上限」，防止换手机号批量刷短信
        int expireIn = authService.sendSmsCode(dto.getPhone(), dto.getType(), ClientIpUtil.of(request));
        return Result.ok(Map.of("expireIn", expireIn));
    }

    @Operation(summary = "第三方登录（重定向）")
    @GetMapping("/oauth/{provider}")
    public Result<String> oauthLogin(@PathVariable String provider) {
        // 实际应重定向到第三方授权页面
        return Result.ok("请跳转到 " + provider + " 授权页面");
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/token/refresh")
    public Result<Map<String, Object>> refreshToken(@Valid @RequestBody TokenRefreshDTO dto) {
        return Result.ok(authService.refreshToken(dto.getRefreshToken()));
    }
}
