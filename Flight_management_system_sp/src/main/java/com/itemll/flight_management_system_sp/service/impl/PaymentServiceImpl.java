package com.itemll.flight_management_system_sp.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.config.PaymentProperties;
import com.itemll.flight_management_system_sp.dto.PaymentCreateDTO;
import com.itemll.flight_management_system_sp.entity.*;
import com.itemll.flight_management_system_sp.mapper.*;
import com.itemll.flight_management_system_sp.pay.CallbackResult;
import com.itemll.flight_management_system_sp.pay.PayMethods;
import com.itemll.flight_management_system_sp.pay.PayQueryResult;
import com.itemll.flight_management_system_sp.pay.PaymentGateway;
import com.itemll.flight_management_system_sp.pay.PaymentGatewayRouter;
import com.itemll.flight_management_system_sp.pay.PrepayResult;
import com.itemll.flight_management_system_sp.service.MemberService;
import com.itemll.flight_management_system_sp.service.OrderChangeSettlementService;
import com.itemll.flight_management_system_sp.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 支付服务实现
 * <p>职责边界：只负责「业务状态推进」与「归属校验」，与支付平台的交互全部委托给
 * {@link PaymentGatewayRouter}，本类不出现任何渠道相关的判断与 SDK 调用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    /** 支付单状态：待支付 */
    private static final String STATUS_PENDING = "PENDING";
    /** 支付单状态：支付成功 */
    private static final String STATUS_SUCCESS = "SUCCESS";
    /** 支付单状态：已关闭（订单超时取消时同步关闭，渠道侧那笔交易不再受理） */
    private static final String STATUS_CLOSED = "CLOSED";
    /** 支付单业务类型：客票支付 */
    private static final String BIZ_TYPE_ORDER = "ORDER";
    /** 支付单业务类型：改签补款（复用支付链路，入账后推进的是改签单） */
    private static final String BIZ_TYPE_CHANGE = "CHANGE";
    /** 改签单状态：审核已通过、等待旅客补款 */
    private static final String CHANGE_PENDING_PAYMENT = "PENDING_PAYMENT";
    /** 订单状态：待支付 */
    private static final String ORDER_PENDING_PAYMENT = "PENDING_PAYMENT";
    /** 订单标题前缀 */
    private static final String SUBJECT_PREFIX = "航班订单";
    /** 渠道原始报文入库前的截断长度 */
    private static final int CALLBACK_DATA_MAX_LENGTH = 1000;
    /** 主动查单节流 Key 前缀：前端每 3 秒轮询一次，若每次都打渠道接口会把渠道打爆 */
    private static final String QUERY_THROTTLE_KEY_PREFIX = "pay:query:";
    /** 主动查单节流窗口（秒），与前端轮询间隔一致 */
    private static final long QUERY_THROTTLE_SECONDS = 3L;
    /** 单批对账上限，防止堆积时一次性调用渠道过多 */
    private static final int RECONCILE_MAX_LIMIT = 200;

    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final OrderServiceItemMapper orderServiceItemMapper;
    private final OrderPassengerMapper orderPassengerMapper;
    private final SpecialServiceRequestMapper specialServiceRequestMapper;
    private final OrderChangeMapper orderChangeMapper;
    private final PaymentGatewayRouter paymentGatewayRouter;
    private final PaymentProperties paymentProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MemberService memberService;
    /** 改签生效的唯一实现；本类只负责在「改签补款入账成功」那一刻把它叫起来 */
    private final OrderChangeSettlementService orderChangeSettlementService;

    @Override
    @Transactional
    public Map<String, Object> createPayment(PaymentCreateDTO dto, Long userId) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderId, dto.getOrderId())
                        .eq(Order::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!ORDER_PENDING_PAYMENT.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单状态不允许支付");
        }
        if (order.getExpireTime() != null && LocalDateTime.now().isAfter(order.getExpireTime())) {
            throw new BusinessException(ErrorCode.ORDER_EXPIRED, "订单已过期，请重新下单");
        }

        // 金额以订单为准：前端传来的 amount 只用于比对，不作为入库依据，
        // 否则任何登录用户都能构造 { orderId, amount: 0.01 } 把订单「付掉」。
        BigDecimal orderAmount = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        if (dto.getAmount() == null || orderAmount.compareTo(dto.getAmount()) != 0) {
            log.warn("支付金额与订单金额不一致: orderId={}, 订单金额={}, 请求金额={}",
                    order.getOrderId(), orderAmount, dto.getAmount());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "支付金额与订单金额不一致，请刷新页面后重试");
        }

        // 按支付方式选择渠道；未开通的渠道在这里直接给出明确提示，而不是返回一个扫不出来的假码
        String payMethod = PayMethods.normalize(dto.getPayMethod());
        PaymentGateway gateway = paymentGatewayRouter.route(payMethod);

        Payment payment = new Payment();
        payment.setPaymentId("PAY" + IdUtil.getSnowflakeNextIdStr());
        payment.setOrderId(order.getId());
        payment.setPayMethod(payMethod);
        payment.setAmount(orderAmount);
        payment.setStatus(STATUS_PENDING);
        payment.setExpireTime(LocalDateTime.now().plusMinutes(paymentProperties.expireMinutesOrDefault()));

        // 向渠道预下单。失败会抛业务异常并回滚事务，不会留下没有支付凭证的孤儿支付单。
        // 使用 paymentId 作为渠道商户单号，重复预下单渠道会返回同一份凭证，天然幂等。
        PrepayResult prepay = gateway.prepay(payment.getPaymentId(), payMethod, orderAmount, buildSubject(order));
        payment.setQrCode(prepay.getQrCode());
        payment.setPayUrl(prepay.getPayUrl());
        paymentMapper.insert(payment);

        log.info("支付创建成功: paymentId={}, orderId={}, amount={}, channel={}",
                payment.getPaymentId(), order.getOrderId(), orderAmount, gateway.channel());

        Map<String, Object> result = new HashMap<>();
        result.put("paymentId", payment.getPaymentId());
        result.put("payUrl", payment.getPayUrl());
        result.put("qrCode", payment.getQrCode());
        result.put("expireAt", payment.getExpireTime());
        return result;
    }

    @Override
    public Map<String, Object> getPaymentStatus(String paymentId, Long userId) {
        Payment payment = requireOwnedPayment(paymentId, userId);

        // 真实模式下，前端 3 秒轮询顺带触发一次渠道查单。
        // 原因：本地开发/答辩环境是 localhost，收不到渠道的公网异步回调，
        // 这条路径保证「扫码 → 付款 → 页面自动跳转」仍能闭环，代价是比回调晚几秒。
        if (STATUS_PENDING.equals(payment.getStatus()) && !paymentGatewayRouter.isMockMode()) {
            payment = syncFromChannelByPolling(payment);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("paymentId", payment.getPaymentId());
        result.put("orderId", payment.getOrderId());
        result.put("amount", payment.getAmount());
        result.put("status", payment.getStatus());
        result.put("paidAt", payment.getPaidTime());
        result.put("transactionId", payment.getTransactionId());
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> simulatePay(String paymentId, Long userId) {
        // 该入口的放行权交给配置，而不是硬编码在代码里：
        // 真实模式下默认关闭（否则等于留了一个「免费支付」后门），
        // 但演示/答辩环境可通过 payment.demo.simulate-enabled 单独打开，作为现场兜底。
        if (!paymentProperties.isSimulateAllowed()) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "当前为真实支付模式，模拟支付接口已关闭，请通过支付平台完成付款");
        }

        Payment payment = requireOwnedPayment(paymentId, userId);
        if (!STATUS_PENDING.equals(payment.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该支付已完成或已关闭");
        }

        boolean marked = markPaid(paymentId,
                "SIM" + System.currentTimeMillis(),
                "{\"source\":\"simulate\",\"channel\":\"MOCK\"}");
        if (!marked) {
            throw new BusinessException(ErrorCode.CONFLICT, "该支付已完成或已关闭");
        }

        Payment latest = findByPaymentId(paymentId);
        log.info("模拟支付成功: paymentId={}, orderId={}, amount={}",
                paymentId, latest.getOrderId(), latest.getAmount());

        Map<String, Object> result = new HashMap<>();
        result.put("paymentId", latest.getPaymentId());
        result.put("status", latest.getStatus());
        result.put("paidAt", latest.getPaidTime());
        result.put("transactionId", latest.getTransactionId());
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> createChangePayment(String changeId, String payMethod, Long userId) {
        OrderChange change = orderChangeMapper.selectOne(new LambdaQueryWrapper<OrderChange>()
                .eq(OrderChange::getChangeId, changeId));
        if (change == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "改签记录不存在");
        }
        Order order = orderMapper.selectById(change.getOrderId());
        // 归属校验不通过统一返回「不存在」，避免通过错误信息差异探测出哪些改签单号真实存在
        if (order == null || userId == null || !userId.equals(order.getUserId())) {
            log.warn("改签补款归属校验失败: changeId={}, 请求用户={}, 订单用户={}",
                    changeId, userId, order == null ? null : order.getUserId());
            throw new BusinessException(ErrorCode.NOT_FOUND, "改签记录不存在");
        }
        if (!CHANGE_PENDING_PAYMENT.equals(change.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该改签当前无需支付");
        }

        BigDecimal payable = change.getTotalFee() == null ? BigDecimal.ZERO : change.getTotalFee();
        if (payable.signum() <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该改签无需补款");
        }

        // 幂等：同一改签单已有未过期的待支付单就直接复用。
        // 否则旅客每刷新一次页面就会向渠道重新预下单、多出一个二维码。
        Payment existing = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getBizType, BIZ_TYPE_CHANGE)
                .eq(Payment::getBizRef, changeId)
                .eq(Payment::getStatus, STATUS_PENDING)
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
        if (existing != null
                && (existing.getExpireTime() == null || existing.getExpireTime().isAfter(LocalDateTime.now()))) {
            return buildPrepayResponse(existing);
        }

        // 支付方式缺省时沿用原订单的支付渠道 —— 改签补款按「原路」收最符合直觉
        String normalized = StrUtil.isBlank(payMethod) ? inheritPayMethod(order.getId()) : PayMethods.normalize(payMethod);
        PaymentGateway gateway = paymentGatewayRouter.route(normalized);

        Payment payment = new Payment();
        payment.setPaymentId("PAY" + IdUtil.getSnowflakeNextIdStr());
        payment.setOrderId(order.getId());
        payment.setPayMethod(normalized);
        payment.setBizType(BIZ_TYPE_CHANGE);
        payment.setBizRef(changeId);
        payment.setAmount(payable);
        payment.setStatus(STATUS_PENDING);
        payment.setExpireTime(LocalDateTime.now().plusMinutes(paymentProperties.expireMinutesOrDefault()));

        // 预下单失败会抛异常并回滚，不会留下没有支付凭证的孤儿支付单
        PrepayResult prepay = gateway.prepay(payment.getPaymentId(), normalized, payable,
                buildChangeSubject(order));
        payment.setQrCode(prepay.getQrCode());
        payment.setPayUrl(prepay.getPayUrl());
        paymentMapper.insert(payment);

        log.info("改签补款支付已创建: paymentId={}, changeId={}, orderNo={}, amount={}, channel={}",
                payment.getPaymentId(), changeId, order.getOrderNo(), payable, gateway.channel());
        return buildPrepayResponse(payment);
    }

    @Override
    @Transactional
    public boolean markPaid(String paymentId, String channelTradeNo, String rawCallback) {
        Payment payment = findByPaymentId(paymentId);
        if (payment == null) {
            log.warn("入账失败，支付单不存在: paymentId={}", paymentId);
            return false;
        }

        // 条件更新：只有把 PENDING 改成 SUCCESS 的那次调用返回 1，其余并发/重复调用返回 0
        int rows = paymentMapper.markSuccess(paymentId, channelTradeNo,
                StrUtil.maxLength(rawCallback, CALLBACK_DATA_MAX_LENGTH));
        if (rows == 0) {
            // 推进失败有两种含义，必须区分开，否则会把资金问题当成正常重复投递静默掉：
            //   ① 已成功入账 → 渠道重复投递，正常情况；
            //   ② 支付单已被关闭 → 订单已超时取消，但用户确实付了钱，需要人工核对/退款。
            if (STATUS_CLOSED.equals(payment.getStatus())) {
                log.warn("渠道显示已收款，但支付单已随订单取消而关闭，存在「钱已收、票未出」风险，"
                                + "需人工核对是否退款: paymentId={}, orderId={}, amount={}, tradeNo={}",
                        paymentId, payment.getOrderId(), payment.getAmount(), channelTradeNo);
            } else {
                log.info("支付单已处理过，跳过重复入账: paymentId={}, status={}", paymentId, payment.getStatus());
            }
            return false;
        }

        // ---------- 改签补款：入账后推进的是「改签单」，不是订单支付状态 ----------
        // 必须在这里分流：改签补款对应的订单本来就已是 PAID，继续走下面的
        // orderMapper.markPaid（条件更新 PENDING_PAYMENT→PAID）必然 0 行，
        // 会被误判成"订单已被取消"并打出误导性告警。
        if (BIZ_TYPE_CHANGE.equals(payment.getBizType())) {
            log.info("改签补款入账成功: paymentId={}, changeId={}, amount={}",
                    paymentId, payment.getBizRef(), payment.getAmount());
            try {
                boolean settled = orderChangeSettlementService.settle(payment.getBizRef());
                if (!settled) {
                    log.error("改签补款已收到，但改签未生效（可能已生效或状态异常），请人工核对: paymentId={}, changeId={}",
                            paymentId, payment.getBizRef());
                }
            } catch (Exception e) {
                // 钱已收到是既成事实，绝不能让改签生效失败把「支付成功」这件事一起回滚
                // （否则支付单留在 PENDING，旅客已扣款却查不到入账，问题更大）。
                // 这里只记 ERROR 并把改签单留在 PENDING_PAYMENT，等人处理。
                log.error("改签补款已收到，但改签生效失败，需人工处理: paymentId={}, changeId={}, err={}",
                        paymentId, payment.getBizRef(), e.getMessage(), e);
            }
            return true;
        }

        Order order = orderMapper.selectById(payment.getOrderId());
        if (order == null) {
            log.error("支付单找不到对应订单，需要人工排查: paymentId={}, orderId={}", paymentId, payment.getOrderId());
            return true;
        }

        int orderRows = orderMapper.markPaid(order.getId());
        if (orderRows == 0) {
            // 说明订单已不在待支付状态：可能已被超时任务取消。
            // 真实收款场景下这里需要发起退款或转人工处理，演示环境先留下明确日志。
            log.warn("已收到款项但订单状态未推进（订单可能已被取消或过期）: paymentId={}, orderId={}, orderStatus={}, amount={}",
                    paymentId, order.getOrderId(), order.getStatus(), payment.getAmount());
            return true;
        }

        log.info("支付入账成功: paymentId={}, orderId={}, amount={}, tradeNo={}",
                paymentId, order.getOrderId(), payment.getAmount(), channelTradeNo);

        // 支付成功后自动创建 SSR 待审请求（特殊服务申请需航司审核后才生效）
        createSsrRequests(order);

        // 支付成功后累积里程：订单旅客里填了本平台常旅客号的，按航段距离发放并重算等级。
        // 刻意吞掉异常 —— 款已收到是既成事实，里程发放失败只能记日志、事后补发，
        // 绝不能让异常冒泡把「支付成功」这件事一起回滚掉。
        try {
            memberService.earnMilesForPaidOrder(order);
        } catch (Exception e) {
            log.error("里程累积失败，订单已正常入账，需人工补发: orderId={}, orderNo={}",
                    order.getId(), order.getOrderNo(), e);
        }

        return true;
    }

    @Override
    @Transactional
    public boolean handleCallback(String provider, Map<String, String> params) {
        PaymentGateway gateway;
        try {
            gateway = paymentGatewayRouter.routeByProvider(provider);
        } catch (BusinessException e) {
            log.warn("收到未识别渠道的支付回调，已拒绝: provider={}", provider);
            return false;
        }

        // ---------- 第一步：渠道验签（真伪判定） ----------
        CallbackResult callback = gateway.verifyCallback(params);
        if (!callback.isSignatureValid()) {
            log.warn("支付回调验签未通过，已拒绝: provider={}, reason={}, notifyId={}",
                    provider, callback.getRawMessage(), callback.getNotifyId());
            return false;
        }

        String paymentId = callback.getPaymentId();
        if (StrUtil.isBlank(paymentId)) {
            log.warn("支付回调缺少商户单号，已拒绝: provider={}, notifyId={}", provider, callback.getNotifyId());
            return false;
        }

        // ---------- 第二步：支付单是否存在 ----------
        Payment payment = findByPaymentId(paymentId);
        if (payment == null) {
            // 这里必须返回「未受理」而不是成功应答。
            // 应答成功等于告诉渠道「我已记账」，而实际上我们连这笔单子都找不到，
            // 后续再没有任何机会补记；返回失败可以让渠道按策略重推，给我们排查时间。
            log.error("支付回调找不到支付单，请排查单号是否一致: provider={}, paymentId={}, notifyId={}",
                    provider, paymentId, callback.getNotifyId());
            return false;
        }

        // ---------- 第三步：金额核对 ----------
        // 防止「1 元订单、0.01 元付款」式差异被静默记成已支付。
        // 回调金额缺省（部分渠道字段可空）时不阻断，但会记日志留痕。
        if (callback.getAmount() == null) {
            log.warn("支付回调未携带金额，跳过金额核对: paymentId={}, 本地金额={}",
                    paymentId, payment.getAmount());
        } else if (payment.getAmount() == null
                || payment.getAmount().compareTo(callback.getAmount()) != 0) {
            log.error("支付回调金额与支付单不一致，拒绝入账: paymentId={}, 支付单金额={}, 回调金额={}, notifyId={}",
                    paymentId, payment.getAmount(), callback.getAmount(), callback.getNotifyId());
            return false;
        }

        // ---------- 第四步：非成功态只需应答，不做入账 ----------
        if (!callback.isPaid()) {
            // WAIT_BUYER_PAY 等中间态无需处理，但必须应答成功，否则渠道会一直重推。
            log.info("支付回调非成功态，无需入账: provider={}, paymentId={}, rawStatus={}",
                    provider, paymentId, callback.getRawStatus());
            return true;
        }

        // ---------- 第五步：幂等入账 ----------
        // markPaid 内部用 SQL 条件更新兜住重复投递：返回 false 说明本次是重复通知，属正常情况。
        boolean marked = markPaid(paymentId, callback.getChannelTradeNo(), callback.getRawMessage());
        log.info("支付回调处理完成: provider={}, paymentId={}, notifyId={}, 本次实际入账={}",
                provider, paymentId, callback.getNotifyId(), marked);
        return true;
    }

    // ==================== 内部方法 ====================

    /**
     * 轮询触发的主动查单（带节流）。
     * <p>前端每 3 秒轮询一次支付状态，如果每次都真的去调渠道接口，用户停留 5 分钟就是 100 次
     * 渠道调用，既浪费配额也容易触发渠道限流。这里用 Redis 做窗口节流，保证同一支付单
     * 最多每 {@value #QUERY_THROTTLE_SECONDS} 秒查一次渠道。</p>
     */
    private Payment syncFromChannelByPolling(Payment payment) {
        if (!acquireQueryQuota(payment.getPaymentId())) {
            return payment;
        }
        try {
            PaymentGateway gateway = paymentGatewayRouter.route(payment.getPayMethod());
            return syncFromChannel(payment, gateway);
        } catch (BusinessException e) {
            // 渠道未开通/凭证缺失，属配置问题，不该让「查询支付状态」这个只读接口报错
            log.debug("轮询查单跳过（渠道不可用）: paymentId={}, reason={}",
                    payment.getPaymentId(), e.getMessage());
            return payment;
        }
    }

    /**
     * 查单并补单。回调丢失时，用户付款后订单仍会停在待支付，本方法是对账的关键一环。
     * <p>任何异常都被吞掉并记日志：轮询查单是「尽力而为」的补偿动作，
     * 不该因为它失败而让前端拿不到支付状态。</p>
     */
    private Payment syncFromChannel(Payment payment, PaymentGateway gateway) {
        try {
            PayQueryResult queryResult = gateway.query(payment.getPaymentId());
            if (queryResult.isPaid()) {
                boolean marked = markPaid(payment.getPaymentId(),
                        queryResult.getChannelTradeNo(), queryResult.getRawMessage());
                log.info("轮询查单发现渠道已收款，已补单入账: paymentId={}, 本次实际入账={}",
                        payment.getPaymentId(), marked);
                return findByPaymentId(payment.getPaymentId());
            }
        } catch (Exception e) {
            log.warn("轮询查单失败，本轮跳过: paymentId={}, err={}", payment.getPaymentId(), e.getMessage());
        }
        return payment;
    }

    /**
     * 领取一次渠道查单配额。
     * <p>用 Redis SETNX 实现窗口节流，天然支持多实例部署（Nginx 负载均衡时不会出现
     * 每个实例各查一次的问题）。Redis 不可用时选择「不查」——宁可晚几秒入账，
     * 也不要降级成每次轮询都打渠道接口。</p>
     */
    private boolean acquireQueryQuota(String paymentId) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    QUERY_THROTTLE_KEY_PREFIX + paymentId, "1",
                    QUERY_THROTTLE_SECONDS, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("查单节流不可用（Redis 异常），本轮跳过查单: paymentId={}, err={}",
                    paymentId, e.getMessage());
            return false;
        }
    }

    /**
     * 校验支付单归属并返回。
     * <p>归属不匹配时统一返回「不存在」而不是「无权限」，避免通过错误信息差异探测出
     * 哪些 paymentId 是真实存在的。</p>
     */
    private Payment requireOwnedPayment(String paymentId, Long userId) {
        Payment payment = findByPaymentId(paymentId);
        if (payment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "支付记录不存在");
        }
        Order order = orderMapper.selectById(payment.getOrderId());
        if (order == null || userId == null || !userId.equals(order.getUserId())) {
            log.warn("支付单归属校验失败: paymentId={}, 请求用户={}, 订单用户={}",
                    paymentId, userId, order == null ? null : order.getUserId());
            throw new BusinessException(ErrorCode.NOT_FOUND, "支付记录不存在");
        }
        return payment;
    }

    private Payment findByPaymentId(String paymentId) {
        return paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getPaymentId, paymentId));
    }

    /** 订单标题：展示在支付平台收银台上，便于用户核对买的是哪段行程 */
    private String buildSubject(Order order) {
        String flightNo = StrUtil.blankToDefault(order.getFlightNo(), "").trim();
        return flightNo.isEmpty()
                ? SUBJECT_PREFIX
                : StrUtil.format("{} {} 客票", SUBJECT_PREFIX, flightNo);
    }

    /** 改签补款的订单标题：让旅客在收银台上一眼看出这是改签补款而不是重复收费 */
    private String buildChangeSubject(Order order) {
        String flightNo = StrUtil.blankToDefault(order.getFlightNo(), "").trim();
        return flightNo.isEmpty()
                ? "改签补款"
                : StrUtil.format("改签补款 {}", flightNo);
    }

    /** 预下单结果的统一响应体，供「首次创建」与「复用已有待支付单」两条路径共用 */
    private Map<String, Object> buildPrepayResponse(Payment payment) {
        Map<String, Object> result = new HashMap<>();
        result.put("paymentId", payment.getPaymentId());
        result.put("payUrl", payment.getPayUrl());
        result.put("qrCode", payment.getQrCode());
        result.put("amount", payment.getAmount());
        result.put("expireAt", payment.getExpireTime());
        return result;
    }

    /**
     * 沿用该订单最近一笔成功支付所用的支付方式，用于「原路补款」。
     * <p>查不到时退回 {@code ALIPAY} —— 它是当前唯一在 live 模式下真正开通的渠道。</p>
     */
    private String inheritPayMethod(Long orderId) {
        Payment lastPaid = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId)
                .eq(Payment::getStatus, STATUS_SUCCESS)
                .orderByDesc(Payment::getPaidTime)
                .last("LIMIT 1"));
        return lastPaid == null ? PayMethods.ALIPAY : PayMethods.normalize(lastPaid.getPayMethod());
    }

    /** 支付成功后，把订单内的 SSR 服务项转成待审核请求 */
    private void createSsrRequests(Order order) {
        List<OrderServiceItem> ssrItems = orderServiceItemMapper.selectList(
                new LambdaQueryWrapper<OrderServiceItem>()
                        .eq(OrderServiceItem::getOrderId, order.getId())
                        .eq(OrderServiceItem::getServiceType, "SSR"));
        if (ssrItems.isEmpty()) {
            return;
        }

        List<OrderPassenger> passengers = orderPassengerMapper.selectList(
                new LambdaQueryWrapper<OrderPassenger>()
                        .eq(OrderPassenger::getOrderId, order.getId()));

        for (OrderServiceItem item : ssrItems) {
            int paxIdx = item.getPassengerIndex() != null ? item.getPassengerIndex() : 0;
            if (paxIdx < 0 || paxIdx >= passengers.size()) {
                continue;
            }
            OrderPassenger op = passengers.get(paxIdx);
            SpecialServiceRequest ssr = new SpecialServiceRequest();
            ssr.setOrderId(order.getId());
            ssr.setOrderPassengerId(op.getId());
            ssr.setPassengerName(op.getPassengerName());
            ssr.setSsrCode(item.getServiceCode());
            ssr.setStatus("PENDING");
            specialServiceRequestMapper.insert(ssr);
            log.info("支付后自动创建SSR请求: orderId={}, code={}, passenger={}",
                    order.getOrderId(), item.getServiceCode(), op.getPassengerName());
        }
    }
}
