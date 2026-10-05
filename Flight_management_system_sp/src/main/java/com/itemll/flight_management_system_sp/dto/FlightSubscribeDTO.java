package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

/** 航班动态订阅请求 */
@Data
@Schema(description = "航班动态订阅请求")
public class FlightSubscribeDTO {
    @NotBlank(message = "航班号不能为空")
    @Schema(description = "航班号", example = "CA1234")
    private String flightNo;

    @NotBlank(message = "日期不能为空")
    @Schema(description = "日期", example = "2026-06-10")
    private String date;

    @NotEmpty(message = "推送渠道不能为空")
    @Schema(description = "推送渠道", example = "[\"SMS\",\"EMAIL\"]")
    private List<String> channels;

    @NotEmpty(message = "订阅类型不能为空")
    @Schema(description = "订阅类型", example = "[\"DELAY\",\"GATE_CHANGE\"]")
    private List<String> types;
}
