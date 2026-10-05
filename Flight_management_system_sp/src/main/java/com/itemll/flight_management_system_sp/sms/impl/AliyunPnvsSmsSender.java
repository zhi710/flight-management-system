package com.itemll.flight_management_system_sp.sms.impl;

import cn.hutool.core.util.StrUtil;
import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.itemll.flight_management_system_sp.config.SmsProperties;
import com.itemll.flight_management_system_sp.sms.SmsScene;
import com.itemll.flight_management_system_sp.sms.SmsSendResult;
import com.itemll.flight_management_system_sp.sms.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 阿里云「短信认证服务」（号码认证服务 PNVS）发送器
 *
 * <p><b>为什么用这个服务而不是普通短信：</b>普通短信服务要求企业资质 + 自建签名 + 模板 +
 * 运营商实名报备，个人开发者无法开通；PNVS 支持个人实名直接调用，平台赠送签名与验证码模板，
 * 免资质、免备案、开通即可用。</p>
 *
 * <p><b>验证码由谁生成：</b>仍然由本项目的业务层生成并写入 Redis
 * （通过 {@code templateParam} 把 {@code {code}} 替换成实际验证码传给渠道），
 * 这样「5 分钟 TTL、用途隔离、失败次数限制」等策略都留在自己的代码里，
 * 也便于统一排查；渠道只承担「把这段文案发出去」的职责。</p>
 *
 * <p><b>客户端懒加载：</b>mock 模式下本类的方法永远不会被调用，因此不会创建客户端、
 * 也不会发起外网请求；即使凭证没配，Spring 容器启动也不会失败。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunPnvsSmsSender implements SmsSender {

    private final SmsProperties smsProperties;

    /** SDK 客户端，线程安全，懒加载后复用 */
    private volatile Client client;

    /** 模板变量里缺少 {code} 占位符时只告警一次，避免刷日志 */
    private volatile boolean templateParamWarned = false;

    @Override
    public String provider() {
        return "ALIYUN";
    }

    @Override
    public boolean available() {
        return smsProperties.getAliyun().available();
    }

    @Override
    public SmsSendResult send(String phone, String code, SmsScene scene) {
        SmsProperties.Aliyun cfg = smsProperties.getAliyun();

        Client sender = clientOrNull();
        if (sender == null) {
            return SmsSendResult.fail(provider(), "CLIENT_INIT_FAILED", "短信客户端初始化失败，请检查 sms.aliyun 配置");
        }

        String templateParam = buildTemplateParam(cfg.getTemplateParam(), code);

        SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                .setPhoneNumber(phone)
                .setSignName(cfg.getSignName().trim())
                .setTemplateCode(cfg.getTemplateCode().trim())
                .setTemplateParam(templateParam);

        try {
            SendSmsVerifyCodeResponse response = sender.sendSmsVerifyCode(request);
            SendSmsVerifyCodeResponseBody body = response == null ? null : response.getBody();

            if (body == null) {
                log.error("[阿里云短信] 响应为空 | 手机号={} | 用途={}", mask(phone), scene.label());
                return SmsSendResult.fail(provider(), "EMPTY_RESPONSE", "渠道返回空响应");
            }

            boolean ok = "OK".equalsIgnoreCase(body.getCode()) || Boolean.TRUE.equals(body.getSuccess());
            String bizId = body.getModel() == null ? null : body.getModel().getBizId();

            if (ok) {
                log.info("[阿里云短信] 验证码已提交 | 手机号={} | 用途={} | bizId={} | 渠道返回={}",
                        mask(phone), scene.label(), bizId, body.getCode());
                return SmsSendResult.ok(provider(), body.getCode(), body.getMessage(), bizId);
            }

            log.error("[阿里云短信] 发送失败 | 手机号={} | 用途={} | code={} | message={} | requestId={}",
                    mask(phone), scene.label(), body.getCode(), body.getMessage(), body.getRequestId());
            return SmsSendResult.fail(provider(), body.getCode(), body.getMessage());

        } catch (TeaException e) {
            // SDK 把渠道业务错误（如签名未报备、余额不足、流控）也以 TeaException 抛出，
            // 这里必须转成失败结果返回，不能把渠道异常类型泄漏给业务层
            log.error("[阿里云短信] 渠道异常 | 手机号={} | 用途={} | code={} | message={}",
                    mask(phone), scene.label(), e.getCode(), e.getMessage());
            return SmsSendResult.fail(provider(), e.getCode(), e.getMessage());

        } catch (Exception e) {
            // 网络超时、DNS、SSL、响应解析等非业务异常
            log.error("[阿里云短信] 调用异常 | 手机号={} | 用途={} | {}: {}",
                    mask(phone), scene.label(), e.getClass().getSimpleName(), e.getMessage());
            return SmsSendResult.fail(provider(), "SDK_ERROR", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 客户端懒加载（双检锁）。
     * <p>初始化失败返回 null 并由调用方转成失败结果 —— 这样「配置写错」表现为一条明确的
     * 业务提示，而不是把用户页面上炸出一个 500。</p>
     */
    private Client clientOrNull() {
        Client local = client;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            local = client;
            if (local != null) {
                return local;
            }
            SmsProperties.Aliyun cfg = smsProperties.getAliyun();
            Config config = new Config()
                    .setAccessKeyId(cfg.getAccessKeyId().trim())
                    .setAccessKeySecret(cfg.getAccessKeySecret().trim())
                    .setEndpoint(cfg.getEndpoint().trim())
                    .setConnectTimeout(cfg.getConnectTimeoutMs())
                    .setReadTimeout(cfg.getReadTimeoutMs());
            try {
                local = new Client(config);
                client = local;
                log.info("阿里云短信客户端已初始化: endpoint={}, signName={}, templateCode={}",
                        cfg.getEndpoint(), cfg.getSignName(), cfg.getTemplateCode());
            } catch (Exception e) {
                log.error("阿里云短信客户端初始化失败: {}: {}", e.getClass().getSimpleName(), e.getMessage());
                return null;
            }
            return local;
        }
    }

    /**
     * 把 {@code {code}} 替换成实际验证码。
     *
     * <p>如果配的模板里没有 {@code {code}} 占位符，渠道会自行随机生成验证码并下发，
     * 与我们 Redis 里存的完全不是同一个值，表现为「用户收到的验证码总是错的」。
     * 这种问题只看日志很难定位，因此这里显式告警一次。</p>
     */
    private String buildTemplateParam(String template, String code) {
        if (StrUtil.isBlank(template)) {
            return "{\"code\":\"" + code + "\"}";
        }
        if (!template.contains("{code}") && !templateParamWarned) {
            templateParamWarned = true;
            log.warn("⚠ sms.aliyun.template-param 中没有 {{code}} 占位符，渠道可能自行生成验证码，"
                    + "导致用户收到的验证码与本系统保存的不一致。当前模板：{}", template);
        }
        return template.replace("{code}", code);
    }

    /** 日志脱敏：138****0001 */
    private String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
