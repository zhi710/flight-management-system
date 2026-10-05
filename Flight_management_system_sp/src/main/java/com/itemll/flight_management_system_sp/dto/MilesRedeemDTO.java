package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 里程兑换请求 */
@Data
@Schema(description = "里程兑换请求")
public class MilesRedeemDTO {
    @NotBlank(message = "兑换类型不能为空")
    @Schema(description = "兑换类型 TICKET/SERVICE")
    private String type;

    @NotBlank(message = "兑换目标ID不能为空")
    @Schema(description = "兑换目标ID")
    private String targetId;

    @NotNull(message = "里程数不能为空")
    @Schema(description = "使用里程数")
    private Integer miles;
}
