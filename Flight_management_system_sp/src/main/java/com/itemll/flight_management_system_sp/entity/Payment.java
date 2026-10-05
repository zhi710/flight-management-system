package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 支付记录 */
@Data
@TableName("payment")
public class Payment {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String paymentId;
    private Long orderId;
    private String payMethod;
    /**
     * 业务类型：{@code ORDER}=客票支付 / {@code CHANGE}=改签补款。
     * <p>改签补款复用了整条支付链路（预下单 → 扫码 → 轮询/回调 → 入账），
     * 入账时必须靠它分流：客票支付推进订单状态，改签补款推进改签单并让改签生效。</p>
     */
    private String bizType;
    /** 业务引用：{@code bizType=CHANGE} 时存 {@code order_change.change_id} */
    private String bizRef;
    private BigDecimal amount;
    private String status;
    private String transactionId;
    private String payUrl;
    private String qrCode;
    private LocalDateTime expireTime;
    private LocalDateTime paidTime;
    private String callbackData;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
