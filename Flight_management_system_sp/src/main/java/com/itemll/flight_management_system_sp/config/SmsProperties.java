package com.itemll.flight_management_system_sp.config;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短信服务配置（对应 application.yml 中的 sms.* ）
 * <p>设计原则与 {@link PaymentProperties} 保持一致：服务商、凭证、限流参数全部集中在此，
 * 「演示模式」与「真实短信」之间只改配置文件，不改任何 Java 代码。</p>
 *
 * <p>配置项写错时的兜底取向：<b>宁可走演示模式</b>。因为走演示最多是验证码不到手机，
 * 而误判为真实模式会拿着空凭证去调云厂商接口，直接抛异常给用户。</p>
 */
@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "sms")
public class SmsProperties {

    /** 短信服务商：mock（演示，仅打印日志）/ aliyun（阿里云短信认证服务） */
    private String provider = "mock";

    /** 验证码有效期（分钟），同时作为写入 Redis 的 TTL */
    private int codeExpireMinutes = 5;

    /** 验证码位数 */
    private int codeLength = 6;

    /** 发送与校验的限流参数（真实短信按条计费，必须防刷） */
    private Throttle throttle = new Throttle();

    /** 演示兜底开关 */
    private Demo demo = new Demo();

    /** 阿里云短信认证服务参数 */
    private Aliyun aliyun = new Aliyun();

    /**
     * 是否为演示模式。
     * <p>除显式配置 {@code aliyun} 之外一律按演示模式处理 —— 配置拼错时不会误发真实短信。</p>
     */
    public boolean isMockProvider() {
        return !"aliyun".equalsIgnoreCase(provider == null ? "" : provider.trim());
    }

    /**
     * 是否放行「万能验证码」。
     *
     * <p>默认<b>跟随服务商</b>：mock 放行、aliyun 关闭。可以单独覆盖的原因与支付侧的
     * {@code payment.demo.simulate-enabled} 完全一致 —— 演示/答辩时希望走真实链路（体现真实对接能力），
     * 但又需要留一个不依赖现场手机信号与余额的手动兜底入口。</p>
     *
     * <p><b>安全边界：</b>开启后，任何手机号用该验证码都能通过校验，等同于「无需短信即可注册/登录」。
     * 因此仅允许在演示/验收环境打开，正式上线前必须关闭。</p>
     */
    public boolean isUniversalCodeAllowed() {
        Boolean explicit = demo == null ? null : demo.getUniversalCodeEnabled();
        return explicit != null ? explicit : isMockProvider();
    }

    /**
     * 万能验证码；未配置或为空串时视为「关闭万能码」。
     * <p>返回 null 表示不需要放行，调用方据此跳过比对。</p>
     */
    public String universalCode() {
        if (demo == null || !isUniversalCodeAllowed()) {
            return null;
        }
        String code = demo.getUniversalCode();
        return StrUtil.isBlank(code) ? null : code.trim();
    }

    /** 验证码有效期（分钟），配置非法时兜底 5 分钟 */
    public int codeExpireMinutesOrDefault() {
        return codeExpireMinutes > 0 ? codeExpireMinutes : 5;
    }

    /** 验证码位数，配置非法时兜底 6 位 */
    public int codeLengthOrDefault() {
        return codeLength > 0 ? codeLength : 6;
    }

    /**
     * 启动期体检：把最容易造成「现象诡异、排查半天」的几种误配提前喊出来。
     * <p>短信配置配错的典型症状是「用户点了没收到」，如果等到那时才发现是凭证没填或渠道没开，
     * 排查成本远高于启动时看一行日志。</p>
     */
    @PostConstruct
    public void reportConfig() {
        log.info("短信模块已加载: provider={}, 验证码有效期={}分钟, 万能码={}, 限流={}秒冷却/单号{}条每日/单IP{}条每小时",
                isMockProvider() ? "mock（演示，不发送真实短信）" : "aliyun（阿里云短信认证服务）",
                codeExpireMinutesOrDefault(),
                universalCode() == null ? "关闭" : "已开启（" + universalCode() + "）",
                throttle.getCooldownSecondsOrDefault(),
                throttle.getDailyLimitPerPhoneOrDefault(),
                throttle.getHourlyLimitPerIpOrDefault());

        if (isMockProvider()) {
            if (!isUniversalCodeAllowed()) {
                log.warn("短信处于 mock 模式但万能码已关闭，本地演示将拿不到验证码 —— "
                        + "请查看后端日志中的「[演示短信]」输出，或开启 sms.demo.universal-code-enabled");
            }
            return;
        }

        if (!aliyun.available()) {
            log.error("短信已配置为 aliyun，但 sms.aliyun 的 access-key-id / sign-name / template-code 不完整，"
                    + "用户获取验证码会直接失败。请检查配置，或改回 sms.provider=mock");
        }

        if (isUniversalCodeAllowed()) {
            log.warn("⚠ 提示：当前为真实短信模式，但「万能验证码」处于放行状态 —— "
                    + "任何手机号用该验证码都能通过校验，等同于免短信注册/登录。"
                    + "仅可用于演示/验收环境，正式上线前请将 sms.demo.universal-code-enabled 置为 false");
        }
    }

