package com.itemll.flight_management_system_sp;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.dto.OrderChangeDTO;
import com.itemll.flight_management_system_sp.dto.OrderRefundDTO;
import com.itemll.flight_management_system_sp.entity.BoardingPass;
import com.itemll.flight_management_system_sp.entity.CheckIn;
import com.itemll.flight_management_system_sp.entity.Flight;
import com.itemll.flight_management_system_sp.entity.FlightCabin;
import com.itemll.flight_management_system_sp.entity.Order;
import com.itemll.flight_management_system_sp.entity.OrderChange;
import com.itemll.flight_management_system_sp.entity.OrderPassenger;
import com.itemll.flight_management_system_sp.entity.OrderRefund;
import com.itemll.flight_management_system_sp.entity.Payment;
import com.itemll.flight_management_system_sp.entity.Seat;
import com.itemll.flight_management_system_sp.mapper.BoardingPassMapper;
import com.itemll.flight_management_system_sp.mapper.CheckInMapper;
import com.itemll.flight_management_system_sp.mapper.FlightCabinMapper;
import com.itemll.flight_management_system_sp.mapper.FlightMapper;
import com.itemll.flight_management_system_sp.mapper.OrderChangeMapper;
import com.itemll.flight_management_system_sp.mapper.OrderMapper;
import com.itemll.flight_management_system_sp.mapper.OrderPassengerMapper;
import com.itemll.flight_management_system_sp.mapper.OrderRefundMapper;
import com.itemll.flight_management_system_sp.mapper.PaymentMapper;
import com.itemll.flight_management_system_sp.mapper.SeatMapper;
import com.itemll.flight_management_system_sp.service.AdminTicketService;
import com.itemll.flight_management_system_sp.service.FeeRuleService;
import com.itemll.flight_management_system_sp.service.OrderService;
import com.itemll.flight_management_system_sp.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 退改资金闭环集成测试
 *
 * <p><b>为什么要有这个类：</b>退票与改签是「钱 + 库存 + 座位 + 值机」四处状态同时变更的链路，
 * 也是本项目历史上最薄的一块（改造前 {@code PaymentGateway.refund} 全项目零调用方、
 * 改签差价恒为 0）。这类跨表一致性问题单测覆盖不了，必须跑真实数据库。</p>
 *
 * <p><b>三个关键的测试设计决策：</b></p>
 * <ol>
 *   <li><b>不用 {@code @Transactional} 回滚。</b>被验证的
 *       {@code OrderChangeSettlementService#settle} 自身是
 *       {@code Propagation.REQUIRES_NEW}（为了让改签生效失败不影响支付入账），
 *       独立事务<b>看不到外层未提交的数据</b>，套上测试事务会导致 settle 查不到订单而失败。
 *       因此改为「造数据 → 跑业务 → {@code @AfterEach} 物理删除」。</li>
 *   <li><b>物理删除而不是逻辑删除。</b>{@code Order} 带 {@code @TableLogic}，
 *       走 Mapper 的 delete 只会把 {@code deleted} 置 1，跑几十次就在库里堆一堆垃圾行。
 *       清理统一走 JdbcTemplate 发原生 DELETE。</li>
 *   <li><b>专供测试的航班。</b>不借用种子航班，避免测试改动真实航班的库存与座位状态。</li>
 * </ol>
 *
 * <p>运行：{@code ./mvnw test -Dtest=RefundChangeSettlementTests}</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        // 退款的渠道调用走演示网关（MockPayGateway#refund 直接返回成功）。
        // 生产配置是 live，若沿用真实配置这里会真的去打支付宝沙箱，既慢又不可重复。
        "payment.mode=mock"
})
class RefundChangeSettlementTests {

    /** 库中已有的实名旅客账号（张三），订单归属校验需要它 */
    private static final Long TEST_USER_ID = 1001L;
    private static final String CABIN_ECONOMY = "ECONOMY";
    private static final String CABIN_BUSINESS = "BUSINESS";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderMapper orderMapper;
    @Autowired private OrderPassengerMapper orderPassengerMapper;
    @Autowired private OrderChangeMapper orderChangeMapper;
    @Autowired private OrderRefundMapper orderRefundMapper;
    @Autowired private PaymentMapper paymentMapper;
    @Autowired private FlightMapper flightMapper;
    @Autowired private FlightCabinMapper flightCabinMapper;
    @Autowired private SeatMapper seatMapper;
    @Autowired private CheckInMapper checkInMapper;
    @Autowired private BoardingPassMapper boardingPassMapper;
    @Autowired private OrderService orderService;
    @Autowired private AdminTicketService adminTicketService;
    @Autowired private PaymentService paymentService;
    @Autowired private FeeRuleService feeRuleService;

