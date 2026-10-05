package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 改签记录 */
@Data
@TableName("order_change")
public class OrderChange {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String changeId;
    private Long orderId;
    private Long originalFlightId;
    private Long newFlightId;
    private String originalCabinClass;
    private String newCabinClass;
    private Integer segmentIndex;
    private BigDecimal changeFee;
    private BigDecimal fareDiff;
    private BigDecimal totalFee;
    private String status;
    private Long operatorId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
