package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 登机口占用（时段排班）：一个登机口可被多个航班在不同时段占用 */
@Data
@TableName("gate_assignment")
public class GateAssignment {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long gateId;
    private Long flightId;
    private LocalDate flightDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