    private Flight flight;
    private FlightCabin economy;
    private FlightCabin business;
    /** 本用例创建过的订单主键，供清理使用 */
    private final List<Long> createdOrderIds = new ArrayList<>();

    // ==================== 夹具 ====================

    @BeforeEach
    void setUp() {
        // 起飞时间放到 30 小时后 —— 落在 before24h 档，确保退改都被规则允许（手续费不是 100%）
        LocalDateTime departure = LocalDateTime.now().plusHours(30);
        flight = new Flight();
        flight.setFlightNo("TT" + (System.nanoTime() % 100000));
        flight.setFlightDate(LocalDate.now());
        flight.setDepartureAirport("PEK");
        flight.setArrivalAirport("SHA");
        flight.setDepartureTime(departure);
        flight.setArrivalTime(departure.plusHours(2));
        flight.setDuration(120);
        flight.setAirlineId(1L);
        flight.setStatus("SCHEDULED");
        flight.setFlightType("DOMESTIC");
        flightMapper.insert(flight);

        economy = insertCabin(CABIN_ECONOMY, "经济舱", new BigDecimal("1000.00"), 100);
        business = insertCabin(CABIN_BUSINESS, "公务舱", new BigDecimal("3000.00"), 50);
    }

    @AfterEach
    void tearDown() {
        for (Long orderId : createdOrderIds) {
            // 改签补款的支付单按 biz_ref 关联，不挂在 order_id 上，必须单独清
            for (Map<String, Object> row : jdbc.queryForList(
                    "SELECT change_id FROM order_change WHERE order_id = ?", orderId)) {
                jdbc.update("DELETE FROM payment WHERE biz_type = 'CHANGE' AND biz_ref = ?",
                        String.valueOf(row.get("change_id")));
            }
            jdbc.update("DELETE FROM boarding_pass WHERE checkin_id IN "
                    + "(SELECT id FROM check_in WHERE order_id = ?)", orderId);
            jdbc.update("DELETE FROM check_in WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM seat WHERE passenger_id IN "
                    + "(SELECT id FROM order_passenger WHERE order_id = ?)", orderId);
            jdbc.update("DELETE FROM order_change WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM order_refund WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM payment WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM order_passenger WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM t_order WHERE id = ?", orderId);
        }
        createdOrderIds.clear();

        jdbc.update("DELETE FROM seat WHERE flight_id = ?", flight.getId());
        jdbc.update("DELETE FROM flight_cabin WHERE flight_id = ?", flight.getId());
        jdbc.update("DELETE FROM flight WHERE id = ?", flight.getId());
    }

    private FlightCabin insertCabin(String cabinClass, String cabinName, BigDecimal totalPrice, int seats) {
        FlightCabin cabin = new FlightCabin();
        cabin.setFlightId(flight.getId());
        cabin.setCabinClass(cabinClass);
        cabin.setCabinName(cabinName);
        cabin.setFare(totalPrice.subtract(new BigDecimal("50.00")));
        cabin.setTax(new BigDecimal("50.00"));
        cabin.setTotalPrice(totalPrice);
        cabin.setTotalSeats(seats);
        cabin.setAvailableSeats(seats);
        cabin.setVersion(0);
        flightCabinMapper.insert(cabin);
        return cabin;
    }

