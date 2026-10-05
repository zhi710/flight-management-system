package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ground_handling")
public class GroundHandling {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long flightId;
    private String nodeCode;
    private String nodeName;
    private LocalDateTime planTime;
    private LocalDateTime actualTime;
    private Integer delayMinutes;
    private String status;
    private Long operatorId;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
