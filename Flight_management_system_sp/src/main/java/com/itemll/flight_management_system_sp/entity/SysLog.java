package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 操作日志 */
@Data
@TableName("sys_log")
public class SysLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long operatorId;
    private String operatorName;
    private String module;
    private String action;
    private String target;
    private String detail;
    private String ip;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
