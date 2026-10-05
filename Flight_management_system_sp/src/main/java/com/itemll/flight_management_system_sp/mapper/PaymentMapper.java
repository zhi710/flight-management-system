package com.itemll.flight_management_system_sp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itemll.flight_management_system_sp.entity.Payment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface PaymentMapper extends BaseMapper<Payment> {

    /**
     * 幂等入账：把支付单从 PENDING 推进到 SUCCESS
     * <p>这是「置为已支付」的<b>唯一入口</b>，用条件更新而不是先查后改，原因：
     * 支付平台的异步回调会重复投递，且回调、定时查单兜底、演示模拟三条路径可能并发触发。
     * 若用 {@code updateById} 先查后改，两个请求都查到 PENDING 就会重复入账。</p>
     *
     * @param paymentId     商户支付单号
     * @param transactionId 渠道交易号
     * @param callbackData  渠道原始报文摘要，便于线上排查
     * @return 影响行数。<b>0 表示该支付单已被其他路径处理过，调用方应直接返回成功，不要重复推进订单</b>
     */
    @Update("UPDATE payment SET status = 'SUCCESS', transaction_id = #{transactionId}, "
            + "callback_data = #{callbackData}, paid_time = NOW(), update_time = NOW() "
            + "WHERE payment_id = #{paymentId} AND status = 'PENDING' AND deleted = 0")
    int markSuccess(@Param("paymentId") String paymentId,
                    @Param("transactionId") String transactionId,
                    @Param("callbackData") String callbackData);

    /**
     * 关闭某笔订单下所有待支付的支付单。
     *
     * <p><b>场景：</b>订单超时被自动取消（或用户主动取消）时，必须把它挂着的支付单一并关闭。
     * 否则会留下「订单已取消、支付单还挂在待支付」的孤儿记录，带来两个问题：</p>
     * <ol>
     *   <li>对账定时任务会反复捞到它去渠道查单，属于无谓的渠道调用，堆积起来会白耗配额；</li>
     *   <li>语义上自相矛盾 —— 订单都没了，支付单却还显示「待支付」，前端与管理端看到的信息互相打架。</li>
     * </ol>
     *
     * <p>同样使用条件更新：只有仍处于 {@code PENDING} 的单子才会被关闭，
     * <b>绝不会把已支付成功的单子误改成已关闭</b>。影响行数为 0 说明该订单没有待支付单，属正常情况。</p>
     *
     * @param orderId 订单主键（{@code payment.order_id} 存的是数字主键，非业务单号）
     * @return 实际关闭的支付单数量
     */
    @Update("UPDATE payment SET status = 'CLOSED', update_time = NOW() "
            + "WHERE order_id = #{orderId} AND status = 'PENDING' AND deleted = 0")
    int closePendingByOrderId(@Param("orderId") Long orderId);

    /**
     * 关闭单笔待支付单（用于渠道查单确认「已关闭且未收款」后收敛状态）。
     *
     * <p><b>为什么需要它：</b>对账任务查到渠道侧已关单时，如果本地支付单仍留在
     * {@code PENDING}，下一轮对账还会把它捞出来再查一次 —— 形成每轮都调渠道、
     * 但状态永不推进的死循环（历史遗留的孤儿支付单会无限期空转，白耗渠道配额并刷屏日志）。
     * 这里把「渠道已确认收不到钱」的单子就地关闭，让它从对账结果集中退出。</p>
     *
     * <p>仍是条件更新：只有 {@code PENDING} 会被关闭，
     * <b>不会覆盖已经支付成功的单子</b>。</p>
     *
     * @param paymentId 商户支付单号
     * @return 影响行数。0 表示该单已被其它路径处理过（或已不是待支付），无需重复处理
     */
    @Update("UPDATE payment SET status = 'CLOSED', update_time = NOW() "
            + "WHERE payment_id = #{paymentId} AND status = 'PENDING' AND deleted = 0")
    int closePendingByPaymentId(@Param("paymentId") String paymentId);
}
