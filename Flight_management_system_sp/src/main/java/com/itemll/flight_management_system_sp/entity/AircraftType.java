package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 机型 */
@Data
@TableName("aircraft_type")
public class AircraftType {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String code;
    private String name;
    private String manufacturer;
    private String seatLayout;
    private Integer totalSeats;
    private Integer rangeKm;
    private Integer cruiseSpeed;
    private Integer status;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
