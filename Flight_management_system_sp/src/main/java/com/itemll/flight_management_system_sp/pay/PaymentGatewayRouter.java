package com.itemll.flight_management_system_sp.pay;

import cn.hutool.core.util.StrUtil;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.config.PaymentProperties;
import com.itemll.flight_management_system_sp.pay.impl.MockPayGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 支付网关路由器
 * <p>业务层唯一的入口：给出「支付方式」，返回「用哪个渠道去收钱」。业务代码里不应出现
 * 任何 {@code if ("ALIPAY".equals(...))} 之类的渠道判断。</p>
 *
 * <p>路由规则：</p>
 * <ol>
 *   <li>{@code payment.mode=mock}（默认）：所有支付方式统一走演示网关，
 *       行为与改造前完全一致，保证无凭证环境仍可完整演示。</li>
 *   <li>{@code payment.mode=live}：在已装配的渠道中，找「支持该支付方式且凭证齐备」的实现。
 *       找不到时明确告知用户该支付方式暂未开通，而不是返回一个扫不出来的假二维码。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentGatewayRouter {

    private final PaymentProperties paymentProperties;
    private final MockPayGateway mockPayGateway;
    /** 所有渠道实现；演示网关的 supportedPayMethods() 为空集，因此不会在真实模式下被选中 */
    private final List<PaymentGateway> gateways;

    /**
     * 按支付方式路由到具体渠道。
     *
     * @param payMethod 支付方式（大小写、空格不敏感）
     * @throws BusinessException 该支付方式对应的渠道未启用或凭证未配置齐全
     */
    public PaymentGateway route(String payMethod) {
        if (paymentProperties.isMockMode()) {
            return mockPayGateway;
        }

        String normalized = PayMethods.normalize(payMethod);
        for (PaymentGateway gateway : gateways) {
            if (gateway.available() && gateway.supportedPayMethods().contains(normalized)) {
                log.debug("支付方式 {} 路由到渠道 {}", normalized, gateway.channel());
                return gateway;
            }
        }

        log.warn("支付方式 {} 无可用渠道（mode=live，已装配渠道 {} 个）", normalized, gateways.size());
        throw new BusinessException(ErrorCode.CONFLICT,
                "支付方式「" + PayMethods.labelOf(normalized) + "」暂未开通，请选择其他支付方式");
    }

    /** 当前是否为演示模式，业务层据此决定是否允许调用 simulate 接口 */
    public boolean isMockMode() {
        return paymentProperties.isMockMode();
    }

    /**
     * 按回调路径中的渠道名路由，用于处理第三方异步通知。
     *
     * <p><b>与 {@link #route} 的两点关键差异：</b></p>
     * <ol>
     *   <li><b>不受 mock/live 模式影响。</b>回调来自渠道侧，是否受理应由渠道自身的验签结果决定，
     *       而不是由本地模式开关决定 —— 否则模式配错会造成「真实付款被静默丢弃」。</li>
     *   <li><b>不检查 {@code available()}。</b>凭证缺失时也要走到渠道实现里，由它明确返回
     *       「无法验签」并留下日志，而不是让回调在路由阶段就无声失败。</li>
     * </ol>
     *
     * @param provider 回调地址中的渠道名，如 {@code alipay}
     * @throws BusinessException 渠道名未识别
     */
    public PaymentGateway routeByProvider(String provider) {
        if (StrUtil.isBlank(provider)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未指定支付渠道");
        }
        String name = provider.trim();
        for (PaymentGateway gateway : gateways) {
            if (gateway.channel().equalsIgnoreCase(name)) {
                return gateway;
            }
        }
        log.warn("收到未识别渠道的回调: provider={}, 已装配渠道={}", name, gateways.size());
        throw new BusinessException(ErrorCode.NOT_FOUND, "不支持的支付渠道：" + name);
    }
}
