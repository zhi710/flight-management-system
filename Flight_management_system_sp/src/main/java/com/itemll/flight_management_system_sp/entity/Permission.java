package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 权限 */
@Data
@TableName("sys_permission")
public class Permission {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String code;
    private String description;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
