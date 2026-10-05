package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 航班状态日志 */
@Data
@TableName("flight_status_log")
public class FlightStatusLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long flightId;
    private String oldStatus;
    private String newStatus;
    private String reason;
    private Long operatorId;
    private String operatorName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
