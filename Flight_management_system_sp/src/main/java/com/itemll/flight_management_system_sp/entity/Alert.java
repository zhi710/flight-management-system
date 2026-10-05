package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 告警 */
@Data
@TableName("alert")
public class Alert {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String level;
    private String type;
    private String title;
    private String content;
    private String status;
    private LocalDateTime resolveTime;
    private Long resolverId;
    private Long refId;
    private String refType;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
