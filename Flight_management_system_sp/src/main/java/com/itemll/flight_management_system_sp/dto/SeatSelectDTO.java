package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 选择座位请求 */
@Data
@Schema(description = "选择座位请求")
public class SeatSelectDTO {
    @NotBlank(message = "订单ID不能为空")
    @Schema(description = "订单ID")
    private String orderId;

    @NotNull(message = "旅客索引不能为空")
    @Schema(description = "旅客索引")
    private Integer passengerIndex;

    @NotNull(message = "排号不能为空")
    @Schema(description = "排号")
    private Integer row;

    @NotBlank(message = "列号不能为空")
    @Schema(description = "列号")
    private String column;
}
