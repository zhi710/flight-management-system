package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("irregular_operation")
public class IrregularOperation {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long flightId;
    private String type;
    private String iataDelayCode;
    private String reason;
    private Integer delayMinutes;
    private String compensationType;
    private BigDecimal compensationAmount;
    private Integer autoRebooked;
    private Integer notifyPassengers;
    private String status;
    private Long operatorId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
