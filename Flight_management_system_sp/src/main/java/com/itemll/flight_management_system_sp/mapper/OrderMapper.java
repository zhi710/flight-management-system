package com.itemll.flight_management_system_sp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itemll.flight_management_system_sp.entity.Order;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 幂等推进订单为「已支付」
     * <p>同样使用条件更新，且条件里带上 {@code status = 'PENDING_PAYMENT'}。
     * 这样能挡住两种真实存在的竞态：</p>
     * <ol>
     *   <li>回调重复投递 —— 第二次更新影响行数为 0，不会重复触发后续动作；</li>
     *   <li>「支付成功」与「超时自动取消」同时发生 —— 谁先落地谁生效，
     *       不会出现已付款订单被过期任务改成 CANCELLED。</li>
     * </ol>
     *
     * @param orderId 订单主键
     * @return 影响行数，0 表示订单不在待支付状态（已支付 / 已取消 / 已过期）
     */
    @Update("UPDATE t_order SET status = 'PAID', paid_time = NOW(), update_time = NOW() "
            + "WHERE id = #{orderId} AND status = 'PENDING_PAYMENT' AND deleted = 0")
    int markPaid(@Param("orderId") Long orderId);

    /**
     * 把待支付订单取消（用户主动取消 / 支付超时自动取消共用一个入口）
     * <p>同样使用条件更新，条件里带 {@code status = 'PENDING_PAYMENT'}，用于解决
     * 「支付成功」与「超时取消」并发时的竞态：两者都只会在订单仍为待支付时生效，
     * 谁先落地谁赢。这一点对座位回退尤其重要 —— 若用 {@code updateById} 先查后改，
     * 两个并发请求都会认为「我把订单取消了」，从而重复把座位加回库存，造成库存虚增。</p>
     *
     * @param orderId 订单主键
     * @param reason  取消原因，落库到 order.cancel_reason
     * @return 影响行数，0 表示订单不在待支付状态（已支付 / 已取消），调用方<b>不得回退座位</b>
     */
    @Update("UPDATE t_order SET status = 'CANCELLED', cancel_reason = #{reason}, update_time = NOW() "
            + "WHERE id = #{orderId} AND status = 'PENDING_PAYMENT' AND deleted = 0")
    int cancelPending(@Param("orderId") Long orderId, @Param("reason") String reason);
}
