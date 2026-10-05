package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notification_log")
public class NotificationLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long targetUserId;
    private Long targetAdminId;
    private String targetPhone;
    private String targetEmail;
    private String channel;
    private String templateCode;
    private String title;
    private String content;
    private String status;
    private LocalDateTime sendTime;
    private LocalDateTime readTime;
    private String errorMsg;
    private String refType;
    private Long refId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
