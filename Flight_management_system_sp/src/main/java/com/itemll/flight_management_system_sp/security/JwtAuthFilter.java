package com.itemll.flight_management_system_sp.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JWT 认证过滤器
 * <p>从请求头 Authorization 中提取 Bearer Token，解析后将用户信息放入 Request Attribute，
 * 供后续 Controller 使用。不通过则直接返回 401。</p>
 * <p>白名单路径（如登录、注册、Swagger 等）无需 Token。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class JwtAuthFilter implements Filter {

    private final JwtUtil jwtUtil;
    private final RedisTokenService redisTokenService;

    /** 白名单路径前缀（包含 context-path /api） */
    private static final String[] WHITE_LIST = {
            "/api/auth/",
            "/api/admin/auth/",
            "/api/flights/search",
            "/api/flights/hot-routes",
            "/api/flights/deals",
            "/api/carousel",            // 首页轮播图（仅 GET 只读，公开，与 hot-routes/deals 一致）
            "/api/flights/",            // GET /flights/{id} 航班详情
            "/api/ssr/codes",           // SSR 代码字典（公开）
            // 支付渠道异步回调：渠道服务器没有 JWT，必须放行。
            // 安全性由渠道验签保证（PaymentGateway.verifyCallback），
            // 验签不过的报文会被直接拒绝，因此放行不等于开放「免费支付」入口。
            "/api/payments/callback/",
            "/api/system/instance",     // 仅本次启动的实例标识（前端开发模式据此判断后端是否重启）
            // 帮助中心仅放行公开只读接口。切勿放行整个 "/api/help/"：
            // 白名单只匹配路径前缀、不看请求方法，会把 POST /api/help/feedback 一并放行，
            // 导致 currentUserId 为 null，反馈落库 user_id=NULL，旅客端「我的反馈」永远查不到。
            "/api/help/faq",
            "/api/help/refund-policy",
            "/api/help/baggage-rules",
            "/api/images/",             // 上传文件访问
            "/api/ws/",                 // WebSocket 升级路径（token 在 query 参数中，handler 内自校验）
            "/api/admin/ws/",           // 管理端 WebSocket 升级路径
            "/swagger-ui",
            "/v3/api-docs",
            "/swagger-resources",
            "/webjars/",
            "/favicon"
    };

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        // 放行白名单
        if (isWhiteListed(uri, method)) {
            chain.doFilter(request, response);
            return;
        }

        // 提取 Authorization 头
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "缺少 Authorization 头");
            return;
        }

        String token = authHeader.substring(7);

        // 验证 Token 有效性
        if (!jwtUtil.validateToken(token)) {
            writeUnauthorized(response, "Token 无效或已过期");
            return;
        }

        try {
            Long userId = jwtUtil.getUserId(token);
            String tokenType = jwtUtil.getTokenType(token);

            boolean redisValid = true;
            try {
                if ("USER".equals(tokenType)) {
                    redisValid = redisTokenService.validateUserToken(userId, token);
                } else if ("ADMIN".equals(tokenType)) {
                    redisValid = redisTokenService.validateAdminToken(userId, token);
                }
            } catch (Exception e) {
                log.warn("Redis 不可用，跳过 Token 状态校验: {}", e.getMessage());
            }

            if (!redisValid) {
                writeUnauthorized(response, "Token 已失效，请重新登录");
                return;
            }

            if ("USER".equals(tokenType)) {
                request.setAttribute("currentUserId", userId);
                request.setAttribute("userType", "USER");
            } else if ("ADMIN".equals(tokenType)) {
                request.setAttribute("currentAdminId", userId);
                Claims claims = jwtUtil.parseToken(token);
                String username = claims.get("extra", String.class);
                if (username != null) request.setAttribute("currentAdminName", username);
                request.setAttribute("userType", "ADMIN");
            } else {
                writeUnauthorized(response, "Token 类型不合法");
                return;
            }

            chain.doFilter(request, response);
        } catch (Exception e) {
            log.error("Token 解析异常: {}", e.getMessage());
            writeUnauthorized(response, "Token 解析失败");
        }
    }

    /**
     * 判断是否在白名单中
     */
    private boolean isWhiteListed(String uri, String method) {
        // 精确匹配：航班动态查询（GET 公开，POST/DELETE 需认证）
        if ("GET".equalsIgnoreCase(method) && "/api/flight-status".equals(uri)) {
            return true;
        }

        for (String prefix : WHITE_LIST) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回 401 JSON 响应
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":401,\"message\":\"" + message + "\",\"data\":null,\"timestamp\":" + System.currentTimeMillis() + "}"
        );
    }
}
