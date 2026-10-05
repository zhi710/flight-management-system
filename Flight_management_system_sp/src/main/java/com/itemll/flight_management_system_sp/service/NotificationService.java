package com.itemll.flight_management_system_sp.service;

import java.util.List;
import java.util.Map;

/**
 * 通知服务：短信/邮件/APP推送/WebSocket实时推送
 */
public interface NotificationService {

    /**
     * 发送通知给指定旅客（自动选渠道：有WebSocket用WS，否则SMS/EMAIL）
     */
    void notifyPassenger(Long userId, String title, String content, String refType, Long refId);

    /**
     * 发送通知给指定管理员
     */
    void notifyAdmin(Long adminId, String title, String content, String refType, Long refId);

    /**
     * 发送短信通知
     */
    void sendSms(String phone, String content, String refType, Long refId);

    /**
     * 发送邮件通知
     */
    void sendEmail(String email, String title, String content, String refType, Long refId);

    /**
     * WebSocket推送通知给旅客端
     */
    void pushToPassenger(Long userId, String type, Map<String, Object> data);

    /**
     * WebSocket推送通知给管理端
     */
    void pushToAdmin(String type, Map<String, Object> data);

    /**
     * 推送航班改签结果给旅客
     */
    void pushRebookingToPassenger(Long userId, Map<String, Object> rebookingData);

    /**
     * 推送不正常航班处理结果给管理端
     */
    void pushIrregularToAdmin(Map<String, Object> irregularData);

    /**
     * 查询旅客通知历史
     */
    List<Map<String, Object>> getPassengerNotifications(Long userId, int page, int pageSize);

    /**
     * 标记单条通知已读
     */
    void markRead(Long userId, Long notificationId);

    /**
     * 全部标记已读
     */
    void markAllRead(Long userId);
}
