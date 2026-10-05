package com.itemll.flight_management_system_sp.scheduler;

import com.itemll.flight_management_system_sp.service.OrderExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单超时清理定时任务
 *
 * <p>扫描并取消所有超过支付时限的待支付订单，同时回退其占用的座位库存。</p>
 *
 * <p><b>为什么需要它：</b>原实现只在「用户查询订单详情/列表」时做懒过期，
 * 于是有两类订单永远不会被清理 —— 用户下单后关掉页面不再回来的订单，
 * 以及下单后从未打开过订单页的用户。这些订单会永久占住座位（库存只减不增），
 * 与 PRD「15 分钟未支付自动取消订单，释放座位库存」的约定不符。
 * 懒过期保留（保证用户看到的状态是实时的），定时任务负责兜底。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpireScheduler {

    private final OrderExpiryService orderExpiryService;

    /** 每 60 秒清理一次，相对 15 分钟支付时限，这个频率足够且开销很小 */
    @Scheduled(fixedDelay = 60_000)
    public void expireTimeoutOrders() {
        try {
            orderExpiryService.expireTimeoutOrders();
        } catch (Exception e) {
            // 定时任务不能因为一次异常就中断后续调度
            log.error("订单超时清理任务执行异常", e);
        }
    }
}
