package com.itemll.flight_management_system_sp.security;

import com.itemll.flight_management_system_sp.service.AdminAuthService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * 管理端接口权限过滤器。
 * <p>在 JWT 认证过滤器之后执行（Order 2）。按「URL 前缀 + HTTP 方法」映射出所需权限码，
 * 与当前管理员（账号→角色→权限）持有的权限码比对，不满足则返回 403。</p>
 * <p>规则与后台前端路由的 meta.permission / 侧边栏菜单分组保持一致。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class AdminPermissionFilter implements Filter {

    private final AdminAuthService adminAuthService;

    private static final String ADMIN_PREFIX = "/api/admin/";

    /** 无需校验业务权限、仅需登录的路径（认证由 JwtAuthFilter 负责） */
    private static final List<String> AUTH_ONLY_PATHS = List.of(
            ADMIN_PREFIX + "auth/login",
            ADMIN_PREFIX + "auth/logout",
            ADMIN_PREFIX + "system/profile",
            ADMIN_PREFIX + "system/change-password",
            // 监控大屏是各角色登录后的落点（前端 /dashboard 无权限码），仅需登录即可读取
            ADMIN_PREFIX + "monitor/dashboard",
            ADMIN_PREFIX + "ws/"
    );

    /** (路径前缀, 读所需权限码, 写所需权限码) —— 前缀按长度降序匹配，先取最长命中 */
    private static final List<String[]> RULES = List.of(
            new String[]{ADMIN_PREFIX + "system/permissions", "system:role", "system:role"},
            new String[]{ADMIN_PREFIX + "system/master-data", "system:data", "system:data"},
            new String[]{ADMIN_PREFIX + "system/change-password", "", ""},
            new String[]{ADMIN_PREFIX + "system/config", "system:config", "system:config"},
            new String[]{ADMIN_PREFIX + "system/users", "system:user", "system:user"},
            new String[]{ADMIN_PREFIX + "system/roles", "system:role", "system:role"},
            new String[]{ADMIN_PREFIX + "system/logs", "system:user", "system:user"},
            new String[]{ADMIN_PREFIX + "system/profile", "", ""},
            new String[]{ADMIN_PREFIX + "flights", "flight:read", "flight:write"},
            new String[]{ADMIN_PREFIX + "crew", "crew:read", "crew:write"},
            new String[]{ADMIN_PREFIX + "checkin", "passenger:read", "passenger:write"},
            new String[]{ADMIN_PREFIX + "gates", "passenger:read", "passenger:write"},
            new String[]{ADMIN_PREFIX + "ssr", "passenger:read", "passenger:write"},
            new String[]{ADMIN_PREFIX + "members", "passenger:read", "passenger:write"},
            new String[]{ADMIN_PREFIX + "passengers", "passenger:read", "passenger:write"},
            new String[]{ADMIN_PREFIX + "feedback", "feedback:read", "feedback:write"},
            new String[]{ADMIN_PREFIX + "seats", "passenger:read", "passenger:write"},
            new String[]{ADMIN_PREFIX + "tickets", "ticket:read", "ticket:write"},
            new String[]{ADMIN_PREFIX + "fares", "ticket:read", "ticket:write"},
            new String[]{ADMIN_PREFIX + "irop", "monitor:read", "monitor:write"},
            new String[]{ADMIN_PREFIX + "ground", "monitor:read", "monitor:write"},
            new String[]{ADMIN_PREFIX + "alerts", "monitor:read", "monitor:write"},
            new String[]{ADMIN_PREFIX + "monitor", "monitor:read", "monitor:write"},
            new String[]{ADMIN_PREFIX + "statistics", "monitor:read", "monitor:read"},
            new String[]{ADMIN_PREFIX + "reports", "report:read", "report:read"}
    );

    /** 规则按前缀长度降序，保证先匹配更具体的路径（如 system/users 优先于 system） */
    private static final List<String[]> RULES_SORTED = RULES.stream()
            .sorted(Comparator.comparingInt((String[] r) -> r[0].length()).reversed())
            .toList();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String uri = request.getRequestURI();

        // 只拦截管理端接口
        if (!uri.startsWith(ADMIN_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        // 放行 OPTIONS 预检与仅登录接口
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }
        for (String p : AUTH_ONLY_PATHS) {
            if (uri.startsWith(p)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // JwtAuthFilter 未注入管理员 ID（未登录/非管理端 token），交由上层逻辑兜底
        Long adminId = (Long) request.getAttribute("currentAdminId");
        if (adminId == null) {
            chain.doFilter(request, response);
            return;
        }

        String required = resolveRequired(uri, method);
        if (required == null) {
            // 未命中任何规则的管理端接口，统一拒绝（超级管理员持有全部权限不受影响）
            writeForbidden(response);
            return;
        }
        if (required.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        boolean allowed = adminAuthService.listPermissionCodes(adminId).contains(required);
        if (!allowed) {
            log.warn("权限拦截 [{}] {} 需要 {} adminId={}", method, uri, required, adminId);
            writeForbidden(response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** 解析路径所需权限码；GET 视为读，其余视为写；规则前缀长度为 0 的视为仅登录（空权限码） */
    private String resolveRequired(String uri, String method) {
        for (String[] rule : RULES_SORTED) {
            if (uri.startsWith(rule[0])) {
                if (rule[1].isEmpty() && rule[2].isEmpty()) return "";
                boolean write = !"GET".equalsIgnoreCase(method);
                return write ? rule[2] : rule[1];
            }
        }
        return null;
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":403,\"message\":\"无权限执行此操作\",\"data\":null,\"timestamp\":" + System.currentTimeMillis() + "}"
        );
    }
}
