package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 取消值机请求 */
@Data
@Schema(description = "取消值机请求")
public class CancelCheckInDTO {
    @NotNull(message = "旅客索引不能为空")
    @Schema(description = "旅客索引")
    private Integer passengerIndex;
}
