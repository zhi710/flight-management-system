package com.itemll.flight_management_system_sp.service;

import java.util.List;
import java.util.Map;

/**
 * 管理端投诉建议服务
 */
public interface AdminFeedbackService {

    /** 反馈列表（可按状态过滤） */
    List<Map<String, Object>> listFeedback(String status);

    /** 回复反馈（回复后通知旅客） */
    void replyFeedback(Long feedbackId, String reply);
}
