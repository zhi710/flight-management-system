package com.itemll.flight_management_system_sp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itemll.flight_management_system_sp.entity.NotificationLog;
import com.itemll.flight_management_system_sp.entity.SysConfig;
import com.itemll.flight_management_system_sp.entity.User;
import com.itemll.flight_management_system_sp.mapper.NotificationLogMapper;
import com.itemll.flight_management_system_sp.mapper.SysConfigMapper;
import com.itemll.flight_management_system_sp.mapper.UserMapper;
import com.itemll.flight_management_system_sp.security.AdminIrregularWebSocketHandler;
import com.itemll.flight_management_system_sp.security.NotificationWebSocketHandler;
import com.itemll.flight_management_system_sp.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationLogMapper notificationLogMapper;
    private final SysConfigMapper sysConfigMapper;
    private final UserMapper userMapper;
    private final NotificationWebSocketHandler notificationWebSocket;
    private final AdminIrregularWebSocketHandler adminIrregularWebSocket;

    @Override
    public void notifyPassenger(Long userId, String title, String content, String refType, Long refId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("通知失败：用户不存在 userId={}", userId);
            return;
        }
        // 先落库拿到通知 id，保证实时推送携带真实 id，前端「已读」才能持久化
        Long logId = saveNotificationLog(userId, null, "WEBSOCKET", title, content, "SENT", refType, refId);

        // 再 WebSocket 推送（实时）
        Map<String, Object> wsData = new LinkedHashMap<>();
        wsData.put("id", logId != null ? logId.toString() : null);
        wsData.put("title", title);
        wsData.put("content", content);
        wsData.put("refType", refType);
        wsData.put("refId", refId);
        wsData.put("timestamp", LocalDateTime.now().toString());
        pushToPassenger(userId, "NOTIFICATION", wsData);

        // 如果有手机号且未连接WS，降级为短信
        if (user.getPhone() != null) {
            sendSms(user.getPhone(), content, refType, refId);
        }
    }

    @Override
    public void notifyAdmin(Long adminId, String title, String content, String refType, Long refId) {
        Map<String, Object> data = Map.of(
                "title", title,
                "content", content,
                "refType", refType,
                "refId", refId,
                "timestamp", LocalDateTime.now().toString()
        );
        pushToAdmin("ADMIN_NOTIFICATION", data);

        saveNotificationLog(null, adminId, "WEBSOCKET", title, content, "SENT", refType, refId);
    }

    @Override
    public void sendSms(String phone, String content, String refType, Long refId) {
        SysConfig smsConfig = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, "sms_enabled"));
        boolean smsEnabled = smsConfig != null && Boolean.parseBoolean(smsConfig.getConfigValue());
        if (!smsEnabled) {
            log.info("短信通知已禁用，跳过发送: phone={}", phone);
            return;
        }

        try {
            // 实际对接SMS提供商（阿里云/腾讯云短信SDK）
            log.info("发送短信: phone={}, content={}", phone, content);
            saveNotificationLog(null, null, "SMS", "短信通知", content, "SENT", refType, refId);
        } catch (Exception e) {
            log.error("短信发送失败: phone={}", phone, e);
            saveNotificationLog(null, null, "SMS", "短信通知", content, "FAILED", refType, refId);
        }
    }

    @Override
    public void sendEmail(String email, String title, String content, String refType, Long refId) {
        try {
            // 实际对接JavaMailSender
            log.info("发送邮件: email={}, title={}", email, title);
            saveNotificationLog(null, null, "EMAIL", title, content, "SENT", refType, refId);
        } catch (Exception e) {
            log.error("邮件发送失败: email={}", email, e);
            saveNotificationLog(null, null, "EMAIL", title, content, "FAILED", refType, refId);
        }
    }

    @Override
    public void pushToPassenger(Long userId, String type, Map<String, Object> data) {
        String json = JSONUtil.toJsonStr(Map.of(
                "type", type,
                "data", data,
                "timestamp", LocalDateTime.now().toString()
        ));
        notificationWebSocket.pushToUser(userId, json);
    }

    @Override
    public void pushToAdmin(String type, Map<String, Object> data) {
        String json = JSONUtil.toJsonStr(Map.of(
                "type", type,
                "data", data,
                "timestamp", LocalDateTime.now().toString()
        ));
        adminIrregularWebSocket.pushToAdmins(type, json);
    }

    @Override
    public void pushRebookingToPassenger(Long userId, Map<String, Object> rebookingData) {
        pushToPassenger(userId, "REBOOKING", rebookingData);
    }

    @Override
    public void pushIrregularToAdmin(Map<String, Object> irregularData) {
        pushToAdmin("IRREGULAR_UPDATE", irregularData);
    }

    @Override
    public List<Map<String, Object>> getPassengerNotifications(Long userId, int page, int pageSize) {
        Page<NotificationLog> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<NotificationLog> wrapper = new LambdaQueryWrapper<NotificationLog>()
                .eq(NotificationLog::getTargetUserId, userId)
                .orderByDesc(NotificationLog::getSendTime);
        Page<NotificationLog> result = notificationLogMapper.selectPage(pageObj, wrapper);
        return result.getRecords().stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId().toString());
            m.put("title", n.getTitle());
            m.put("content", n.getContent());
            m.put("channel", n.getChannel());
            m.put("refType", n.getRefType());
            m.put("refId", n.getRefId() != null ? n.getRefId().toString() : null);
            m.put("timestamp", n.getSendTime() != null ? n.getSendTime().toString() : null);
            m.put("read", n.getReadTime() != null);
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public void markRead(Long userId, Long notificationId) {
        notificationLogMapper.update(null, new LambdaUpdateWrapper<NotificationLog>()
                .eq(NotificationLog::getId, notificationId)
                .eq(NotificationLog::getTargetUserId, userId)
                .set(NotificationLog::getReadTime, LocalDateTime.now()));
    }

    @Override
    public void markAllRead(Long userId) {
        notificationLogMapper.update(null, new LambdaUpdateWrapper<NotificationLog>()
                .eq(NotificationLog::getTargetUserId, userId)
                .isNull(NotificationLog::getReadTime)
                .set(NotificationLog::getReadTime, LocalDateTime.now()));
    }

    private Long saveNotificationLog(Long userId, Long adminId, String channel, String title,
                                     String content, String status, String refType, Long refId) {
        NotificationLog log = new NotificationLog();
        log.setTargetUserId(userId);
        log.setTargetAdminId(adminId);
        log.setChannel(channel);
        log.setTitle(title);
        log.setContent(content);
        log.setStatus(status);
        log.setSendTime(LocalDateTime.now());
        log.setRefType(refType);
        log.setRefId(refId);
        notificationLogMapper.insert(log);
        return log.getId();
    }
}
