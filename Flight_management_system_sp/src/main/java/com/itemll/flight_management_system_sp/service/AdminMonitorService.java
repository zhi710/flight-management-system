package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.common.result.PageResult;

import java.util.Map;

/**
 * 管理端监控服务接口
 */
public interface AdminMonitorService {
    Map<String, Object> getDashboard();
    PageResult<Map<String, Object>> getAlerts(String level, String type, String status, int page, int pageSize);
    Map<String, Object> getAlertStats();
    void createAlert(String level, String type, String title, String content);
    void resolveAlert(Long alertId, Long adminId);
    Map<String, Object> getStatistics(String dimension, String startDate, String endDate, String groupBy);
}
