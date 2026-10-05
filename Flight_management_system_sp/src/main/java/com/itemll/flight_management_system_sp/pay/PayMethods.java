package com.itemll.flight_management_system_sp.pay;

import java.util.Locale;

/**
 * 支付方式常量
 * <p>取值与前端 {@code Payment.vue} 的 payMethods 以及 payment 表的 pay_method 字段一一对应，
 * 是支付方式字符串的唯一来源，避免各处散落字面量。</p>
 */
public final class PayMethods {

    private PayMethods() {
    }

    /** 支付宝 */
    public static final String ALIPAY = "ALIPAY";
    /** 微信支付 */
    public static final String WECHAT = "WECHAT";
    /** 银行卡 */
    public static final String BANK_CARD = "BANK_CARD";
    /** 信用支付 */
    public static final String CREDIT = "CREDIT";

    /**
     * 归一化：去掉空白并转大写。
     * <p>前端的支付方式可能带空格或大小写不一致，入库前统一处理，避免同一渠道产生多条不同写法。</p>
     *
     * @return 归一化后的支付方式，入参为空时返回空串
     */
    public static String normalize(String payMethod) {
        return payMethod == null ? "" : payMethod.trim().toUpperCase(Locale.ROOT);
    }

    /** 中文标签，用于给用户的错误提示 */
    public static String labelOf(String payMethod) {
        return switch (normalize(payMethod)) {
            case ALIPAY -> "支付宝";
            case WECHAT -> "微信支付";
            case BANK_CARD -> "银行卡";
            case CREDIT -> "信用支付";
            default -> payMethod;
        };
    }
}
