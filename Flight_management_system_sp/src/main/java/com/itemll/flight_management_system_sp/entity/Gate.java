package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 登机口（物理资源，不含占用信息） */
@Data
@TableName("gate")
public class Gate {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String gateCode;
    private String terminal;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
