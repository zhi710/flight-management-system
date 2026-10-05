package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 机组人员 */
@Data
@TableName("crew")
public class Crew {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String crewId;
    private String name;
    private String gender;
    private String phone;
    private String email;
    private String department;
    private String status;
    /** 当前状态的截止时间：REST/GROUNDED/TRAINING 到期后由状态机自动推进；STANDBY 时为 null */
    private LocalDateTime statusUntil;
    /** 状态来源：OVER_LIMIT=超月飞行时限停飞、QUAL_INVALID=资质失效停飞、FLIGHT_REST=航班到达后强制休息 */
    private String statusReason;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
