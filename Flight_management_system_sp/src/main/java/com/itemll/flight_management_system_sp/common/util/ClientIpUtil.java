package com.itemll.flight_management_system_sp.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 提取工具
 * <p>部署在 Nginx / 反向代理后面时 {@code getRemoteAddr()} 拿到的是代理地址，
 * 必须优先读转发头，否则「同一 IP 每小时发送上限」这类限流会把所有用户算成同一个 IP
 * —— 一个人触发限流，全网都发不出验证码。</p>
 */
public final class ClientIpUtil {

    private ClientIpUtil() {
    }

    /**
     * 提取客户端真实 IP。
     * <p>取不到时返回 {@code null}（而不是 "unknown" 之类的占位串），
     * 调用方据此跳过 IP 维度的限流，避免把占位串当成一个真实 IP 累计计数。</p>
     */
    public static String of(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        // X-Forwarded-For 可能是「客户端IP, 代理1, 代理2」，第一个才是客户端
        String ip = request.getHeader("X-Forwarded-For");
        if (isValid(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (isValid(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    private static boolean isValid(String ip) {
        return ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip.trim());
    }
}
