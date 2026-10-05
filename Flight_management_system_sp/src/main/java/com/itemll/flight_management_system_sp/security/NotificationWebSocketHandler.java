package com.itemll.flight_management_system_sp.security;

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
 * 旅客端通知 WebSocket 推送处理器
 * <p>端点：ws://host:port/api/ws/passenger/notification?token=xxx&userId=xxx</p>
 * <p>服务端推送：{"type":"NOTIFICATION","data":{...},"timestamp":"..."}</p>
 * <p>每个session关联一个userId，方便按用户推送</p>
 */
@Slf4j
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final CloseStatus AUTH_FAILED = new CloseStatus(4001, "认证失败");

    /** userId -> session */
    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    /** sessionId -> userId */
    private final Map<String, Long> sessionUsers = new ConcurrentHashMap<>();
    private final JwtUtil jwtUtil;

    public NotificationWebSocketHandler(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extractQueryParam(session.getUri(), "token");
        if (token == null || !jwtUtil.validateToken(token)) {
            log.warn("旅客通知WebSocket认证失败: sessionId={}", session.getId());
            closeSession(session);
            return;
        }
        if (!"USER".equals(jwtUtil.getTokenType(token))) {
            log.warn("旅客通知WebSocket类型不匹配: sessionId={}", session.getId());
            closeSession(session);
            return;
        }
        // userId 从 JWT subject 获取，避免 query 参数中带 "U" 前缀导致的格式问题
        Long userId = jwtUtil.getUserId(token);
        if (userId == null) {
            log.warn("旅客通知WebSocket token中无userId: sessionId={}", session.getId());
            closeSession(session);
            return;
        }
        userSessions.put(userId, session);
        sessionUsers.put(session.getId(), userId);
        log.info("旅客通知WebSocket连接建立: sessionId={}, userId={}", session.getId(), userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.debug("旅客通知WS收到消息: sessionId={}, payload={}", session.getId(), message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = sessionUsers.remove(session.getId());
        if (userId != null) {
            userSessions.remove(userId);
        }
        log.info("旅客通知WebSocket连接关闭: sessionId={}, userId={}", session.getId(), userId);
    }

    /**
     * 向指定旅客推送通知
     */
    public void pushToUser(Long userId, String messageJson) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(messageJson));
            } catch (IOException e) {
                log.error("推送通知给用户{}失败: sessionId={}", userId, session.getId(), e);
            }
        }
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
