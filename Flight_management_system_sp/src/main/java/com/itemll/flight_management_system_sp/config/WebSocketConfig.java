package com.itemll.flight_management_system_sp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.itemll.flight_management_system_sp.security.FlightStatusWebSocketHandler;
import com.itemll.flight_management_system_sp.security.AdminMonitorWebSocketHandler;
import com.itemll.flight_management_system_sp.security.NotificationWebSocketHandler;
import com.itemll.flight_management_system_sp.security.AdminIrregularWebSocketHandler;
import lombok.RequiredArgsConstructor;

/**
 * WebSocket 配置
 * <p>注册 4 个 WebSocket 端点：旅客端航班动态/通知、管理端监控/IROPS。</p>
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final FlightStatusWebSocketHandler flightStatusHandler;
    private final AdminMonitorWebSocketHandler adminMonitorHandler;
    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final AdminIrregularWebSocketHandler adminIrregularWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 旅客端航班动态推送（已有）
        registry.addHandler(flightStatusHandler, "/ws/flight-status")
                .setAllowedOrigins("*");

        // 旅客端通知推送（新）
        registry.addHandler(notificationWebSocketHandler, "/ws/passenger/notification")
                .setAllowedOrigins("*");

        // 管理端监控推送（已有）
        registry.addHandler(adminMonitorHandler, "/admin/ws/monitor")
                .setAllowedOrigins("*");

        // 管理端不正常航班/IROPS推送（新）
        registry.addHandler(adminIrregularWebSocketHandler, "/admin/ws/irregular")
                .setAllowedOrigins("*");
    }
}
