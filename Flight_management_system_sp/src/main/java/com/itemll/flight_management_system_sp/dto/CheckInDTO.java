package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

/** 办理值机请求 */
@Data
@Schema(description = "办理值机请求")
public class CheckInDTO {
    @NotBlank(message = "订单ID不能为空")
    @Schema(description = "订单ID")
    private String orderId;

    @NotEmpty(message = "旅客列表不能为空")
    @Schema(description = "值机旅客列表")
    private List<CheckInPassenger> passengers;

    @Data
    @Schema(description = "值机旅客")
    public static class CheckInPassenger {
        private Integer passengerIndex;
        private Integer seatRow;
        private String seatColumn;
    }
}