    /**
     * 造一笔「已支付」订单。
     *
     * @param cabinClass 舱位，决定订单票面金额
     * @param checkedIn  是否已值机并占用座位（退票释放座位的断言需要它为 true）
     */
    private Order seedPaidOrder(String cabinClass, boolean checkedIn) {
        FlightCabin cabin = CABIN_BUSINESS.equals(cabinClass) ? business : economy;

        Order order = new Order();
        order.setOrderId("TST" + IdUtil.getSnowflakeNextIdStr());
        order.setOrderNo("TNO" + IdUtil.getSnowflakeNextIdStr());
        order.setPnr("TSTPNR" + (System.nanoTime() % 100000));
        order.setUserId(TEST_USER_ID);
        order.setFlightId(flight.getId());
        order.setFlightNo(flight.getFlightNo());
        order.setCabinClass(cabinClass);
        order.setContactName("测试旅客");
        order.setContactPhone("13800138001");
        order.setStatus("PAID");
        order.setFare(cabin.getFare());
        order.setTax(cabin.getTax());
        order.setServiceFee(BigDecimal.ZERO);
        order.setTotalAmount(cabin.getTotalPrice());
        order.setPaidTime(LocalDateTime.now());
        orderMapper.insert(order);
        createdOrderIds.add(order.getId());

        OrderPassenger p = new OrderPassenger();
        p.setOrderId(order.getId());
        p.setPassengerName("测试旅客");
        p.setGender("MALE");
        p.setBirthday(LocalDate.of(1990, 1, 1));
        p.setIdType("ID_CARD");
        p.setIdNumber("110101199001011234");
        p.setPhone("13800138001");
        p.setPassengerType("ADULT");
        if (checkedIn) {
            p.setCheckinStatus("CHECKED_IN");
            p.setSeatRow(10);
            p.setSeatColumn("A");
        } else {
            p.setCheckinStatus("NOT_CHECKED_IN");
        }
        orderPassengerMapper.insert(p);

        // 支付成功记录：审核退票 / 改签退差价都要靠它找到渠道交易，缺了会直接报错
        Payment payment = new Payment();
        payment.setPaymentId("PAYTST" + IdUtil.getSnowflakeNextIdStr());
        payment.setOrderId(order.getId());
        payment.setPayMethod("ALIPAY");
        payment.setBizType("ORDER");
        payment.setAmount(cabin.getTotalPrice());
        payment.setStatus("SUCCESS");
        payment.setTransactionId("MOCK" + System.nanoTime());
        payment.setPaidTime(LocalDateTime.now());
        paymentMapper.insert(payment);

        if (checkedIn) {
            Seat seat = new Seat();
            seat.setFlightId(flight.getId());
            seat.setRowNum(10);
            seat.setColCode("A");
            seat.setCabinClass(cabinClass);
            seat.setSeatType("NORMAL");
            seat.setExtraFee(BigDecimal.ZERO);
            seat.setStatus("OCCUPIED");
            seat.setPassengerId(p.getId());
            seatMapper.insert(seat);

            CheckIn checkIn = new CheckIn();
            checkIn.setCheckinId("CKTST" + IdUtil.getSnowflakeNextIdStr());
            checkIn.setOrderId(order.getId());
            checkIn.setFlightId(flight.getId());
            checkIn.setUserId(TEST_USER_ID);
            checkIn.setStatus("COMPLETED");
            checkInMapper.insert(checkIn);

            BoardingPass bp = new BoardingPass();
            bp.setCheckinId(checkIn.getId());
            bp.setPassengerName("测试旅客");
            bp.setFlightNo(flight.getFlightNo());
            bp.setSeat("10A");
            bp.setGate("A01");
            bp.setBoardingTime(LocalDateTime.now().plusHours(29));
            boardingPassMapper.insert(bp);

            // 下单时扣减过座位，这里同步扣掉，让库存基线与真实流程一致
            jdbc.update("UPDATE flight_cabin SET available_seats = available_seats - 1 WHERE id = ?",
                    cabin.getId());
        }
        return order;
    }

    private int seatsOf(FlightCabin cabin) {
        Integer v = jdbc.queryForObject(
                "SELECT available_seats FROM flight_cabin WHERE id = ?", Integer.class, cabin.getId());
        return v == null ? -1 : v;
    }

    private Order reload(Order order) {
        return orderMapper.selectById(order.getId());
    }

    private String changeStatusOf(String changeId) {
        OrderChange c = orderChangeMapper.selectOne(
                new LambdaQueryWrapper<OrderChange>().eq(OrderChange::getChangeId, changeId));
        return c == null ? null : c.getStatus();
    }

