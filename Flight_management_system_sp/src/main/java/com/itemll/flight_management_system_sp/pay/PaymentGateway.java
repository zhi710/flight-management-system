package com.itemll.flight_management_system_sp.pay;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * 支付网关抽象
 * <p>把「向支付平台预下单 / 查单 / 退款」三件事从业务逻辑中隔离出来。业务侧
 * （{@code PaymentService}）只依赖本接口和 {@link PaymentGatewayRouter}，不感知任何具体渠道，
 * 新增渠道只需实现本接口并声明 {@link #supportedPayMethods()}，无需改动支付服务。</p>
 *
 * <p>实现约定：</p>
 * <ul>
 *   <li><b>不碰业务状态</b>：本接口只负责与渠道交互，订单/支付单状态的推进一律由业务层决定，
 *       否则会出现「渠道下单成功但订单状态被网关改乱」的不可控情况。</li>
 *   <li><b>异常归一</b>：SDK 异常、网络异常必须转换成 {@code BusinessException} 后抛出，
 *       不能把第三方异常类型泄漏到上层。</li>
 *   <li><b>线程安全</b>：渠道客户端应为单例（如支付宝的 AlipayClient 本身就是线程安全的），
 *       禁止每次调用 new 一个客户端。</li>
 *   <li><b>幂等</b>：同一个 outTradeNo（即 paymentId）重复 {@link #prepay} 应返回同一份支付凭证，
 *       渠道本身一般已保证；实现时不要生成随机的新单号。</li>
 * </ul>
 */
public interface PaymentGateway {

    /** 渠道标识：MOCK / ALIPAY，用于日志与后续回调路由 */
    String channel();

    /**
     * 本网关支持的支付方式（对应 payment.pay_method）。
     * <p>演示网关返回空集合 —— 它不代表任何一种真实支付方式，只作为 mock 模式的统一实现，
     * 这样 {@link PaymentGatewayRouter} 在真实模式下永远不会选中它。</p>
     */
    Set<String> supportedPayMethods();

    /** 渠道是否可用（凭证是否配置齐全）。返回 false 时路由器会给出「暂未开通」提示而不是空指针 */
    boolean available();

    /**
     * 预下单，生成支付凭证。
     *
     * @param paymentId 商户支付单号，同时作为渠道的 out_trade_no，必须全局唯一且可重放
     * @param payMethod 归一化后的支付方式，真实渠道可忽略，演示渠道据此生成对应的模拟码
     * @param amount    支付金额，以订单金额为准（调用方已校验，不由前端决定）
     * @param subject   订单标题，展示在用户收银台
     * @return 支付凭证（二维码内容 / 跳转链接）
     */
    PrepayResult prepay(String paymentId, String payMethod, BigDecimal amount, String subject);

    /**
     * 主动查单，用于回调丢失时的兜底对账。
     *
     * @param paymentId 商户支付单号
     * @return 渠道侧的真实状态；渠道返回订单不存在时应返回 {@code closed=true} 而不是抛异常
     */
    PayQueryResult query(String paymentId);

    /**
     * 退款。金额为空表示全额退款。
     *
     * @param paymentId 原支付单号
     * @param amount    退款金额
     * @param reason    退款原因，会展示给用户
     */
    RefundResult refund(String paymentId, BigDecimal amount, String reason);

    /**
     * 解析并校验渠道异步回调。
     *
     * <p><b>这是整个支付链路上最不能出错的一环</b>：回调报文来自公网，任何人都可以向我方
     * 回调地址 POST 一段伪造的「已付款」消息。因此实现必须完成渠道要求的验签 /
     * 解密，验签未通过时返回 {@link CallbackResult#rejected}，<b>绝不能入账</b>。</p>
     *
     * <p>实现约定：</p>
     * <ul>
     *   <li>验签必须先于任何字段判断，不要先看 {@code trade_status} 再验签；</li>
     *   <li>只做「报文 → 结构化结果」的转换与真伪判定，<b>不修改任何业务状态</b>
     *       （入账由业务层调用 {@code PaymentService.markPaid} 完成）；</li>
     *   <li>解析失败、字段缺失、金额格式非法等都返回 {@code rejected}，不要抛异常 ——
     *       异常会让回调接口返回 500，渠道侧只能看到「处理失败」，排查时缺少线索。</li>
     * </ul>
     *
     * @param params 回调原始参数（表单参数或等价键值对）
     * @return 解析结果；{@code signatureValid=false} 表示报文不可信
     */
    CallbackResult verifyCallback(Map<String, String> params);
}
