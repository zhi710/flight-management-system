package com.itemll.flight_management_system_sp.service;

import java.util.List;
import java.util.Map;

/**
 * 管理端报表服务接口
 */
public interface AdminReportService {
    Map<String, Object> getOperationReport(String type, String startDate, String endDate, String format);
    Map<String, Object> getRevenueReport(String type, String startDate, String endDate);
    List<Map<String, Object>> getCustomReports();
    void createCustomReport(Map<String, Object> report);
    void generateReport(Long reportId);
}
