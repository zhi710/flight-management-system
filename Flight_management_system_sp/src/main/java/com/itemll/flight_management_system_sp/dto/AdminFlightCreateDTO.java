package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

/** 管理端创建航班请求 */
@Data
@Schema(description = "创建航班请求")
public class AdminFlightCreateDTO {
    @NotBlank(message = "航班号不能为空")
    @Schema(description = "航班号", example = "CA1234")
    private String flightNo;

    @NotBlank(message = "航班日期不能为空")
    @Schema(description = "航班日期", example = "2026-06-10")
    private String date;

    @Schema(description = "航线信息")
    private RouteDTO route;

    @Schema(description = "时刻信息")
    private ScheduleDTO schedule;

    @Schema(description = "机型信息")
    private AircraftDTO aircraft;

    @NotBlank(message = "航班类型不能为空")
    @Schema(description = "航班类型 DOMESTIC/INTERNATIONAL/REGIONAL")
    private String flightType;

    @Schema(description = "经停信息")
    private List<StopDTO> stops;

    @Schema(description = "航空公司ID")
    private Long airlineId;

    @Schema(description = "出发航站楼")
    private String departureTerminal;

    @Schema(description = "到达航站楼")
    private String arrivalTerminal;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "值机开放：起飞前多少小时，默认24")
    private Integer checkinOpenHours;

    @Schema(description = "值机关闭：起飞前多少分钟，默认30")
    private Integer checkinCloseMinutes;

    @Data
    public static class RouteDTO {
        private String departure;
        private String arrival;
    }

    @Data
    public static class ScheduleDTO {
        private String departureTime;
        private String arrivalTime;
    }

    @Data
    public static class AircraftDTO {
        private String type;
        private String registration;
    }

    @Data
    public static class StopDTO {
        private String airport;
        private String arrivalTime;
        private String departureTime;
    }
}
