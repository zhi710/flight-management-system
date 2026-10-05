package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 航班舱位
 * <p>核心并发控制实体：available_seats + version 字段配合实现乐观锁扣减。</p>
 */
@Data
@TableName("flight_cabin")
public class FlightCabin {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long flightId;
    private String cabinClass;
    private String cabinName;
    private BigDecimal fare;
    private BigDecimal tax;
    private BigDecimal totalPrice;
    private Integer totalSeats;
    /** 可用座位数（并发扣减字段） */
    private Integer availableSeats;
    private String baggage;
    private String refundRule;
    private String changeRule;
    private Integer advancePurchaseDays;
    private java.time.LocalDate blackoutStart;
    private java.time.LocalDate blackoutEnd;
    private java.math.BigDecimal corporateFare;
    /** 乐观锁版本号 */
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
