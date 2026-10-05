package com.itemll.flight_management_system_sp.pay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 渠道异步回调解析结果
 * <p>渠道回调是「钱」的入口，因此本对象的字段被刻意分成两层：</p>
 * <ul>
 *   <li>{@link #signatureValid} —— 报文真伪。为 {@code false} 时<b>其余所有字段都不可信</b>，
 *       调用方必须直接拒绝，不能因为 {@code paid=true} 就入账。</li>
 *   <li>其余字段 —— 报文内容。即使验签通过，调用方仍需自行核对
 *       {@link #paymentId}、{@link #amount}、{@link #appId} 与本地记录是否一致。</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackResult {

    /** 验签是否通过（唯一可信判据） */
    private boolean signatureValid;

    /** 渠道是否确认已收款 */
    private boolean paid;

    /** 商户支付单号（渠道的 out_trade_no），即本地 payment.payment_id */
    private String paymentId;

    /** 渠道交易号（渠道的 trade_no） */
    private String channelTradeNo;

    /** 渠道回传的实付金额，由调用方与支付单金额比对 */
    private BigDecimal amount;

    /** 渠道通知 id，用于幂等与排障 */
    private String notifyId;

    /** 渠道原始状态，如支付宝的 TRADE_SUCCESS / WAIT_BUYER_PAY */
    private String rawStatus;

    /** 渠道所属应用 id，调用方应校验与本应用一致，避免其它应用的合法回调被误采信 */
    private String appId;

    /** 原始报文摘要，落库到 payment.callback_data 便于线上排查 */
    private String rawMessage;

    /** 构造一个「验签未通过」的拒绝结果 */
    public static CallbackResult rejected(String reason) {
        return CallbackResult.builder()
                .signatureValid(false)
                .paid(false)
                .rawMessage(reason)
                .build();
    }
}
