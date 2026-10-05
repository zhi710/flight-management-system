package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 用户-角色关联 */
@Data
@TableName("sys_user_role")
public class UserRole {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long roleId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
