package com.itemll.flight_management_system_sp.common.exception;

import lombok.Getter;

/**
 * 业务异常
 * <p>在 Service 层抛出，由 GlobalExceptionHandler 统一捕获并转换为 Result 响应。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }
}
