package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 用户注册请求 */
@Data
@Schema(description = "用户注册请求")
public class RegisterDTO {
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "短信验证码", example = "123456")
    private String smsCode;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度8-20位")
    @Schema(description = "密码（8-20位，含字母和数字）", example = "Abc123456")
    private String password;

    @Schema(description = "用户姓名", example = "张三")
    private String name;
}
