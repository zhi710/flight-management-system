package com.itemll.flight_management_system_sp.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理端 IROPS WebSocket 推送处理器
 * <p>端点：ws://host:port/api/admin/ws/irregular?token=xxx</p>
 * <p>推送消息类型：IRREGULAR_UPDATE / REBOOKING_RESULT / GATE_CHANGE / CREW_COMPLIANCE_ALERT</p>
 */
@Slf4j
@Component
public class AdminIrregularWebSocketHandler extends TextWebSocketHandler {

    private static final CloseStatus AUTH_FAILED = new CloseStatus(4001, "认证失败");

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final JwtUtil jwtUtil;

    public AdminIrregularWebSocketHandler(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extractQueryParam(session.getUri(), "token");
        if (token == null || !jwtUtil.validateToken(token)) {
            log.warn("管理端IROPS WebSocket认证失败: sessionId={}", session.getId());
            closeSession(session);
            return;
        }
        if (!"ADMIN".equals(jwtUtil.getTokenType(token))) {
            log.warn("管理端IROPS WebSocket类型不匹配: sessionId={}", session.getId());
            closeSession(session);
            return;
        }
        sessions.put(session.getId(), session);
        log.info("管理端IROPS WebSocket连接建立: sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.debug("管理端IROPS收到消息: sessionId={}, payload={}", session.getId(), message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("管理端IROPS WebSocket连接关闭: sessionId={}", session.getId());
    }

    /**
     * 向所有管理端推送IROPS消息
     */
    public void pushToAdmins(String type, String data) {
        TextMessage msg = new TextMessage("{\"type\":\"" + type + "\",\"data\":" + data + ",\"timestamp\":\"" + java.time.Instant.now() + "\"}");
        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(msg);
                } catch (IOException e) {
                    log.error("推送IROPS消息失败: sessionId={}", session.getId(), e);
                }
            }
        });
    }

    private String extractQueryParam(URI uri, String name) {
        if (uri == null) return null;
        String query = uri.getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && name.equals(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }

    private void closeSession(WebSocketSession session) {
        try {
            session.close(AUTH_FAILED);
        } catch (IOException e) {
            log.warn("关闭未认证的WebSocket连接失败: sessionId={}", session.getId(), e);
        }
    }
}
