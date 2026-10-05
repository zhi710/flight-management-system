package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

/** 退票请求 */
@Data
@Schema(description = "退票请求")
public class OrderRefundDTO {
    @NotBlank(message = "退票原因不能为空")
    @Schema(description = "退票原因")
    private String reason;

    @Schema(description = "退票旅客（部分退票时）")
    private List<Integer> passengers;
}
