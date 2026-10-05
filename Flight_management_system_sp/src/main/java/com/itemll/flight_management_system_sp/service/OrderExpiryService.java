package com.itemll.flight_management_system_sp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.entity.FlightCabin;
import com.itemll.flight_management_system_sp.entity.Order;
import com.itemll.flight_management_system_sp.entity.OrderPassenger;
import com.itemll.flight_management_system_sp.mapper.FlightCabinMapper;
import com.itemll.flight_management_system_sp.mapper.OrderMapper;
import com.itemll.flight_management_system_sp.mapper.OrderPassengerMapper;
import com.itemll.flight_management_system_sp.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单取消 / 超时过期服务
 *
 * <p><b>唯一的「取消待支付订单」实现。</b>把「条件取消订单 → 回退座位 → 关闭待支付单」
 * 收敛到一个方法里，供三条路径复用：</p>
 * <ol>
 *   <li><b>用户主动取消</b>：{@code OrderServiceImpl#cancelOrder}；</li>
 *   <li><b>懒过期</b>：用户查询订单详情/列表时顺手判定已超时的订单；</li>
 *   <li><b>定时清理</b>：{@link com.itemll.flight_management_system_sp.scheduler.OrderExpireScheduler} 每分钟批量扫描。</li>
 * </ol>
 *
 * <p><b>为什么必须有定时清理：</b>只有懒过期时，用户下单后关掉页面不再访问订单列表，
 * 这笔订单就永远不会被清理 —— 座位被永久占住（库存只减不增）、渠道侧订单也一直挂着。
 * 这与 PRD「15 分钟未支付自动取消订单，释放座位库存」的约定不符。</p>
 *
 * <p><b>为什么要独立成类：</b>懒过期由查询路径触发，定时任务从外部触发。
 * 如果把它留在 {@code OrderServiceImpl} 内部，自调用会绕过 Spring 事务代理，
 * 导致「取消订单」与「回退座位」不在同一事务中，可能出现订单已取消但座位没回退的中间态
 * （座位泄漏正是我们要修的问题，不能换个形式再引入一次）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpiryService {

    /** 订单状态：待支付 */
    private static final String STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
    /** 订单状态：已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";
    /** 超时自动取消原因 */
    private static final String TIMEOUT_CANCEL_REASON = "超过支付时限，系统自动取消";
    /** 单次批处理上限，避免首次上线时一次性把历史积压订单全部扫出来 */
    private static final int BATCH_LIMIT = 200;

    private final OrderMapper orderMapper;
    private final OrderPassengerMapper orderPassengerMapper;
    private final FlightCabinMapper flightCabinMapper;
    private final PaymentMapper paymentMapper;

    /**
     * 单笔懒过期判定：订单已超时则取消并回退座位。
     * <p>由查询详情/列表调用，因此必须幂等且未超时时零副作用。取消成功后会把内存中的
     * {@code order} 对象同步为已取消状态，保证调用方随后返回给前端的状态与数据库一致。</p>
     */
    @Transactional
    public void expireIfNeeded(Order order) {
        if (order == null
                || !STATUS_PENDING_PAYMENT.equals(order.getStatus())
                || order.getExpireTime() == null
                || !order.getExpireTime().isBefore(LocalDateTime.now())) {
            return;
        }
        doCancel(order, TIMEOUT_CANCEL_REASON);
    }

    /**
     * 批量扫描并取消所有已超时的待支付订单。
     *
     * @return 实际取消的订单数
     */
    @Transactional
    public int expireTimeoutOrders() {
        List<Order> expiredOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, STATUS_PENDING_PAYMENT)
                .lt(Order::getExpireTime, LocalDateTime.now())
                .orderByAsc(Order::getId)
                .last("LIMIT " + BATCH_LIMIT));

        if (expiredOrders.isEmpty()) {
            return 0;
        }

        int cancelled = 0;
        for (Order order : expiredOrders) {
            if (doCancel(order, TIMEOUT_CANCEL_REASON)) {
                cancelled++;
            }
        }

        log.info("订单超时清理完成: 扫描 {} 笔，实际取消 {} 笔", expiredOrders.size(), cancelled);
        return cancelled;
    }

    /**
     * 用户主动取消待支付订单，与超时取消共用同一套「条件取消 + 回退座位」逻辑。
     * <p>原实现是 {@code updateById} 后再回退座位，两次并发取消会各回退一次造成库存虚增；
     * 这里改为条件更新，只有真正把状态从待支付改成已取消的那次调用才回退座位。</p>
     *
     * @return true = 本次调用完成了取消；false = 订单已不是待支付状态
     */
    @Transactional
    public boolean cancelPendingOrder(Order order, String reason) {
        return doCancel(order, reason);
    }

    /**
     * 取消待支付订单并回退座位。
     *
     * @return true = 本次调用完成了取消（座位回退已执行）
     */
    private boolean doCancel(Order order, String reason) {
        // 条件更新兜住竞态：只有订单仍处于「待支付」时才取消成功。
        // 影响行数为 0 说明订单已被支付或被其它实例处理过，此时绝不能回退座位，否则库存虚增。
        int rows = orderMapper.cancelPending(order.getId(), reason);
        if (rows == 0) {
            log.debug("订单已不处于待支付状态，跳过取消: orderId={}, reason={}", order.getOrderId(), reason);
            return false;
        }

        // 同步内存对象，保证调用方（查询接口）返回给前端的状态与数据库一致
        order.setStatus(STATUS_CANCELLED);
        order.setCancelReason(reason);

        releaseSeats(order);
        closePendingPayments(order);

        log.info("订单已取消: orderId={}, reason={}", order.getOrderId(), reason);
        return true;
    }

    /**
     * 关闭该订单下仍处于待支付的支付单。
     * <p>订单都没了，支付单不该还停在「待支付」：既会让对账定时任务反复做无谓的渠道查单，
     * 也会让前端与管理端看到互相矛盾的订单/支付状态。
     * 用条件更新（仅 PENDING 可关闭），<b>不会影响已支付成功的单子</b>。</p>
     */
    private void closePendingPayments(Order order) {
        int closed = paymentMapper.closePendingByOrderId(order.getId());
        if (closed > 0) {
            log.info("已同步关闭该订单下 {} 笔待支付支付单: orderId={}", closed, order.getOrderId());
        }
    }

    /**
     * 回退订单占用的座位。
     * <p>回退数量按订单实际乘机人数计算，而不是订单条数 —— 一张订单可能包含多名旅客，
     * 下单时扣减的也是人数。</p>
     */
    private void releaseSeats(Order order) {
        int seats = countPassengers(order.getId());
        if (seats <= 0) {
            log.warn("订单没有乘机人记录，无法回退座位: orderId={}", order.getOrderId());
            return;
        }

        FlightCabin cabin = flightCabinMapper.selectOne(new LambdaQueryWrapper<FlightCabin>()
                .eq(FlightCabin::getFlightId, order.getFlightId())
                .eq(FlightCabin::getCabinClass, order.getCabinClass()));
        if (cabin == null) {
            log.warn("回退座位失败，舱位不存在: orderId={}, flightId={}, cabinClass={}",
                    order.getOrderId(), order.getFlightId(), order.getCabinClass());
            return;
        }

        flightCabinMapper.restoreSeats(cabin.getId(), seats);
        log.info("订单已回退座位: orderId={}, flightId={}, cabinClass={}, seats={}",
                order.getOrderId(), order.getFlightId(), order.getCabinClass(), seats);
    }

    /** 订单乘机人数，即下单时扣减的座位数 */
    private int countPassengers(Long orderId) {
        Long count = orderPassengerMapper.selectCount(
                new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, orderId));
        return count == null ? 0 : count.intValue();
    }
}
