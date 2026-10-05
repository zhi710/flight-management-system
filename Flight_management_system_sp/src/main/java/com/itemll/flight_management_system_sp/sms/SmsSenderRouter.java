package com.itemll.flight_management_system_sp.sms;

import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.config.SmsProperties;
import com.itemll.flight_management_system_sp.sms.impl.MockSmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 短信发送器路由器
 * <p>业务层唯一入口：返回「当前该用哪个发送器」。业务代码里不应出现
 * {@code if ("aliyun".equals(provider))} 之类的服务商判断。</p>
 *
 * <p>路由规则：</p>
 * <ol>
 *   <li>{@code sms.provider=mock}（默认）：一律走 {@link MockSmsSender}，只打日志不发短信，
 *       保证无凭证环境下的本地演示与离线构建。</li>
 *   <li>{@code sms.provider=aliyun}：按 provider 精确匹配到对应实现；
 *       命中但凭证不全时明确告知「暂未配置完整」，而不是拿空 AccessKey 去调渠道。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsSenderRouter {

    private final SmsProperties smsProperties;
    private final MockSmsSender mockSmsSender;
    /** 所有真实服务商实现 */
    private final List<SmsSender> senders;

    /**
     * 取出当前生效的发送器。
     *
     * @throws BusinessException 服务商未识别或凭证未配置完整
     */
    public SmsSender route() {
        if (smsProperties.isMockProvider()) {
            return mockSmsSender;
        }

        String wanted = smsProperties.getProvider().trim();
        for (SmsSender sender : senders) {
            if (sender.provider().equalsIgnoreCase(wanted)) {
                if (!sender.available()) {
                    log.error("短信服务商 {} 已被选中但凭证不完整，无法下发验证码", wanted);
                    throw new BusinessException(ErrorCode.SMS_SEND_FAILED,
                            "短信服务尚未配置完整，暂时无法下发验证码，请联系管理员");
                }
                log.debug("短信发送路由到服务商 {}", sender.provider());
                return sender;
            }
        }

        log.error("未找到短信服务商实现: configured={}, 已装配={} 个", wanted, senders.size());
        throw new BusinessException(ErrorCode.SMS_SEND_FAILED, "不支持的短信服务商：" + wanted);
    }

    /** 当前是否演示模式，业务层可据此决定提示文案（例如是否提示去后端日志取验证码） */
    public boolean isMockProvider() {
        return smsProperties.isMockProvider();
    }
}
