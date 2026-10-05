package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 投诉建议 */
@Data
@TableName("feedback")
public class Feedback {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String type;
    private Long orderId;
    private String content;
    private String contactPhone;
    private String attachments;
    private String status;
    private String reply;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
