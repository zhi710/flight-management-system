package com.itemll.flight_management_system_sp.sms;

import lombok.Builder;
import lombok.Getter;

/**
 * 短信发送结果
 * <p>渠道侧的失败（余额不足、手机号在黑名单、模板参数不合法…）通过本对象返回，
 * 而不是抛异常 —— 这些都属于「可预期的业务失败」，调用方需要据此回滚 Redis、
 * 释放限流冷却并给用户一句明确的提示。</p>
 */
@Getter
@Builder
public class SmsSendResult {

    /** 是否已成功提交给渠道 */
    private final boolean success;

    /** 渠道标识：MOCK / ALIYUN */
    private final String provider;

    /** 渠道返回码；成功时通常为 OK */
    private final String providerCode;

    /** 渠道返回描述，失败时用于日志与排查 */
    private final String providerMessage;

    /** 渠道流水号，出现问题时可凭它找云厂商核对 */
    private final String bizId;

    public static SmsSendResult ok(String provider, String providerCode, String providerMessage, String bizId) {
        return SmsSendResult.builder()
                .success(true)
                .provider(provider)
                .providerCode(providerCode)
                .providerMessage(providerMessage)
                .bizId(bizId)
                .build();
    }

    public static SmsSendResult fail(String provider, String providerCode, String providerMessage) {
        return SmsSendResult.builder()
                .success(false)
                .provider(provider)
                .providerCode(providerCode)
                .providerMessage(providerMessage)
                .build();
    }
}
