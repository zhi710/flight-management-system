package com.itemll.flight_management_system_sp.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付渠道配置（对应 application.yml 中的 payment.* ）
 * <p>设计原则：所有渠道相关参数（模式、开关、密钥、网关）集中在此，
 * 切换沙箱/生产只改配置文件，不改任何 Java 代码。</p>
 */
@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    /** 运行模式：mock（演示，不调用真实渠道）/ live（真实渠道） */
    private String mode = "mock";

    /** 支付单有效期（分钟） */
    private int expireMinutes = 15;

    /** 各支付方式开关，仅 live 模式生效 */
    private Channels channels = new Channels();

    /** 支付宝渠道参数 */
    private Alipay alipay = new Alipay();

    /** 演示兜底开关 */
    private Demo demo = new Demo();

    /**
     * 是否为演示模式。
     * <p>除显式配置 {@code live} 之外一律按演示模式处理 —— 配置写错时宁可走模拟，
     * 也不要误认为是真实模式而拿假凭证去调支付平台。</p>
     */
    public boolean isMockMode() {
        return !"live".equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    /**
     * 是否放行「模拟支付完成」接口。
     *
     * <p>默认<b>跟随运行模式</b>：mock 放行、live 关闭。之所以能单独覆盖，是为了解决
     * 一个真实的场景冲突 —— 答辩/演示时希望走真实支付宝（体现真实对接能力），
     * 但又需要留一个不依赖现场网络与沙箱状态的手动兜底入口。把两者绑死在 mode 上，
     * 就只能二选一：要么演示假码、要么失去兜底。</p>
     *
     * <p><b>安全边界：</b>该开关只控制「模拟入账」这一个接口，不影响预下单、回调验签、
     * 查单兜底等任何真实链路。开启后等同于「任何登录用户都能不付款把订单刷成已支付」，
     * 因此仅允许在演示/验收环境打开，正式上线前必须关闭。</p>
     */
    public boolean isSimulateAllowed() {
        Boolean explicit = demo == null ? null : demo.getSimulateEnabled();
        return explicit != null ? explicit : isMockMode();
    }

    /** 支付单有效期（分钟），配置非法时兜底 15 分钟 */
    public int expireMinutesOrDefault() {
        return expireMinutes > 0 ? expireMinutes : 15;
    }

    /**
     * 启动期体检：配置里最容易出错的两个组合，提前用日志点出来。
     * <p>支付配置一旦配错，症状往往延迟到用户点击支付时才暴露；这里在启动时就把
     * 「真实模式却忘了开渠道」「开了模拟兜底却以为是安全的」两种误配喊出来。</p>
     */
    @PostConstruct
    public void reportConfig() {
        String currentMode = isMockMode() ? "mock（演示）" : "live（真实渠道）";
        log.info("支付模块已加载: mode={}, 可用渠道={}, 模拟支付接口={}",
                currentMode, describeEnabledChannels(), isSimulateAllowed() ? "放行" : "关闭");

        if (isMockMode()) {
            return;
        }

        if (!hasEnabledChannel()) {
            log.warn("支付处于 live 模式，但所有渠道均为关闭状态，用户点击支付会收到「暂未开通」。"
                    + "若这是预期行为可忽略；否则请检查 payment.channels.*.enabled");
        }

        if (isSimulateAllowed()) {
            log.warn("⚠ 提示：当前为 live 模式，但「模拟支付完成」接口处于放行状态 —— "
                    + "任何登录用户都能不付款把订单刷成已支付。仅可用于演示/验收环境，"
                    + "正式上线前请将 payment.demo.simulate-enabled 置为 false 或删除该配置项");
        }
    }

    /** 已开启的渠道名，用于启动日志 */
    private String describeEnabledChannels() {
        StringBuilder sb = new StringBuilder();
        appendIfEnabled(sb, "alipay", channels == null ? null : channels.getAlipay());
        appendIfEnabled(sb, "wechat", channels == null ? null : channels.getWechat());
        appendIfEnabled(sb, "bank-card", channels == null ? null : channels.getBankCard());
        appendIfEnabled(sb, "credit", channels == null ? null : channels.getCredit());
        return sb.length() == 0 ? "无" : sb.toString();
    }

    /** 是否至少有一个渠道处于开启状态（live 模式下全关等于支付不可用） */
    private boolean hasEnabledChannel() {
        return isChannelEnabled(channels == null ? null : channels.getAlipay())
                || isChannelEnabled(channels == null ? null : channels.getWechat())
                || isChannelEnabled(channels == null ? null : channels.getBankCard())
                || isChannelEnabled(channels == null ? null : channels.getCredit());
    }

    private boolean isChannelEnabled(ChannelSwitch sw) {
        return sw != null && sw.isEnabled();
    }

    private void appendIfEnabled(StringBuilder sb, String name, ChannelSwitch sw) {
        if (isChannelEnabled(sw)) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(name);
        }
    }

    @Data
    public static class Channels {
        private ChannelSwitch alipay = new ChannelSwitch();
        private ChannelSwitch wechat = new ChannelSwitch();
        private ChannelSwitch bankCard = new ChannelSwitch();
        private ChannelSwitch credit = new ChannelSwitch();
    }

    @Data
    public static class ChannelSwitch {
        private boolean enabled = false;
    }

    @Data
    public static class Demo {
        /**
         * 是否放行「模拟支付完成」接口。
         * <p>不配置（null）= 跟随 mode（mock 放行 / live 关闭）；</p>
         * <p>true = 强制放行（live 模式下也能用兜底按钮）；false = 强制关闭。</p>
         */
        private Boolean simulateEnabled;
    }

    @Data
    public static class Alipay {
        /** 沙箱/生产应用的 APPID */
        private String appId = "";
        /** 网关地址，沙箱为 https://openapi-sandbox.dl.alipaydev.com/gateway.do */
        private String gatewayUrl = "";
        /** 应用私钥（PKCS8），仅保存在服务端，用于请求加签 */
        private String appPrivateKey = "";
        /** 支付宝公钥，用于回调验签 */
        private String alipayPublicKey = "";
        /** 异步通知地址，必须公网可达 */
        private String notifyUrl = "";
        /** 同步跳转地址，可选 */
        private String returnUrl = "";
        /** 签名算法，固定 RSA2 */
        private String signType = "RSA2";
        /** 订单标题前缀 */
        private String subjectPrefix = "航班订单";
    }
}
