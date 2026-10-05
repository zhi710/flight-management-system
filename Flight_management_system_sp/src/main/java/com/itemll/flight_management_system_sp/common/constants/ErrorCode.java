package com.itemll.flight_management_system_sp.common.constants;

/**
 * 业务错误码常量
 * <p>错误码规则：5 位数字，前缀按模块划分</p>
 * <ul>
 *   <li>100xx — 航班相关</li>
 *   <li>200xx — 用户相关</li>
 *   <li>300xx — 机组相关</li>
 *   <li>400xx — 订单/支付相关</li>
 * </ul>
 */
public final class ErrorCode {

    private ErrorCode() {}

    // ==================== 通用 ====================
    public static final int SUCCESS = 200;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;
    public static final int TOO_MANY_REQUESTS = 429;
    public static final int INTERNAL_ERROR = 500;

    // ==================== 航班模块 100xx ====================
    public static final int FLIGHT_NOT_FOUND = 10001;
    public static final int FLIGHT_FULL = 10002;
    public static final int SEAT_OCCUPIED = 10003;
    public static final int ORDER_EXPIRED = 10004;
    public static final int PAYMENT_FAILED = 10005;
    public static final int REFUND_CHANGE_NOT_ALLOWED = 10006;

    // ==================== 用户模块 200xx ====================
    public static final int USER_NOT_FOUND = 20001;
    public static final int PASSWORD_ERROR = 20002;
    public static final int CAPTCHA_ERROR = 20003;
    public static final int DOCUMENT_EXISTS = 20004;
    /** 短信验证码发送失败（渠道侧失败或未配置），与「验证码错误」区分开，便于前端提示与排查 */
    public static final int SMS_SEND_FAILED = 20005;
    /** 证件号未通过格式校验（长度、地区码、出生日期或校验位不合法） */
    public static final int DOCUMENT_INVALID = 20006;
    /** 账号未实名：需先到个人中心填写姓名与证件号，之后才能购票 */
    public static final int REALNAME_REQUIRED = 20007;
    /** 订单中缺少本人：需有一位乘客的姓名与证件号与实名档案完全一致 */
    public static final int SELF_PASSENGER_REQUIRED = 20008;
    /** 该证件号已被其他账号实名占用 */
    public static final int DOCUMENT_OCCUPIED = 20009;

    // ==================== 机组模块 300xx ====================
    public static final int CREW_QUAL_NOT_MET = 30001;
    public static final int CREW_SCHEDULE_CONFLICT = 30002;
    public static final int CREW_FLIGHT_TIME_EXCEEDED = 30003;
}
