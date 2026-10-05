package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 短信验证码登录请求 */
@Data
@Schema(description = "短信验证码登录请求")
public class SmsLoginDTO {
    @NotBlank(message = "手机号不能为空")
    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "短信验证码", example = "123456")
    private String smsCode;
}
