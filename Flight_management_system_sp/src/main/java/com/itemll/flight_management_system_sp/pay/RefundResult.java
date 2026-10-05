package com.itemll.flight_management_system_sp.pay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道退款结果
 * <p>与 {@link PayQueryResult} 分开定义，因为退款成功与否的判断依据和查单不同
 * （退款看渠道受理结果，查单看交易状态），混用容易写错判断条件。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResult {

    /** 渠道是否受理退款成功 */
    private boolean success;

    /** 渠道交易号 */
    private String channelTradeNo;

    /** 渠道原始返回摘要，便于排查 */
    private String rawMessage;
}
