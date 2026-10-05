package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 旅客端用户 */
@Data
@TableName("sys_user")
public class User {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String userId;
    private String phone;
    private String password;
    private String name;
    private String gender;
    private LocalDate birthday;
    private String email;
    private String avatar;
    /** 实名证件类型，NULL 表示未实名 */
    private String idType;
    /** 实名证件号，NULL 表示未实名 */
    private String idNumber;
    /** 实名认证时间 */
    private LocalDateTime realnameTime;
    private String memberLevel;
    private String memberNo;
    private Integer miles;
    private Integer status;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
