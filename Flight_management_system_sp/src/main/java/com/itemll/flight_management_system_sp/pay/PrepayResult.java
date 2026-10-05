package com.itemll.flight_management_system_sp.pay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预下单结果
 * <p>渠道返回的「支付凭证」。业务层拿到后落库到 payment 表的 qr_code / pay_url 字段，
 * 前端把它们渲染成二维码供用户扫码。</p>
 * <p>注意：本对象只描述「怎么付钱」，不代表用户已付款 —— 是否已收款必须通过
 * {@link PaymentGateway#query} 或渠道异步回调确认。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepayResult {

    /** 渠道标识：MOCK / ALIPAY */
    private String channel;

    /** 二维码内容。支付宝当面付为 code_url，直接渲染即可；为空表示该渠道不走扫码 */
    private String qrCode;

    /** 收银台跳转链接，为空表示该渠道不走跳转 */
    private String payUrl;

    /** 渠道侧预下单号（用于对账排查），部分渠道预下单阶段为空 */
    private String channelTradeNo;
}
