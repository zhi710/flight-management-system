package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.dto.PaymentCreateDTO;
import java.util.Map;

/**
 * 支付服务接口
 * <p>所有涉及「谁能操作这笔支付」的方法都必须带 userId：支付单号是可被枚举的字符串，
 * 仅凭 paymentId 操作等于把别人的订单交给任何登录用户。</p>
 */
public interface PaymentService {

    /** 创建支付（向渠道预下单，返回二维码/跳转链接） */
    Map<String, Object> createPayment(PaymentCreateDTO dto, Long userId);

    /**
     * 为「改签补款」创建支付。
     *
     * <p>改签审核通过但手续费与差价合计为正时，改签单进入 {@code PENDING_PAYMENT}，
     * 旅客需要补款后改签才生效。本方法复用整条支付链路（渠道路由 → 预下单 → 扫码 →
     * 轮询/回调 → 入账），只是把支付单标记为 {@code biz_type=CHANGE}，
     * 入账时据此走改签生效而不是推进订单支付状态。</p>
     *
     * <p>幂等：同一改签单若已有待支付的支付单，直接返回它，不重复向渠道预下单 ——
     * 否则旅客每刷新一次页面就会多出一个二维码。</p>
     *
     * @param changeId  改签单号
     * @param payMethod 支付方式
     * @param userId    当前用户，用于归属校验
     */
    Map<String, Object> createChangePayment(String changeId, String payMethod, Long userId);

    /** 查询支付状态（供前端轮询） */
    Map<String, Object> getPaymentStatus(String paymentId, Long userId);

    /** 模拟支付完成（仅演示模式下可用） */
    Map<String, Object> simulatePay(String paymentId, Long userId);

    /**
     * 幂等入账：把支付单与订单推进到已支付。
     * <p>三条路径共用本方法，保证入账逻辑只有一份：</p>
     * <ol>
     *   <li>演示模式的 simulate 接口；</li>
     *   <li>支付宝异步回调（真实付款后由支付宝推送）；</li>
     *   <li>定时查单兜底（回调丢失时反查渠道状态补单）。</li>
     * </ol>
     *
     * @param paymentId     商户支付单号
     * @param channelTradeNo 渠道交易号
     * @param rawCallback   渠道原始报文摘要，落库便于排查
     * @return true = 本次调用完成了入账；false = 该支付单已被处理过（重复调用属正常情况，不应报错）
     */
    boolean markPaid(String paymentId, String channelTradeNo, String rawCallback);

    /**
     * 处理渠道异步回调。
     *
     * <p>完整流程：路由到渠道 → 渠道验签 → 校验支付单存在 → 核对金额 → 幂等入账。
     * 任意一步不通过都返回 {@code false}，由控制器应答渠道「处理失败」，让其按重推策略再投递。</p>
     *
     * @param provider 回调地址中的渠道名（如 {@code alipay}）
     * @param params   回调原始参数
     * @return true = 已受理（控制器应返回纯文本 {@code success}）；
     *         false = 未受理（应返回 {@code failure}，渠道会重推）
     */
    boolean handleCallback(String provider, Map<String, String> params);
}
