package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 管理员登录请求 */
@Data
@Schema(description = "管理员登录请求")
public class AdminLoginDTO {
    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", example = "admin")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "Admin@123")
    private String password;

    @Schema(description = "多因素认证码")
    private String mfaCode;
}
