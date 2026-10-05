package com.itemll.flight_management_system_sp.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.entity.Payment;
import com.itemll.flight_management_system_sp.mapper.PaymentMapper;
import com.itemll.flight_management_system_sp.pay.PayQueryResult;
import com.itemll.flight_management_system_sp.pay.PaymentGateway;
import com.itemll.flight_management_system_sp.pay.PaymentGatewayRouter;
import com.itemll.flight_management_system_sp.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付查单兜底定时任务
 *
 * <p><b>为什么必须有这条路径：</b>渠道的异步回调不保证 100% 到达 —— 网络抖动、我方发布重启、
 * notify_url 配置变更、渠道侧重推窗口内我方一直 500，都会导致回调丢失。回调一旦丢失，
 * 若不主动反查，用户付了钱而订单永远停在待支付，这是线上最典型的资金类故障。</p>
 *
 * <p><b>两条兜底路径的分工：</b></p>
 * <ul>
 *   <li><b>轮询触发</b>（{@code PaymentServiceImpl#getPaymentStatus}）：用户在支付页未离开时，
 *       前端每 3 秒轮询会顺带触发查单，付款后几秒内即可自动跳转。</li>
 *   <li><b>本定时任务</b>：覆盖「用户付完款直接关了页面」的情况 —— 此时没有任何轮询，
 *       只能靠定时反查。两者都收敛到 {@code PaymentService#markPaid} 做幂等入账。</li>
 * </ul>
 *
 * <p>入账统一走 {@code PaymentService} 的代理方法，每笔一个独立事务：
 * 这样某一笔对账失败不会影响同批其它支付单，也不会留下「支付单已改、订单没改」的中间态。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconcileScheduler {

    /** 支付单状态：待支付 */
    private static final String STATUS_PENDING = "PENDING";
    /** 单次对账的支付单上限，防止积压时一次性打爆渠道接口 */
    private static final int BATCH_LIMIT = 50;
    /**
     * 只对账创建满 2 分钟的待支付单。
     * <p>刚创建的支付单用户还在扫码，此时回调「还没来」是正常现象，频繁查单纯属浪费渠道配额；
     * 留 2 分钟缓冲既能让绝大多数回调自然到达，又能把丢失回调的补单延迟控制在几分钟内。</p>
     */
    private static final int MIN_AGE_MINUTES = 2;

    private final PaymentMapper paymentMapper;
    private final PaymentGatewayRouter paymentGatewayRouter;
    private final PaymentService paymentService;

    /** 每 60 秒对账一次 */
    @Scheduled(fixedDelay = 60_000)
    public void reconcilePendingPayments() {
        // 演示模式没有真实渠道，查单没有意义
        if (paymentGatewayRouter.isMockMode()) {
            return;
        }

        List<Payment> candidates;
        try {
            candidates = paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                    .eq(Payment::getStatus, STATUS_PENDING)
                    .lt(Payment::getCreateTime, LocalDateTime.now().minusMinutes(MIN_AGE_MINUTES))
                    .orderByAsc(Payment::getId)
                    .last("LIMIT " + BATCH_LIMIT));
        } catch (Exception e) {
            log.error("支付对账任务查询待对账支付单失败", e);
            return;
        }

        if (candidates.isEmpty()) {
            return;
        }

        int fixed = 0;
        for (Payment payment : candidates) {
            if (reconcileOne(payment)) {
                fixed++;
            }
        }

        if (fixed > 0) {
            log.warn("支付对账补单完成: 扫描 {} 笔待支付，其中 {} 笔渠道实际已收款（异步回调此前丢失）",
                    candidates.size(), fixed);
        }
    }

    /**
     * 对账单笔支付。
     *
     * @return true = 本次确实补单入账
     */
    private boolean reconcileOne(Payment payment) {
        PaymentGateway gateway;
        try {
            gateway = paymentGatewayRouter.route(payment.getPayMethod());
        } catch (BusinessException e) {
            // 渠道未开通或凭证缺失属于配置问题，长时间存在说明部署有误，用 debug 避免刷屏
            log.debug("支付对账跳过（渠道不可用）: paymentId={}, reason={}",
                    payment.getPaymentId(), e.getMessage());
            return false;
        }

        PayQueryResult queryResult;
        try {
            queryResult = gateway.query(payment.getPaymentId());
        } catch (Exception e) {
            // 查单失败不等于未付款，绝不能据此关闭支付单
            log.warn("支付对账查单失败，本轮跳过: paymentId={}, err={}",
                    payment.getPaymentId(), e.getMessage());
            return false;
        }

        if (!queryResult.isPaid()) {
            if (queryResult.isClosed()) {
                // 渠道侧已明确关单（用户超时未付 / 交易不存在），这笔钱不可能再收到。
                // 关键：必须把本地支付单就地关闭，否则下一轮对账又会捞到它再查一次 ——
                // 每 60 秒一次、永不停止的无效渠道调用（历史孤儿支付单正是这样无限空转的，
                // 日志刷屏且白耗渠道配额）。
                //
                // 只在我方支付单也已过期时才关：仍在有效期内的单子即便渠道短暂返回「已关闭」，
                // 也应留给用户继续支付的机会 —— 一旦提前关闭，用户随后付款成功时
                // markSuccess 会因状态不再是 PENDING 而无法入账（钱收了、订单没确认）。
                if (isExpired(payment)) {
                    int closed = paymentMapper.closePendingByPaymentId(payment.getPaymentId());
                    if (closed > 0) {
                        log.info("支付对账收敛过期未付款的支付单: paymentId={}, rawStatus={}, expireTime={}",
                                payment.getPaymentId(), queryResult.getRawStatus(), payment.getExpireTime());
                    }
                } else {
                    log.debug("渠道侧已关单但支付单仍在有效期内，暂不关闭: paymentId={}, rawStatus={}",
                            payment.getPaymentId(), queryResult.getRawStatus());
                }
            }
            return false;
        }

        boolean marked = paymentService.markPaid(payment.getPaymentId(),
                queryResult.getChannelTradeNo(), queryResult.getRawMessage());
        if (marked) {
            log.warn("支付对账补单成功（该笔支付的异步回调此前丢失，请检查 notify-url 是否可达）: "
                            + "paymentId={}, orderId={}, amount={}",
                    payment.getPaymentId(), payment.getOrderId(), payment.getAmount());
        }
        return marked;
    }

    /**
     * 支付单是否已过有效期（即对应订单的支付时限已过）。
     * <p>过期与否是对账能否「就地关闭」的判据：过期 = 用户已无支付入口，可安全收敛；
     * 未过期 = 用户仍可能正在付款，只能继续观察，不能关单。</p>
     */
    private boolean isExpired(Payment payment) {
        return payment.getExpireTime() != null
                && payment.getExpireTime().isBefore(LocalDateTime.now());
    }
}
