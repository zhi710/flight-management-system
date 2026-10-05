package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

/** 改签请求 */
@Data
@Schema(description = "改签请求")
public class OrderChangeDTO {
    @Schema(description = "改签航段索引", example = "0")
    private Integer segmentIndex;

    @NotBlank(message = "新航班ID不能为空")
    @Schema(description = "新航班ID")
    private String newFlightId;

    @NotBlank(message = "新舱位等级不能为空")
    @Schema(description = "新舱位等级")
    private String newCabinClass;

    @Schema(description = "改签旅客（部分改签时）")
    private List<Integer> passengers;
}
