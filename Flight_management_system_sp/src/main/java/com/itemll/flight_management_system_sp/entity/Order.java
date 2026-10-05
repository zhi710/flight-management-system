package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单 */
@Data
@TableName("t_order")
public class Order {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String orderId;
    private String orderNo;
    private String pnr;
    private Long userId;
    private Long flightId;
    private String flightNo;
    private String cabinClass;
    private String searchId;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String status;
    private BigDecimal fare;
    private BigDecimal tax;
    private BigDecimal serviceFee;
    private BigDecimal totalAmount;
    private LocalDateTime expireTime;
    private LocalDateTime paidTime;
    private String cancelReason;
    private Long rebookedFromId;
    private Integer ssrFlag;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
