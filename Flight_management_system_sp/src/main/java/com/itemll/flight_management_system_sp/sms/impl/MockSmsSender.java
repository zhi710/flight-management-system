package com.itemll.flight_management_system_sp.sms.impl;

import com.itemll.flight_management_system_sp.sms.SmsScene;
import com.itemll.flight_management_system_sp.sms.SmsSendResult;
import com.itemll.flight_management_system_sp.sms.SmsSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 演示短信发送器（mock 模式的唯一实现）
 * <p>不发起任何外网请求，只把验证码打印到服务端日志。这正是改造前的行为
 * （原 `AuthServiceImpl.sendSmsCode` 只依赖 Redis + log），迁移到本类后
 * 保证无凭证环境下的演示流程零改动可用。</p>
 *
 * <p>与 {@code MockPayGateway} 不同的是，这里 <b>不能</b>返回「无效结果来避免被真实模式选中」的做法 ——
 * 短信只有「发出去」一件事，没有支付方式那种多路选择，所以路由改由
 * {@link com.itemll.flight_management_system_sp.sms.SmsSenderRouter} 按 provider 精确匹配，
 * 真实模式永远不会走到本类。</p>
 */
@Slf4j
@Component
public class MockSmsSender implements SmsSender {

    @Override
    public String provider() {
        return "MOCK";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public SmsSendResult send(String phone, String code, SmsScene scene) {
        // 演示模式下验证码必须可见，否则本地根本没法完成注册/登录流程
        log.info("[演示短信] 未调用任何短信网关 | 手机号={} | 用途={} | 验证码={} | 请直接使用或从前端输入",
                mask(phone), scene.label(), code);
        return SmsSendResult.ok(provider(), "MOCK_OK", "演示模式，验证码仅打印在服务端日志", null);
    }

    /** 日志脱敏：138****0001 */
    private String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
