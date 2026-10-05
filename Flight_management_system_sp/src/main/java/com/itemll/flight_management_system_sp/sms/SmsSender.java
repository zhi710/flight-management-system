package com.itemll.flight_management_system_sp.sms;

/**
 * 短信发送抽象
 * <p>把「把一条验证码交给渠道发出去」这件事从业务逻辑里隔离出来。业务侧
 * （{@code AuthServiceImpl}）只依赖本接口与 {@link SmsSenderRouter}，不感知任何具体服务商；
 * 换服务商（阿里云 / 腾讯云 / 第三方平台）只需实现本接口，业务代码零改动。</p>
 *
 * <p><b>职责边界：</b>实现类只负责「调用渠道并翻译返回值」。</p>
 * <ul>
 *   <li><b>不碰 Redis、不碰业务状态</b>：验证码的生成、存储、TTL、校验全部由业务层负责。
 *       这样即便以后换成「渠道托管验证码」（如阿里云的 CheckSmsVerifyCode），
 *       业务层也只是改校验方式，不影响渠道实现。</li>
 *   <li><b>不抛业务异常</b>：渠道返回失败请用 {@link SmsSendResult#fail} 表达。
 *       实现内部若捕获到异常，必须转换成失败结果返回，不能把 SDK 异常类型泄漏到上层
 *       —— 上层需要区分「渠道说不行」与「程序崩了」两种情况。</li>
 *   <li><b>客户端单例</b>：渠道 SDK 客户端较重量级且线程安全，应复用，
 *       禁止每次调用 new 一个（可参考 {@link com.itemll.flight_management_system_sp.pay.impl.AlipayClientProvider}
 *       的懒加载做法）。</li>
 *   <li><b>不打印完整手机号与验证码</b>：真实模式下日志会长期留存，必须脱敏。</li>
 * </ul>
 */
public interface SmsSender {

    /** 服务商标识：MOCK / ALIYUN，用于日志与路由 */
    String provider();

    /** 凭证是否配置齐全。返回 false 时路由器给出「暂未配置」提示，而不是抛空指针 */
    boolean available();

    /**
     * 发送验证码短信。
     *
     * @param phone 手机号（已由业务层校验过格式）
     * @param code  验证码明文，由业务层生成并已写入 Redis
     * @param scene 用途，部分渠道的不同用途会走不同模板
     * @return 发送结果；渠道失败时 {@code success=false}，调用方据此回滚
     */
    SmsSendResult send(String phone, String code, SmsScene scene);
}
