package com.itemll.flight_management_system_sp.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import com.itemll.flight_management_system_sp.common.util.ClientIpUtil;
import com.itemll.flight_management_system_sp.entity.SysLog;
import com.itemll.flight_management_system_sp.mapper.SysLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ControllerLogAspect {

    private final SysLogMapper sysLogMapper;

    private static final int MAX_PARAM_LENGTH = 500;
    private static final int MAX_RESULT_LENGTH = 1000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Around("@annotation(com.itemll.flight_management_system_sp.common.annotation.OperationLog)")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OperationLog ann = method.getAnnotation(OperationLog.class);
        String className = signature.getDeclaringType().getSimpleName();
        String fullSignature = buildFullSignature(className, method);

        String httpMethod = "UNKNOWN";
        String requestUri = "";
        String clientIp = "unknown";
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            httpMethod = request.getMethod();
            requestUri = request.getRequestURI();
            clientIp = ClientIpUtil.of(request);
        }

        String params = ann.saveParams() ? buildParamsDetail(joinPoint, signature) : "无";

        long startTime = System.currentTimeMillis();
        log.info("======> 接口调用开始 ======");
        log.info("  请求: [{}] {}", httpMethod, requestUri);
        log.info("  来源 IP: {}", clientIp);
        log.info("  方法签名: {}", fullSignature);
        log.info("  请求参数: {}", params);

        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - startTime;
            String resultSummary = buildResultSummary(result);
            log.info("  返回值: {}", resultSummary);
            log.info("  耗时: {}ms", elapsed);
            log.info("======> 接口调用完成 ======");
            saveLog(requestUri, ann, httpMethod, clientIp, fullSignature, params, result, null);
            return result;
        } catch (Throwable ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("  耗时: {}ms", elapsed);
            log.error("  异常类型: {}", ex.getClass().getSimpleName());
            log.error("  异常信息: {}", ex.getMessage());
            log.error("======> 接口调用异常 ======");
            saveLog(requestUri, ann, httpMethod, clientIp, fullSignature, params, null, ex.getMessage());
            throw ex;
        }
    }

    private String buildFullSignature(String className, Method method) {
        Parameter[] parameters = method.getParameters();
        StringBuilder sb = new StringBuilder();
        sb.append(className).append(".").append(method.getName()).append("(");
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(parameters[i].getType().getSimpleName())
              .append(" ")
              .append(parameters[i].getName());
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildParamsDetail(ProceedingJoinPoint joinPoint, MethodSignature signature) {
        Object[] args = joinPoint.getArgs();
        String[] paramNames = signature.getParameterNames();

        if (args == null || args.length == 0) return "无";

        Map<String, String> paramInfo = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof HttpServletRequest) continue;
            if (args[i] instanceof HttpServletResponse) continue;

            String name = (paramNames != null && i < paramNames.length) ? paramNames[i] : "arg" + i;
            String value = serializeArg(args[i]);
            paramInfo.put(name, value);
        }

        if (paramInfo.isEmpty()) return "无";

        StringBuilder sb = new StringBuilder();
        paramInfo.forEach((k, v) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(k).append("=").append(v);
        });
        return sb.toString();
    }

    private String serializeArg(Object arg) {
        if (arg == null) return "null";
        try {
            String json = objectMapper.writeValueAsString(arg);
            if (json.length() > MAX_PARAM_LENGTH) {
                return json.substring(0, MAX_PARAM_LENGTH) + "...(截断)";
            }
            return json;
        } catch (Exception e) {
            String str = arg.toString();
            if (str.length() > MAX_PARAM_LENGTH) {
                return str.substring(0, MAX_PARAM_LENGTH) + "...(截断)";
            }
            return str;
        }
    }

    private String buildResultSummary(Object result) {
        if (result == null) return "null";
        try {
            String json = objectMapper.writeValueAsString(result);
            if (json.length() > MAX_RESULT_LENGTH) {
                return json.substring(0, MAX_RESULT_LENGTH) + "...(截断)";
            }
            return json;
        } catch (Exception e) {
            String str = result.toString();
            if (str.length() > MAX_RESULT_LENGTH) {
                return str.substring(0, MAX_RESULT_LENGTH) + "...(截断)";
            }
            return str;
        }
    }

    private void saveLog(String uri, OperationLog ann, String httpMethod, String ip, String signature,
                          String params, Object result, String errorMsg) {
        if (uri != null && uri.contains("/admin/system/logs")) return;
        try {
            SysLog sl = new SysLog();
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                Long adminId = (Long) req.getAttribute("currentAdminId");
                sl.setOperatorId(adminId);
                String name = (String) req.getAttribute("currentAdminName");
                sl.setOperatorName(name != null ? name : (adminId != null ? String.valueOf(adminId) : "system"));
            }
            sl.setModule(ann.module());
            sl.setAction(ann.action());
            String target = ann.target();
            if (target.isEmpty() && uri != null) {
                target = extractResourcePath(uri);
            }
            sl.setTarget(target);
            sl.setDetail(cleanParams(params));
            sl.setIp(ip);
            sysLogMapper.insert(sl);
        } catch (Exception e) {
            log.warn("保存操作日志失败", e);
        }
    }

    private String extractResourcePath(String uri) {
        String path = uri;
        if (path.startsWith("/api/")) path = path.substring(5);
        if (path.startsWith("/admin/")) path = path.substring(7);
        return path;
    }

    private String cleanParams(String params) {
        if (params == null || params.isEmpty() || "无".equals(params)) return "";
        StringBuilder sb = new StringBuilder();
        String[] parts = params.split(", ");
        for (String part : parts) {
            if (part.contains("=null") || part.endsWith("=")) continue;
            if (sb.length() > 0) sb.append("; ");
            if (part.length() > 80) {
                sb.append(part, 0, 80).append("…");
            } else {
                sb.append(part);
            }
        }
        if (sb.isEmpty()) return "查看";
        String result = sb.toString();
        return result.length() > 200 ? result.substring(0, 200) + "…" : result;
    }
}
