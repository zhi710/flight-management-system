package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 里程变动记录 */
@Data
@TableName("member_miles_record")
public class MemberMilesRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String type;
    private Integer miles;
    private String description;
    private Long orderId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
