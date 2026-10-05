package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 刷新 Token 请求 */
@Data
@Schema(description = "刷新Token请求")
public class TokenRefreshDTO {
    @NotBlank(message = "refreshToken不能为空")
    @Schema(description = "刷新令牌")
    private String refreshToken;
}
