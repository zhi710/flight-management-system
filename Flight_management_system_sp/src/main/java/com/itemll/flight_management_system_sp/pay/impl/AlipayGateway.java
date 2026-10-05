package com.itemll.flight_management_system_sp.pay.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayRequest;
import com.alipay.api.AlipayResponse;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.config.PaymentProperties;
import com.itemll.flight_management_system_sp.pay.CallbackResult;
import com.itemll.flight_management_system_sp.pay.PayMethods;
import com.itemll.flight_management_system_sp.pay.PayQueryResult;
import com.itemll.flight_management_system_sp.pay.PaymentGateway;
import com.itemll.flight_management_system_sp.pay.PrepayResult;
import com.itemll.flight_management_system_sp.pay.RefundResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;

/**
 * 支付宝渠道实现（当面付 · 扫码支付）
 *
 * <p>使用开放平台的三个接口，签名/验签全部由 SDK 完成，不手写 RSA2：</p>
 * <ul>
 *   <li>{@code alipay.trade.precreate} — 预下单，返回 {@code qr_code} 供前端渲染二维码</li>
 *   <li>{@code alipay.trade.query} — 主动查单，回调丢失时的兜底对账</li>
 *   <li>{@code alipay.trade.refund} — 退款</li>
 * </ul>
 *
 * <p><b>商户单号约定：</b>直接用 {@code payment.payment_id} 作为 {@code out_trade_no}。
 * 好处是回调与查单都不需要额外索引字段，且天然幂等（同一单号重复预下单返回同一份凭证）。</p>
 *
 * <p><b>金额规则：</b>一律从订单金额取值（调用方已校验一致性），并强制两位小数 ——
 * 支付宝对金额格式敏感，传入 {@code 1200.0} 之类会被拒绝。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayGateway implements PaymentGateway {

    /** 支付宝交易状态：支付成功 */
    private static final String TRADE_SUCCESS = "TRADE_SUCCESS";
    /** 支付宝交易状态：交易完成（不可退款） */
    private static final String TRADE_FINISHED = "TRADE_FINISHED";
    /** 支付宝交易状态：交易已关闭 */
    private static final String TRADE_CLOSED = "TRADE_CLOSED";
    /** 子错误码：交易不存在（用户从未扫码下单，属于正常情况而非故障） */
    private static final String SUB_CODE_TRADE_NOT_EXIST = "ACQ.TRADE_NOT_EXIST";
    /** 字符集，回调验签必须与渠道一致，否则必然验签失败 */
    private static final String CHARSET = "UTF-8";
    /** 订单标题最大长度 */
    private static final int SUBJECT_MAX_LENGTH = 256;

    private final AlipayClientProvider alipayClientProvider;
    private final PaymentProperties paymentProperties;

    @Override
    public String channel() {
        return "ALIPAY";
    }

    @Override
    public Set<String> supportedPayMethods() {
        return Set.of(PayMethods.ALIPAY);
    }

    @Override
    public boolean available() {
        return paymentProperties.getChannels().getAlipay().isEnabled()
                && alipayClientProvider.credentialsReady();
    }

    @Override
    public PrepayResult prepay(String paymentId, String payMethod, BigDecimal amount, String subject) {
        PaymentProperties.Alipay cfg = paymentProperties.getAlipay();

        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        if (StrUtil.isNotBlank(cfg.getNotifyUrl())) {
            request.setNotifyUrl(cfg.getNotifyUrl());
        }

        JSONObject biz = new JSONObject();
        biz.put("out_trade_no", paymentId);
        biz.put("total_amount", formatAmount(amount));
        biz.put("subject", buildSubject(subject));
        // 让渠道侧的订单超时与订单「15 分钟支付时限」保持一致，避免订单已取消但二维码仍可付款
        biz.put("timeout_express", paymentProperties.expireMinutesOrDefault() + "m");
        request.setBizContent(biz.toJSONString());

        AlipayTradePrecreateResponse response = execute(request, "预下单");
        if (!response.isSuccess() || StrUtil.isBlank(response.getQrCode())) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED,
                    "支付宝下单失败：" + friendlyMessage(response));
        }

        log.info("支付宝预下单成功: paymentId={}, amount={}", paymentId, formatAmount(amount));
        return PrepayResult.builder()
                .channel(channel())
                .qrCode(response.getQrCode())
                .channelTradeNo(response.getOutTradeNo())
                .build();
    }

    @Override
    public PayQueryResult query(String paymentId) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        JSONObject biz = new JSONObject();
        biz.put("out_trade_no", paymentId);
        request.setBizContent(biz.toJSONString());

        AlipayTradeQueryResponse response = execute(request, "查单");

        String tradeStatus = response.getTradeStatus();
        boolean notExist = SUB_CODE_TRADE_NOT_EXIST.equals(response.getSubCode());

        if (!response.isSuccess() && !notExist) {
            // 查单失败不能当作「未支付」静默吞掉，否则会把真问题掩盖成用户没付钱
            log.warn("支付宝查单未成功: paymentId={}, code={}, subCode={}, subMsg={}",
                    paymentId, response.getCode(), response.getSubCode(), response.getSubMsg());
        }

        boolean paid = TRADE_SUCCESS.equals(tradeStatus) || TRADE_FINISHED.equals(tradeStatus);
        boolean closed = TRADE_CLOSED.equals(tradeStatus) || notExist;

        return PayQueryResult.builder()
                .paid(paid)
                .closed(closed)
                .channelTradeNo(response.getTradeNo())
                .rawStatus(StrUtil.blankToDefault(tradeStatus, response.getCode()))
                .rawMessage(brief(response))
                .build();
    }

    @Override
    public RefundResult refund(String paymentId, BigDecimal amount, String reason) {
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();

        JSONObject biz = new JSONObject();
        biz.put("out_trade_no", paymentId);
        if (amount != null) {
            biz.put("refund_amount", formatAmount(amount));
        }
        biz.put("refund_reason", StrUtil.blankToDefault(reason, "用户申请退款"));
        request.setBizContent(biz.toJSONString());

        AlipayTradeRefundResponse response = execute(request, "退款");

        if (!response.isSuccess()) {
            log.warn("支付宝退款未成功: paymentId={}, subCode={}, subMsg={}",
                    paymentId, response.getSubCode(), response.getSubMsg());
        }
        return RefundResult.builder()
                .success(response.isSuccess())
                .channelTradeNo(response.getTradeNo())
                .rawMessage(brief(response))
                .build();
    }

    @Override
    public CallbackResult verifyCallback(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return CallbackResult.rejected("回调参数为空");
        }

        String notifyId = params.get("notify_id");
        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String tradeStatus = params.get("trade_status");
        String appId = params.get("app_id");

        // ---------- 第一步：验签。必须先于任何字段判断 ----------
        // 未验签就采信 trade_status，等于把回调地址变成一个「任何人 POST 一下就能把订单刷成已支付」的接口。
        String alipayPublicKey = alipayClientProvider.alipayPublicKey();
        if (StrUtil.isBlank(alipayPublicKey)) {
            return CallbackResult.rejected("未配置 payment.alipay.alipay-public-key，无法验签");
        }

        boolean signatureValid;
        try {
            signatureValid = AlipaySignature.rsaCheckV1(
                    params, alipayPublicKey, CHARSET, alipayClientProvider.signType());
        } catch (AlipayApiException e) {
            log.error("支付宝回调验签异常: notifyId={}, errCode={}, errMsg={}",
                    notifyId, e.getErrCode(), e.getErrMsg(), e);
            return CallbackResult.rejected("验签异常：" + e.getErrMsg());
        }

        if (!signatureValid) {
            log.warn("支付宝回调验签未通过，已拒绝: notifyId={}, outTradeNo={}", notifyId, outTradeNo);
            return CallbackResult.rejected("验签未通过");
        }

        // ---------- 第二步：应用归属校验 ----------
        // 验签只能证明「报文来自支付宝」，不能证明「这笔交易属于本应用」：
        // 同一开发者名下多个应用共用公钥体系，缺少这步会让其它应用的交易被记到本系统账上。
        String configuredAppId = paymentProperties.getAlipay().getAppId();
        if (StrUtil.isNotBlank(configuredAppId) && StrUtil.isNotBlank(appId)
                && !configuredAppId.equals(appId)) {
            log.warn("支付宝回调 appId 与本应用不符，已拒绝: 回调appId={}, 本应用appId={}, notifyId={}",
                    appId, configuredAppId, notifyId);
            return CallbackResult.rejected("appId 不匹配");
        }

        // ---------- 第三步：解析业务字段 ----------
        boolean paid = TRADE_SUCCESS.equals(tradeStatus) || TRADE_FINISHED.equals(tradeStatus);

        return CallbackResult.builder()
                .signatureValid(true)
                .paid(paid)
                .paymentId(outTradeNo)
                .channelTradeNo(tradeNo)
                .amount(parseAmount(params.get("total_amount")))
                .notifyId(notifyId)
                .rawStatus(tradeStatus)
                .appId(appId)
                .rawMessage(callbackBrief(params))
                .build();
    }

    // ==================== 内部工具 ====================

    /**
     * 解析回调金额。
     * <p>金额格式非法时返回 {@code null} 而不是抛异常：业务层对 {@code null} 的处理是
     * 「无法核对」，会按拒绝入账处理，比直接 500 更可控。</p>
     */
    private BigDecimal parseAmount(String rawAmount) {
        if (StrUtil.isBlank(rawAmount)) {
            return null;
        }
        try {
            return new BigDecimal(rawAmount.trim());
        } catch (NumberFormatException e) {
            log.warn("支付宝回调金额格式非法: total_amount={}", rawAmount);
            return null;
        }
    }

    /** 回调报文摘要：只保留对账必需字段，避免把整包报文（含买家信息）落库 */
    private String callbackBrief(Map<String, String> params) {
        return StrUtil.maxLength(StrUtil.format(
                "notifyId={}, outTradeNo={}, tradeNo={}, tradeStatus={}, totalAmount={}, appId={}",
                params.get("notify_id"), params.get("out_trade_no"), params.get("trade_no"),
                params.get("trade_status"), params.get("total_amount"), params.get("app_id")), 500);
    }

    /**
     * 统一执行入口：把 SDK 异常归一成业务异常。
     * <p>第三方异常类型不应泄漏到上层业务代码，否则业务层会被迫依赖支付宝 SDK 的异常体系。</p>
     */
    private <T extends AlipayResponse> T execute(AlipayRequest<T> request, String action) {
        try {
            return alipayClientProvider.get().execute(request);
        } catch (AlipayApiException e) {
            log.error("支付宝[{}]调用异常: errCode={}, errMsg={}", action, e.getErrCode(), e.getErrMsg(), e);
            throw new BusinessException(ErrorCode.PAYMENT_FAILED,
                    "支付渠道调用失败（" + action + "），请稍后重试");
        }
    }

    /** 金额格式化为两位小数字符串，支付宝对格式敏感 */
    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "支付金额不能为空");
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 订单标题：未传时用配置前缀兜底，并截断到渠道允许的长度 */
    private String buildSubject(String subject) {
        String prefix = paymentProperties.getAlipay().getSubjectPrefix();
        String finalSubject = StrUtil.isNotBlank(subject) ? subject : StrUtil.blankToDefault(prefix, "航班订单");
        return StrUtil.maxLength(finalSubject, SUBJECT_MAX_LENGTH);
    }

    /** 给用户看的错误信息：优先子错误描述，其次主错误描述 */
    private String friendlyMessage(AlipayResponse response) {
        return StrUtil.blankToDefault(response.getSubMsg(), response.getMsg());
    }

    /** 原始返回摘要，用于落库排查（截断避免撑爆字段） */
    private String brief(AlipayResponse response) {
        return StrUtil.maxLength(StrUtil.format("code={}, subCode={}, subMsg={}, tradeStatus={}",
                response.getCode(), response.getSubCode(), response.getSubMsg(),
                response instanceof AlipayTradeQueryResponse q ? q.getTradeStatus() : "-"), 500);
    }
}
