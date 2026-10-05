package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 用户登录请求 */
@Data
@Schema(description = "用户登录请求")
public class LoginDTO {
    @NotBlank(message = "手机号不能为空")
    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "登录密码", example = "Abc123456")
    private String password;

    @Schema(description = "验证码（触发风控时必填）")
    private String captcha;
}
