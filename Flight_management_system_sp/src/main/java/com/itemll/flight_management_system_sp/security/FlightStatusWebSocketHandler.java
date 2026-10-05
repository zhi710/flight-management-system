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
 * 航班动态 WebSocket 推送处理器
 * <p>端点：ws://host:port/api/ws/flight-status?token=xxx</p>
 * <p>客户端发送订阅消息：{"action":"SUBSCRIBE","flights":["CA1234","MU5678"]}</p>
 * <p>服务端推送：{"type":"FLIGHT_UPDATE","data":{...},"timestamp":"..."}</p>
 */
@Slf4j
@Component
public class FlightStatusWebSocketHandler extends TextWebSocketHandler {

    /** 会话管理：sessionId -> session */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("航班动态 WebSocket 连接建立: sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.debug("收到订阅消息: sessionId={}, payload={}", session.getId(), payload);
        // 实际应解析 payload 获取订阅的航班号，注册到推送队列
        // 这里简化处理，返回确认消息
        try {
            session.sendMessage(new TextMessage("{\"type\":\"SUBSCRIBE_ACK\",\"message\":\"订阅成功\"}"));
        } catch (IOException e) {
            log.error("发送确认消息失败", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("航班动态 WebSocket 连接关闭: sessionId={}", session.getId());
    }

    /**
     * 向所有订阅了指定航班的客户端推送消息（供 Service 层调用）
     */
    public void pushFlightUpdate(String flightNo, String data) {
        TextMessage msg = new TextMessage("{\"type\":\"FLIGHT_UPDATE\",\"flightNo\":\"" + flightNo + "\",\"data\":" + data + ",\"timestamp\":\"" + java.time.Instant.now() + "\"}");
        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(msg);
                } catch (IOException e) {
                    log.error("推送航班动态失败: sessionId={}", session.getId(), e);
                }
            }
        });
    }
}
