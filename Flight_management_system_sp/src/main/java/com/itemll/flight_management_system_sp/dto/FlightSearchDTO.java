package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 航班搜索请求 */
@Data
@Schema(description = "航班搜索请求")
public class FlightSearchDTO {
    @NotBlank(message = "行程类型不能为空")
    @Schema(description = "行程类型 ONEWAY/ROUND/MULTI", example = "ONEWAY")
    private String tripType;

    @Schema(description = "出发城市/机场代码（改签搜索时可选）", example = "PEK")
    private String departure;

    @Schema(description = "到达城市/机场代码（改签搜索时可选）", example = "SHA")
    private String arrival;

    @Schema(description = "出发日期 YYYY-MM-DD，为空时查询该航线所有日期航班", example = "2026-06-10")
    private String departDate;

    @Schema(description = "返程日期（往返必填）")
    private String returnDate;

    @NotNull(message = "成人人数不能为空")
    @Schema(description = "成人人数（1-9）", example = "1")
    private Integer adults;

    @Schema(description = "儿童人数（0-9）", example = "0")
    private Integer children;

    @Schema(description = "婴儿人数（0-9）", example = "0")
    private Integer infants;

    @Schema(description = "舱位等级 ECONOMY/BUSINESS/FIRST", example = "ECONOMY")
    private String cabinClass;

    @Schema(description = "是否仅直飞", example = "false")
    private Boolean directOnly;

    @Schema(description = "排序方式 RECOMMEND/PRICE/TIME/DURATION")
    private String sortBy;

    @Schema(description = "排序方向 asc/desc")
    private String sortOrder;
}
