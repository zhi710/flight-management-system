package com.itemll.flight_management_system_sp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.entity.Payment;
import com.itemll.flight_management_system_sp.mapper.PaymentMapper;
import com.itemll.flight_management_system_sp.pay.PaymentGateway;
import com.itemll.flight_management_system_sp.pay.PaymentGatewayRouter;
import com.itemll.flight_management_system_sp.pay.RefundResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 退款出口服务
 *
 * <p><b>存在的意义：</b>{@link PaymentGateway#refund} 在改造前<b>全项目零调用方</b> ——
 * 网关里实现得整整齐齐，业务侧却从来没用过，导致"审核通过=钱到账"是假的。
 * 现在把「找到可退的支付单 → 路由到渠道 → 发起退款」收敛成本类，
 * 退票审核（{@code AdminTicketServiceImpl#approveRefund}）与
 * 改签退差价（{@link OrderChangeSettlementService}）共用同一份实现，
 * 不会出现"两条路各写一遍、其中一条忘了真退"。</p>
 *
 * <p><b>为什么找不到支付单要直接报错：</b>没有支付成功的记录就没有可退的渠道交易。
 * 这时如果"照常把状态改成已退款"就是伪造退款，是本次改造要消灭的行为。
 * 宁可让审核动作失败并留下明确提示，让人去查数据。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundService {

    /** 支付单业务类型：客票支付 */
    private static final String BIZ_TYPE_ORDER = "ORDER";
    /** 支付单状态：支付成功 */
    private static final String STATUS_SUCCESS = "SUCCESS";

    private final PaymentMapper paymentMapper;
    private final PaymentGatewayRouter paymentGatewayRouter;

    /**
     * 取该订单最近一笔「支付成功」的客票支付单。
     *
     * @throws BusinessException 订单没有成功支付记录（历史脏数据或数据不一致）
     */
    public Payment requireSuccessfulPayment(Long orderId) {
        Payment payment = findSuccessfulPayment(orderId);
        if (payment == null) {
            log.error("订单没有支付成功的支付记录，无法发起退款: orderId={}", orderId);
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该订单没有支付成功的支付记录，无法发起退款，请核对账务");
        }
        return payment;
    }

    /** 同上但不抛异常，供只做判断的场景使用 */
    public Payment findSuccessfulPayment(Long orderId) {
        return paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId)
                .eq(Payment::getBizType, BIZ_TYPE_ORDER)
                .eq(Payment::getStatus, STATUS_SUCCESS)
                .orderByDesc(Payment::getPaidTime)
                .last("LIMIT 1"));
    }

    /**
     * 向渠道发起退款。
     *
     * <p>渠道不受理时抛业务异常，由调用方决定语义：退票审核场景下异常会让整个审核事务回滚，
     * 状态留在"待审核"，管理员可以稍后重试 —— 这比"标记成已退款但钱没退"安全得多。</p>
     *
     * @param payment 原支付单
     * @param amount  退款金额；传 null 表示全额退款
     * @param reason  退款原因，会展示给用户
     * @throws BusinessException 渠道未开通、凭证缺失或渠道明确拒绝退款
     */
    public RefundResult refund(Payment payment, BigDecimal amount, String reason) {
        PaymentGateway gateway = paymentGatewayRouter.route(payment.getPayMethod());
        RefundResult result;
        try {
            result = gateway.refund(payment.getPaymentId(), amount, reason);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("发起退款异常: paymentId={}, amount={}, channel={}, err={}",
                    payment.getPaymentId(), amount, gateway.channel(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.PAYMENT_FAILED, "退款请求异常：" + e.getMessage());
        }

        if (result == null || !result.isSuccess()) {
            String detail = result == null ? "渠道无响应" : result.getRawMessage();
            log.warn("渠道退款未受理: paymentId={}, amount={}, channel={}, detail={}",
                    payment.getPaymentId(), amount, gateway.channel(), detail);
            throw new BusinessException(ErrorCode.PAYMENT_FAILED, "渠道退款失败：" + detail);
        }

        log.info("渠道退款成功: paymentId={}, amount={}, channel={}, channelTradeNo={}",
                payment.getPaymentId(), amount, gateway.channel(), result.getChannelTradeNo());
        return result;
    }
}