    @Data
    public static class Throttle {
        /** 同一手机号的发送冷却时间（秒），前端 60 秒倒计时对应的服务端强校验 */
        private int cooldownSeconds = 60;

        /** 同一手机号每日发送上限（条） */
        private int dailyLimitPerPhone = 10;

        /** 同一 IP 每小时发送上限（条） */
        private int hourlyLimitPerIp = 20;

        /** 同一手机号校验失败次数上限，超过后临时锁定，防 6 位验证码被暴力枚举 */
        private int maxVerifyAttempts = 5;

        /** 校验失败超限后的锁定时长（分钟） */
        private int verifyLockMinutes = 15;

        public int getCooldownSecondsOrDefault() {
            return cooldownSeconds > 0 ? cooldownSeconds : 60;
        }

        public int getDailyLimitPerPhoneOrDefault() {
            return dailyLimitPerPhone > 0 ? dailyLimitPerPhone : 10;
        }

        public int getHourlyLimitPerIpOrDefault() {
            return hourlyLimitPerIp > 0 ? hourlyLimitPerIp : 20;
        }

        public int getMaxVerifyAttemptsOrDefault() {
            return maxVerifyAttempts > 0 ? maxVerifyAttempts : 5;
        }

        public int getVerifyLockMinutesOrDefault() {
            return verifyLockMinutes > 0 ? verifyLockMinutes : 15;
        }
    }

    @Data
    public static class Demo {
        /**
         * 万能验证码内容，演示时不必真的收短信。
         * <p>留空 = 关闭万能码。</p>
         */
        private String universalCode = "123456";

        /**
         * 是否放行万能验证码。
         * <p>不配置（null）= 跟随 provider（mock 放行 / aliyun 关闭）；</p>
         * <p>true = 强制放行（真实短信模式下也能兜底）；false = 强制关闭。</p>
         */
        private Boolean universalCodeEnabled;
    }

    @Data
    public static class Aliyun {
        /** RAM 用户的 AccessKeyId（不要用主账号密钥） */
        private String accessKeyId = "";
        /** RAM 用户的 AccessKeySecret */
        private String accessKeySecret = "";
        /** 服务接入地址，短信认证服务固定为 dypnsapi.aliyuncs.com */
        private String endpoint = "dypnsapi.aliyuncs.com";
        /** 短信签名，使用控制台「赠送签名」的原文，不要带【】 */
        private String signName = "";
        /** 验证码模板 Code，使用控制台「赠送模板」的编号 */
        private String templateCode = "";
        /**
         * 模板变量 JSON，{code} 会被替换成实际验证码。
         * <p>赠送模板默认含「验证码」与「有效期」两个变量，变量名请以控制台模板详情为准，
         * 若为其它名称（如 min 写成 minutes），只改这一行即可，无需改代码。</p>
         */
        private String templateParam = "{\"code\":\"{code}\",\"min\":\"5\"}";
        /** 连接超时（毫秒） */
        private int connectTimeoutMs = 5000;
        /** 读取超时（毫秒） */
        private int readTimeoutMs = 10000;

        /** 凭证是否齐备；不齐时发送方直接给出明确提示，而不是抛空指针 */
        public boolean available() {
            return StrUtil.isNotBlank(accessKeyId)
                    && StrUtil.isNotBlank(accessKeySecret)
                    && StrUtil.isNotBlank(signName)
                    && StrUtil.isNotBlank(templateCode);
        }
    }
}
