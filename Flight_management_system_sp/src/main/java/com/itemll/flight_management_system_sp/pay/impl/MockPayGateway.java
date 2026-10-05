package com.itemll.flight_management_system_sp.pay.impl;

import com.itemll.flight_management_system_sp.pay.CallbackResult;
import com.itemll.flight_management_system_sp.pay.PayMethods;
import com.itemll.flight_management_system_sp.pay.PayQueryResult;
import com.itemll.flight_management_system_sp.pay.PaymentGateway;
import com.itemll.flight_management_system_sp.pay.PrepayResult;
import com.itemll.flight_management_system_sp.pay.RefundResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * 演示支付网关（mock 模式的唯一实现）
 * <p>不发起任何外网请求，只生成一段可被前端渲染成二维码的模拟串。用户扫码无法付款，
 * 支付状态由 {@code POST /payments/{paymentId}/simulate} 推进 —— 这正是改造前的行为，
 * 迁移到本类后保证原有演示流程零改动可用。</p>
 *
 * <p>{@link #supportedPayMethods()} 返回空集是刻意设计：它不是任何一种真实支付方式的渠道，
 * 因此真实模式下的路由永远不会选中它，避免「配置漏填 → 静默走模拟 → 用户以为付了钱」。</p>
 */
@Slf4j
@Component
public class MockPayGateway implements PaymentGateway {

    /** 模拟码协议前缀，一眼可辨不是真实支付码 */
    private static final String MOCK_QR_SCHEME = "mockpay";
    private static final String MOCK_PAY_URL = "https://pay.example.com/";

    @Override
    public String channel() {
        return "MOCK";
    }

    @Override
    public Set<String> supportedPayMethods() {
        return Set.of();
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public PrepayResult prepay(String paymentId, String payMethod, BigDecimal amount, String subject) {
        String method = PayMethods.normalize(payMethod).toLowerCase();
        log.info("[演示支付] 预下单 paymentId={}, payMethod={}, amount={}, subject={}（未调用任何真实渠道）",
                paymentId, method, amount, subject);

        return PrepayResult.builder()
                .channel(channel())
                .qrCode(MOCK_QR_SCHEME + "://" + (method.isEmpty() ? "unknown" : method) + "/" + paymentId)
                .payUrl(MOCK_PAY_URL + paymentId)
                .build();
    }

    @Override
    public PayQueryResult query(String paymentId) {
        // 演示渠道没有真实交易，一律返回未支付，由 simulate 接口推进状态
        return PayQueryResult.builder()
                .paid(false)
                .closed(false)
                .rawStatus("MOCK_PENDING")
                .rawMessage("演示模式，无渠道交易")
                .build();
    }

    @Override
    public RefundResult refund(String paymentId, BigDecimal amount, String reason) {
        log.info("[演示支付] 退款 paymentId={}, amount={}, reason={}（未调用任何真实渠道）", paymentId, amount, reason);
        // 生成一个模拟渠道流水号：真实渠道退款会回传流水号用于对账，
        // 演示网关若不返回，退票记录里的 channel_refund_no 就是空的，
        // 「钱到底退到哪了」在演示里也无从体现（集成测试曾因此断言失败）。
        return RefundResult.builder()
                .success(true)
                .channelTradeNo("MOCKREF" + System.currentTimeMillis())
                .rawMessage("演示模式，退款直接置为成功")
                .build();
    }

    @Override
    public CallbackResult verifyCallback(Map<String, String> params) {
        // 演示渠道没有可验签的密钥体系，任何报文都无法证明真伪，因此一律拒绝。
        // 若这里放行，等于在演示环境开放了一个「POST 一下就免费出票」的接口。
        log.warn("[演示支付] 收到支付回调但演示渠道不接收任何真实回调，已拒绝");
        return CallbackResult.rejected("演示渠道不接收支付回调");
    }
}
