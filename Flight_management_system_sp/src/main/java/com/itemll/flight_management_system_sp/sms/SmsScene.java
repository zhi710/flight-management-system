package com.itemll.flight_management_system_sp.sms;

import cn.hutool.core.util.StrUtil;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;

/**
 * 验证码用途
 * <p>用途必须参与 Redis key 的拼装。改造前校验侧把 key 后缀写死成 {@code register}，
 * 导致「登录场景写入 {@code :login}、校验却读 {@code :register}」永远读不到值
 * —— 短信验证码登录实际是失效的，只有万能码能绕过。用枚举收敛用途，从类型上杜绝这类问题。</p>
 */
public enum SmsScene {

    /** 注册 */
    REGISTER("register", "注册"),
    /** 登录 */
    LOGIN("login", "登录"),
    /** 重置密码（接口尚未开放，仅用于接收前端传参并给出明确提示） */
    RESET_PASSWORD("resetPassword", "重置密码");

    private final String code;
    private final String label;

    SmsScene(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 前端传入的用途标识，同时作为 Redis key 的后缀 */
    public String code() {
        return code;
    }

    /** 中文用途，用于日志与提示 */
    public String label() {
        return label;
    }

    /**
     * 解析前端传入的用途。
     *
     * @param raw 前端传的 type，大小写与首尾空格不敏感
     * @throws BusinessException 为空或不在白名单内 —— 必须显式拒绝，不能默认落到某个用途上，
     *                           否则非法 type 会静默写出一批无人读取的 Redis key
     */
    public static SmsScene of(String raw) {
        if (StrUtil.isBlank(raw)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码用途不能为空");
        }
        String value = raw.trim();
        for (SmsScene scene : values()) {
            if (scene.code.equalsIgnoreCase(value)) {
                return scene;
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的验证码用途：" + raw);
    }

    @Override
    public String toString() {
        return code;
    }
}
