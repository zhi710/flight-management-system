package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 机组排班 */
@Data
@TableName("crew_schedule")
public class CrewSchedule {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long crewId;
    private Long flightId;
    private String role;
    private LocalDate scheduleDate;
    private String status;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
