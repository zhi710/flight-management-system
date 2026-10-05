package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 退票记录 */
@Data
@TableName("order_refund")
public class OrderRefund {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String refundId;
    private Long orderId;
    private String reason;
    private BigDecimal refundAmount;
    private BigDecimal refundFee;
    private String status;
    /** 渠道退款流水号：退款成功后回填，便于与渠道对账 */
    private String channelRefundNo;
    /** 退款成功时间 */
    private LocalDateTime refundTime;
    /** 退款失败原因：失败时留痕，管理端可据此重试，不再"静默没退" */
    private String failReason;
    private Long operatorId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
