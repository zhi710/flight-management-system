package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 发送短信验证码请求 */
@Data
@Schema(description = "发送短信验证码请求")
public class SmsSendDTO {
    @NotBlank(message = "手机号不能为空")
    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @NotBlank(message = "类型不能为空")
    @Schema(description = "验证码用途 register/login/resetPassword", example = "register")
    private String type;
}
