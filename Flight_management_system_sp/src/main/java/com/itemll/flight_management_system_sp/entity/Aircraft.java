package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 飞机（机队） */
@Data
@TableName("aircraft")
public class Aircraft {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String registration;
    private Long aircraftTypeId;
    private Long airlineId;
    private LocalDate manufactureDate;
    private String status;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
