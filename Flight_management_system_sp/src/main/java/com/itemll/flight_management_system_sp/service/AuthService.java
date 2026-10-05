package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.dto.LoginDTO;
import com.itemll.flight_management_system_sp.dto.RegisterDTO;
import java.util.Map;

/**
 * 认证服务接口
 */
public interface AuthService {

    /** 用户注册 */
    Map<String, Object> register(RegisterDTO dto);

    /** 用户登录（手机号+密码） */
    Map<String, Object> login(LoginDTO dto);

    /** 短信验证码登录 */
    Map<String, Object> loginBySms(String phone, String smsCode);

    /**
     * 发送短信验证码
     *
     * @param phone    手机号
     * @param type     用途：register / login / resetPassword
     * @param clientIp 请求方 IP，用于发送频次限制（取不到可传 null）
     * @return 验证码有效期（秒），供前端展示倒计时
     */
    int sendSmsCode(String phone, String type, String clientIp);

    /** 刷新 Token */
    Map<String, Object> refreshToken(String refreshToken);

    /** 用户登出 */
    void logout(Long userId);
}
