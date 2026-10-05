package com.itemll.flight_management_system_sp.pay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道查单结果
 * <p>用于「主动查单兜底」：异步回调不保证 100% 到达，当支付单长时间停在 PENDING 时，
 * 必须反查渠道侧的真实收款状态，否则会出现「用户已付款但订单仍挂着待支付」。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayQueryResult {

    /** 渠道侧是否已确认收款 */
    private boolean paid;

    /** 渠道侧订单是否已关闭（用户超时未付、渠道主动关单） */
    private boolean closed;

    /** 渠道交易号，付款成功后才有值 */
    private String channelTradeNo;

    /** 渠道原始状态，如支付宝的 TRADE_SUCCESS / WAIT_BUYER_PAY */
    private String rawStatus;

    /** 原始返回摘要，落库到 payment.callback_data 便于线上排查 */
    private String rawMessage;
}
