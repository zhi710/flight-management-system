package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("airline_fare")
public class AirlineFare {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long airlineId;
    private String cabinClass;
    private BigDecimal fare;
    private BigDecimal tax;
    private String baggage;
    private String refundRule;
    private String changeRule;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
