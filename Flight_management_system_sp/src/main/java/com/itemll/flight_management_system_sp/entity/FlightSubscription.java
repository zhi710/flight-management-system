package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 航班动态订阅 */
@Data
@TableName("flight_subscription")
public class FlightSubscription {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String flightNo;
    private LocalDate flightDate;
    private String channels;
    private String types;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
