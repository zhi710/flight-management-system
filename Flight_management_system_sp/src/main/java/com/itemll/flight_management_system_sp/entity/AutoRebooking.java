package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("auto_rebooking")
public class AutoRebooking {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long originalOrderId;
    private Long newFlightId;
    private String newCabinClass;
    private String passengerName;
    private String passengerIdType;
    private String passengerIdNumber;
    private BigDecimal fareDiff;
    private Integer autoProcess;
    private String status;
    private Long operatorId;
    private LocalDateTime executeTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
