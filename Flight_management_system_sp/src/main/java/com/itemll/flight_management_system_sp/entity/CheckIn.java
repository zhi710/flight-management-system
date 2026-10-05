package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 值机记录 */
@Data
@TableName("check_in")
public class CheckIn {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String checkinId;
    private Long orderId;
    private Long flightId;
    private Long userId;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
