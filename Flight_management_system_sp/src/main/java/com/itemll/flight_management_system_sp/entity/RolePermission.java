package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 角色-权限关联 */
@Data
@TableName("sys_role_permission")
public class RolePermission {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long roleId;
    private Long permissionId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
