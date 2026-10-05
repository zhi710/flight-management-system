package com.itemll.flight_management_system_sp.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理端监控 WebSocket 推送处理器
 * <p>端点：ws://host:port/api/admin/ws/monitor?token=xxx</p>
 * <p>推送消息类型：FLIGHT_STATUS / ALERT / DASHBOARD_UPDATE / CHECKIN_UPDATE</p>
 */
@Slf4j
@Component
public class AdminMonitorWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("管理端监控 WebSocket 连接建立: sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.debug("管理端监控收到消息: {}", message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("管理端监控 WebSocket 连接关闭: sessionId={}", session.getId());
    }

    /**
     * 向所有管理端客户端推送监控消息
     *
     * @param type 消息类型：FLIGHT_STATUS / ALERT / DASHBOARD_UPDATE / CHECKIN_UPDATE
     * @param data JSON 数据
     */
    public void pushMonitorUpdate(String type, String data) {
        TextMessage msg = new TextMessage("{\"type\":\"" + type + "\",\"data\":" + data + ",\"timestamp\":\"" + java.time.Instant.now() + "\"}");
        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(msg);
                } catch (IOException e) {
                    log.error("推送监控消息失败: sessionId={}", session.getId(), e);
                }
            }
        });
    }
}
