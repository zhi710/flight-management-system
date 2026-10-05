package com.itemll.flight_management_system_sp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.entity.Flight;
import com.itemll.flight_management_system_sp.entity.FlightCabin;
import com.itemll.flight_management_system_sp.entity.Order;
import com.itemll.flight_management_system_sp.entity.OrderChange;
import com.itemll.flight_management_system_sp.entity.Payment;
import com.itemll.flight_management_system_sp.mapper.FlightCabinMapper;
import com.itemll.flight_management_system_sp.mapper.FlightMapper;
import com.itemll.flight_management_system_sp.mapper.OrderChangeMapper;
import com.itemll.flight_management_system_sp.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 改签生效（结算）服务
 *
 * <p><b>为什么独立成类：</b>改签的"生效"有两个触发口 ——</p>
 * <ol>
 *   <li><b>无需补款</b>（手续费 + 差价 &le; 0）：后台审核通过后立即生效，
 *       由 {@code AdminTicketServiceImpl#approveChange} 调用；</li>
 *   <li><b>需要补款</b>（手续费 + 差价 &gt; 0）：旅客付款成功那一刻生效，
 *       由 {@code PaymentServiceImpl#markPaid} 识别 {@code biz_type=CHANGE} 后调用。</li>
 * </ol>
 * 两条路必须执行完全相同的动作，否则会出现"免补款的改签换了航班但没换座位"这类
 * 只在某种路径下才复现的脏数据。因此把它收敛成唯一实现。
 * <p>独立成类的另一个原因是<b>避免循环依赖</b>：{@code PaymentServiceImpl} 要调用它，
 * 而它不能再反向依赖支付服务。</p>
 *
 * <p><b>生效动作（顺序有讲究）：</b></p>
 * <ol>
 *   <li>先<b>扣减新航班库存</b> —— 失败直接抛异常回滚，此时还没动原航班的数据，
 *       不会出现"原行程已释放、新行程没订上"的空档；</li>
 *   <li>作废值机 + 释放原航班座位 + 回补原航班库存；</li>
 *   <li>改写订单的航班/舱位/票面金额；</li>
 *   <li>净额为负时向渠道退还差价；</li>
 *   <li>改签单置 {@code COMPLETED} 并通知旅客。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderChangeSettlementService {

    /** 改签单状态：待审核 */
    private static final String STATUS_PENDING = "PENDING";
    /** 改签单状态：待补款 */
    private static final String STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
    /** 改签单状态：已完成（已生效） */
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final OrderChangeMapper orderChangeMapper;
    private final OrderMapper orderMapper;
    private final FlightMapper flightMapper;
    private final FlightCabinMapper flightCabinMapper;
    private final SeatInventoryService seatInventoryService;
    private final PaymentRefundService paymentRefundService;
    private final NotificationService notificationService;

    /**
     * 让一笔审核通过的改签真正生效。幂等：已生效的直接返回 false。
     *
     * <p><b>为什么是 {@code REQUIRES_NEW}：</b>主要调用方
     * {@code PaymentServiceImpl#markPaid} 自己就在事务里。如果这里沿用外层事务，
     * 一旦生效过程抛出异常（比如新航班余票刚好被抢光），整个事务会被标记为
     * rollback-only，导致<b>已经收到的款没能记成 SUCCESS</b> ——
     * 旅客扣了钱、系统里却查不到入账，比"改签没生效"严重得多。
     * 独立事务让改签可以单独失败，支付入账照常提交，失败留给人工处理。</p>
     *
     * @param changeId 改签单号
     * @return true = 本次调用完成了生效动作；false = 此前已生效或状态不允许
     * @throws BusinessException 新航班余票不足、缺支付记录等
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean settle(String changeId) {
        OrderChange change = orderChangeMapper.selectOne(new LambdaQueryWrapper<OrderChange>()
                .eq(OrderChange::getChangeId, changeId));
        if (change == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "改签记录不存在");
        }
        if (STATUS_COMPLETED.equals(change.getStatus())) {
            log.info("改签已生效，跳过重复处理: changeId={}", changeId);
            return false;
        }
        if (!STATUS_PENDING_PAYMENT.equals(change.getStatus()) && !STATUS_PENDING.equals(change.getStatus())) {
            log.warn("改签单状态不允许生效，已跳过: changeId={}, status={}", changeId, change.getStatus());
            return false;
        }

        Order order = orderMapper.selectById(change.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "改签关联的订单不存在");
        }

        int passengerCount = seatInventoryService.countPassengers(order.getId());
        if (passengerCount <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单没有乘机人，无法完成改签");
        }

        Flight newFlight = flightMapper.selectById(change.getNewFlightId());
        if (newFlight == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "改签目标航班不存在");
        }
        FlightCabin newCabin = flightCabinMapper.selectOne(new LambdaQueryWrapper<FlightCabin>()
                .eq(FlightCabin::getFlightId, change.getNewFlightId())
                .eq(FlightCabin::getCabinClass, change.getNewCabinClass()));
        if (newCabin == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "改签目标航班不存在该舱位");
        }

        // ---------- ① 先扣新航班库存（失败即回滚，不动原行程） ----------
        seatInventoryService.deductCabinSeats(change.getNewFlightId(), change.getNewCabinClass(), passengerCount);

        // ---------- ② 作废值机、释放原航班座位、回补原航班库存 ----------
        seatInventoryService.voidCheckInAndReleaseSeats(order.getId(), order.getFlightId());
        seatInventoryService.restoreCabinSeats(
                change.getOriginalFlightId(), change.getOriginalCabinClass(), passengerCount);

        // ---------- ③ 改写订单的行程与票面金额 ----------
        String oldFlightNo = order.getFlightNo();
        order.setFlightId(change.getNewFlightId());
        order.setFlightNo(newFlight.getFlightNo());
        order.setCabinClass(change.getNewCabinClass());
        BigDecimal newFare = zeroIfNull(newCabin.getFare()).multiply(BigDecimal.valueOf(passengerCount));
        BigDecimal newTax = zeroIfNull(newCabin.getTax()).multiply(BigDecimal.valueOf(passengerCount));
        order.setFare(newFare);
        order.setTax(newTax);
        // 服务费与舱位无关，保持原值；票面金额按新舱位重算
        order.setTotalAmount(newFare.add(newTax).add(zeroIfNull(order.getServiceFee())));
        orderMapper.updateById(order);

        // ---------- ④ 净额为负 → 向渠道退还差价 ----------
        // 净额 = 改签手续费 + 差价。为负说明旅客不但不用补钱，我们还欠他钱。
        BigDecimal netAmount = zeroIfNull(change.getChangeFee()).add(zeroIfNull(change.getFareDiff()));
        String refundNote = "";
        if (netAmount.signum() < 0) {
            BigDecimal refundAmount = netAmount.negate();
            Payment payment = paymentRefundService.requireSuccessfulPayment(order.getId());
            paymentRefundService.refund(payment, refundAmount, "改签退差价");
            refundNote = "，差价 ¥" + refundAmount + " 已原路退回";
            log.info("改签退差价完成: changeId={}, orderId={}, amount={}",
                    changeId, order.getOrderId(), refundAmount);
        }

        // ---------- ⑤ 收口状态并通知 ----------
        change.setStatus(STATUS_COMPLETED);
        orderChangeMapper.updateById(change);

        log.info("改签已生效: changeId={}, orderId={}, {} -> {}, cabin {} -> {}, passengers={}",
                changeId, order.getOrderId(), oldFlightNo, newFlight.getFlightNo(),
                change.getOriginalCabinClass(), change.getNewCabinClass(), passengerCount);

        if (order.getUserId() != null) {
            notificationService.notifyPassenger(order.getUserId(),
                    "改签成功",
                    "您的行程已改签至 " + newFlight.getFlightNo() + "（"
                            + change.getNewCabinClass() + "），原座位已释放，请重新办理值机选座" + refundNote,
                    "CHANGE", order.getId());
        }
        return true;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
