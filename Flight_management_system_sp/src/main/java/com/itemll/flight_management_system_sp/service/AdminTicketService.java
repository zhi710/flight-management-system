package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.common.result.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 管理端客票服务接口
 */
public interface AdminTicketService {
    PageResult<Map<String, Object>> getBookings(String pnr, String passengerName, String flightNo, int page, int pageSize);
    Map<String, Object> getBookingDetail(String pnr);
    PageResult<Map<String, Object>> getFares(int page, int pageSize);
    void createFare(Map<String, Object> fare);
    void updateFare(Long fareId, Map<String, Object> fare);
    List<Map<String, Object>> getRefunds();
    void approveRefund(String refundId, Map<String, Object> body);
    List<Map<String, Object>> getChanges();
    void approveChange(String changeId, Map<String, Object> body);
}
