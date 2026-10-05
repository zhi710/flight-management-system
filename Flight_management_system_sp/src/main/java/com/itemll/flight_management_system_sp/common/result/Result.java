package com.itemll.flight_management_system_sp.common.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * 统一返回结果封装（与 JT.md 接口文档 1.2 节完全对齐）
 * <pre>
 * {
 *   "code": 200,
 *   "message": "success",
 *   "data": {},
 *   "timestamp": "2026-06-05T10:30:00Z",
 *   "requestId": "req_abc123def456"
 * }
 * </pre>
 *
 * @param <T> 数据类型
 */
@Data
@Schema(description = "统一返回结果")
public class Result<T> implements Serializable {

    @Schema(description = "业务状态码，200 表示成功")
    private int code;

    @Schema(description = "提示信息")
    private String message;

    @Schema(description = "响应数据")
    private T data;

    @Schema(description = "服务器时间戳（ISO 8601）", example = "2026-06-05T10:30:00Z")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant timestamp = Instant.now();

    @Schema(description = "请求唯一标识", example = "req_abc123def456")
    private String requestId = "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    // ==================== 构造方法 ====================

    public Result() {}

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ==================== 静态工厂方法 ====================

    /** 成功（无数据） */
    public static <T> Result<T> ok() {
        return new Result<>(200, "success", null);
    }

    /** 成功（带数据） */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    /** 成功（自定义消息 + 数据） */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, message, data);
    }

    /** 失败（自定义错误码和消息） */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败（默认 500） */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    /** 参数错误 400 */
    public static <T> Result<T> badRequest(String message) {
        return new Result<>(400, message, null);
    }

    /** 未授权 401 */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(401, message, null);
    }

    /** 权限不足 403 */
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(403, message, null);
    }

    /** 资源不存在 404 */
    public static <T> Result<T> notFound(String message) {
        return new Result<>(404, message, null);
    }
}
