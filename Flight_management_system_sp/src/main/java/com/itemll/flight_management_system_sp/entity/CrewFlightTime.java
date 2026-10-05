package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("crew_flight_time")
public class CrewFlightTime {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long crewId;
    private LocalDate flightDate;
    private Integer flightHours;
    private Integer dutyHours;
    private Integer restRequired;
    private Long flightId;
    private String role;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
