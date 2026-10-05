package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 机组资质 */
@Data
@TableName("crew_qualification")
public class CrewQualification {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long crewId;
    private String type;
    private String name;
    private String number;
    private String level;
    private LocalDate issueDate;
    private LocalDate expireDate;
    private String status;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