    private Map<String, Object> changeTo(Order order, String newCabinClass) {
        OrderChangeDTO dto = new OrderChangeDTO();
        dto.setNewFlightId(String.valueOf(flight.getId()));
        dto.setNewCabinClass(newCabinClass);
        dto.setSegmentIndex(0);
        return orderService.changeOrder(order.getOrderId(), TEST_USER_ID, dto);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> feeOf(Map<String, Object> applied) {
        return (Map<String, Object>) applied.get("fee");
    }

    private static void assertMoney(String expected, Object actual, String message) {
        assertNotNull(actual, message);
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual), message);
    }

    // ==================== 规则计算 ====================

    @Test
    @DisplayName("手续费规则按距起飞时间分档，起飞前 2 小时内不可退改")
    void feeRuleStages() {
        assertMoney("0.05", feeRuleService.refundFeeRatio(LocalDateTime.now().plusHours(30)),
                "距起飞 ≥24h 退票手续费应为 5%");
        assertMoney("0.10", feeRuleService.refundFeeRatio(LocalDateTime.now().plusHours(10)),
                "距起飞 2~24h 退票手续费应为 10%");
        assertMoney("1", feeRuleService.refundFeeRatio(LocalDateTime.now().plusMinutes(30)),
                "距起飞不足 2h 应为全额扣费（视同不可退）");
        assertFalse(feeRuleService.refundAllowed(LocalDateTime.now().plusMinutes(30)));
        assertTrue(feeRuleService.refundAllowed(LocalDateTime.now().plusHours(30)));

        // 起飞时间缺失按最严档处理 —— 不能"查不到时间就免费退"
        assertMoney("1", feeRuleService.refundFeeRatio(null), "起飞时间缺失应视为不可退");
    }

    // ==================== 退票 ====================

    @Test
    @DisplayName("退票：申请按规则计费 → 审核通过真实退款并回补库存与座位")
    void refundSettlement() {
        Order order = seedPaidOrder(CABIN_ECONOMY, true);
        int seatsBefore = seatsOf(economy);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setReason("行程变更");
        Map<String, Object> applied = orderService.refundOrder(order.getOrderId(), TEST_USER_ID, dto);

        // 规则档位 before24h = 5%，订单总额 1000 → 手续费 50、实退 950
        // （改造前是写死的 10%：手续费 100、实退 900）
        assertMoney("50.00", applied.get("refundFee"), "手续费应按规则算成 50，而不是写死的 100");
        assertMoney("950.00", applied.get("refundAmount"), "实退金额 = 总额 − 手续费");

        String refundId = (String) applied.get("refundId");

        // 同一订单不允许再提第二笔：两笔都审核通过就会退两次钱
        assertThrows(BusinessException.class,
                () -> orderService.refundOrder(order.getOrderId(), TEST_USER_ID, dto),
                "已有处理中的退票申请时必须拒绝再次申请");

        adminTicketService.approveRefund(refundId, Map.of("approved", true));

        OrderRefund refund = orderRefundMapper.selectOne(
                new LambdaQueryWrapper<OrderRefund>().eq(OrderRefund::getRefundId, refundId));
        assertEquals("COMPLETED", refund.getStatus(), "审核通过后应落成已完成（已退款）");
        assertNotNull(refund.getRefundTime(), "退款时间必须落库，否则无法证明钱退过");
        assertNotNull(refund.getChannelRefundNo(), "渠道退款流水号必须落库，便于对账");

        assertEquals("REFUNDED", reload(order).getStatus(), "订单应置为已退款");
        assertEquals(seatsBefore + 1, seatsOf(economy), "座位库存应按乘机人数回补");

        OrderPassenger p = orderPassengerMapper.selectOne(
                new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, order.getId()));
        assertNull(p.getSeatRow(), "旅客座位应清空（order_passenger 是座位占用的真源）");
        assertNull(p.getSeatColumn());
        assertEquals("NOT_CHECKED_IN", p.getCheckinStatus());

        Seat seat = seatMapper.selectOne(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getFlightId, flight.getId()).eq(Seat::getRowNum, 10).eq(Seat::getColCode, "A"));
        assertEquals("AVAILABLE", seat.getStatus(), "座位应释放为可预订");
        assertNull(seat.getPassengerId(), "座位不应再绑定旅客");

        CheckIn checkIn = checkInMapper.selectOne(
                new LambdaQueryWrapper<CheckIn>().eq(CheckIn::getOrderId, order.getId()));
        assertEquals("CANCELLED", checkIn.getStatus(), "值机记录应作废");
        assertEquals(0L, (long) boardingPassMapper.selectCount(
                        new LambdaQueryWrapper<BoardingPass>().eq(BoardingPass::getCheckinId, checkIn.getId())),
                "登机牌应删除，否则旅客手里还留着旧航班的登机牌");
    }

    @Test
    @DisplayName("退票：重复审核是幂等的，不会退第二次、也不会重复回补库存")
    void refundApproveIsIdempotent() {
        Order order = seedPaidOrder(CABIN_ECONOMY, true);
        int seatsBefore = seatsOf(economy);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setReason("行程变更");
        String refundId = (String) orderService
                .refundOrder(order.getOrderId(), TEST_USER_ID, dto).get("refundId");

        adminTicketService.approveRefund(refundId, Map.of("approved", true));
        int seatsAfterFirst = seatsOf(economy);

        // 第二次点击「通过」必须直接跳过，否则会再退一笔钱、再回补一次库存
        adminTicketService.approveRefund(refundId, Map.of("approved", true));

        assertEquals(seatsBefore + 1, seatsAfterFirst);
        assertEquals(seatsAfterFirst, seatsOf(economy), "重复审核不得重复回补库存");
        assertEquals(1L, (long) orderRefundMapper.selectCount(
                new LambdaQueryWrapper<OrderRefund>().eq(OrderRefund::getOrderId, order.getId())),
                "不得因为重复审核多出一条退票记录");
    }

    // ==================== 改签 ====================

    @Test
    @DisplayName("改签：差价按新旧舱位票面差额真实计算，而非恒为 0")
    void changeFareDiffIsComputed() {
        Order order = seedPaidOrder(CABIN_ECONOMY, false);

        Map<String, Object> applied = changeTo(order, CABIN_BUSINESS);
        Map<String, Object> fee = feeOf(applied);

        // 差价 = 公务舱 3000 − 经济舱已付票面（950 + 50）= 2000
        assertMoney("2000.00", fee.get("fareDiff"), "差价必须是新旧舱位票面差额，改造前这里恒为 0");
        // 距起飞 30 小时 → 改签规则 before24h 档 = 0%
        assertMoney("0.00", fee.get("changeFee"), "改签手续费应按规则算");
        assertMoney("2000.00", fee.get("total"), "需补金额 = 手续费 + 差价");
    }

    @Test
    @DisplayName("改签需补款：审核后进入待补款、行程不变；支付成功后才真正生效")
    void changeWithTopUpSettlesAfterPayment() {
        Order order = seedPaidOrder(CABIN_ECONOMY, true);
        int economySeatsBefore = seatsOf(economy);
        int businessSeatsBefore = seatsOf(business);

        Map<String, Object> applied = changeTo(order, CABIN_BUSINESS);
        String changeId = (String) applied.get("changeId");

        adminTicketService.approveChange(changeId, Map.of("approved", true));

        assertEquals("PENDING_PAYMENT", changeStatusOf(changeId), "需补款时应停在待补款");
        assertEquals(CABIN_ECONOMY, reload(order).getCabinClass(), "钱没到之前行程不能变");
        assertEquals(economySeatsBefore, seatsOf(economy), "钱没到之前不能释放原舱位库存");

        // 旅客补款
        Map<String, Object> prepay = paymentService.createChangePayment(changeId, null, TEST_USER_ID);
        String paymentId = (String) prepay.get("paymentId");
        assertNotNull(paymentId, "应生成改签补款支付单");
        paymentService.simulatePay(paymentId, TEST_USER_ID);

        Order settled = reload(order);
        assertEquals(CABIN_BUSINESS, settled.getCabinClass(), "补款成功后行程应变更");
        assertEquals(0, business.getTotalPrice().compareTo(settled.getFare().add(settled.getTax())),
                "订单票面金额应按新舱位重算");
        assertEquals("COMPLETED", changeStatusOf(changeId), "补款成功后改签应收口为已完成");

        assertEquals(economySeatsBefore + 1, seatsOf(economy), "原航班舱位库存应回补");
        assertEquals(businessSeatsBefore - 1, seatsOf(business), "新航班舱位库存应扣减");

        OrderPassenger p = orderPassengerMapper.selectOne(
                new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, order.getId()));
        assertNull(p.getSeatRow(), "改签后必须重新值机选座，旧座位应释放");
        assertEquals("NOT_CHECKED_IN", p.getCheckinStatus());
    }

    @Test
    @DisplayName("改签无需补款：审核通过即生效，并在净额为负时退还差价")
    void changeWithoutTopUpSettlesImmediately() {
        // 公务舱 → 经济舱：差价 −2000，净额为负，属"应退旅客钱"的分支
        Order order = seedPaidOrder(CABIN_BUSINESS, true);
        int economySeatsBefore = seatsOf(economy);
        int businessSeatsBefore = seatsOf(business);

        Map<String, Object> applied = changeTo(order, CABIN_ECONOMY);
        Map<String, Object> fee = feeOf(applied);
        assertMoney("2000.00", fee.get("refundable"), "净额为负时应告知前端可退金额");
        assertMoney("0.00", fee.get("total"), "应退钱时旅客无需再付款");

        String changeId = (String) applied.get("changeId");
        adminTicketService.approveChange(changeId, Map.of("approved", true));

        assertEquals("COMPLETED", changeStatusOf(changeId), "无需补款的改签应在审核通过时立即生效");
        assertEquals(CABIN_ECONOMY, reload(order).getCabinClass());
        assertEquals(economySeatsBefore - 1, seatsOf(economy), "新舱位库存应扣减");
        assertEquals(businessSeatsBefore + 1, seatsOf(business), "原舱位库存应回补");
    }
}
