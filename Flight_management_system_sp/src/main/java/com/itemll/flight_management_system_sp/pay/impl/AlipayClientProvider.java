package com.itemll.flight_management_system_sp.pay.impl;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.config.PaymentProperties;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 支付宝客户端提供者（懒加载）
 *
 * <p><b>为什么不用 @Bean 直接装配：</b>支付宝客户端必须在凭证齐备时才能创建，如果做成
 * 启动期 Bean，则「开发机没填密钥」会直接导致整个应用启动失败 —— 而支付只是系统的一个模块，
 * 不应该阻塞演示与联调。因此改为首次真正调用支付宝时再创建，并在此刻做凭证校验：
 * 配置错误会在第一次支付时立刻暴露，且不影响应用启动。</p>
 *
 * <p>客户端创建后缓存复用。AlipayClient 官方说明是线程安全的，全局单例即可，
 * 不要每次请求都 new，否则会反复做证书解析，明显拖慢响应。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayClientProvider {

    private final PaymentProperties paymentProperties;

    private final Object lock = new Object();
    private volatile AlipayClient client;

    /**
     * 获取支付宝客户端（首次调用时创建）。
     *
     * @throws BusinessException 凭证未配置齐全
     */
    public AlipayClient get() {
        AlipayClient local = client;
        if (local == null) {
            synchronized (lock) {
                local = client;
                if (local == null) {
                    local = create();
                    client = local;
                }
            }
        }
        return local;
    }

    /**
     * 凭证是否齐备（只做配置检查，不触发客户端创建）。
     * <p>供 {@code AlipayGateway.available()} 使用，保证路由阶段能给出「未开通」提示
     * 而不是等到调接口时才抛异常。</p>
     */
    public boolean credentialsReady() {
        PaymentProperties.Alipay cfg = paymentProperties.getAlipay();
        return StrUtil.isNotBlank(cfg.getAppId())
                && StrUtil.isNotBlank(cfg.getGatewayUrl())
                && StrUtil.isNotBlank(cfg.getAppPrivateKey())
                && StrUtil.isNotBlank(cfg.getAlipayPublicKey());
    }

    private AlipayClient create() {
        PaymentProperties.Alipay cfg = paymentProperties.getAlipay();

        requireText(cfg.getAppId(), "payment.alipay.app-id", "沙箱应用的 APPID");
        requireText(cfg.getGatewayUrl(), "payment.alipay.gateway-url", "网关地址");
        requireText(cfg.getAppPrivateKey(), "payment.alipay.app-private-key", "应用私钥");
        requireText(cfg.getAlipayPublicKey(), "payment.alipay.alipay-public-key", "支付宝公钥");
        if (StrUtil.isBlank(cfg.getNotifyUrl())) {
            // 不阻断启动：本地演示可退化为「前端轮询 + 后端主动查单」，不依赖异步回调
            log.warn("未配置 payment.alipay.notify-url，支付宝异步回调将无法送达，"
                    + "支付结果只能依赖前端轮询触发的主动查单兜底");
        }

        AlipayClient created = new DefaultAlipayClient(
                cfg.getGatewayUrl(),
                cfg.getAppId(),
                normalizeKey(cfg.getAppPrivateKey()),
                "json",
                "UTF-8",
                normalizeKey(cfg.getAlipayPublicKey()),
                signType());

        log.info("支付宝客户端初始化完成: gateway={}, appId={}, notifyUrl={}",
                cfg.getGatewayUrl(), cfg.getAppId(), cfg.getNotifyUrl());
        return created;
    }

    /**
     * 支付宝公钥（已清洗 PEM 头尾与空白）。
     * <p>回调验签走的是 {@code AlipaySignature.rsaCheckV1}，它需要的是公钥字符串而不是
     * AlipayClient 实例，因此这里单独暴露一个访问器。公钥未配置时返回空串，
     * 由调用方决定如何拒绝，避免在访问器里抛异常导致异常类型泄漏到网关之外。</p>
     */
    public String alipayPublicKey() {
        return normalizeKey(paymentProperties.getAlipay().getAlipayPublicKey());
    }

    /** 签名算法，默认 RSA2 */
    public String signType() {
        return StrUtil.blankToDefault(paymentProperties.getAlipay().getSignType(), "RSA2");
    }

    private void requireText(String value, String configKey, String desc) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "支付渠道配置缺失：" + configKey + "（" + desc + "）未填写，请检查 application.yml");
        }
    }

    /**
     * 规范化密钥文本。
     * <p>密钥从开放平台复制出来时可能带 PEM 头尾（{@code -----BEGIN PRIVATE KEY-----}）
     * 和换行；SDK 需要的是纯 Base64 内容。这里统一清洗，避免因粘贴格式差异导致的签名失败 ——
     * 这类报错往往只提示「签名错误」，很难定位。</p>
     */
    private String normalizeKey(String raw) {
        return raw.replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
    }
}
