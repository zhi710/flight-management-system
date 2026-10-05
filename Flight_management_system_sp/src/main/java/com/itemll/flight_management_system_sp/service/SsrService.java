package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.common.result.PageResult;

import java.util.List;
import java.util.Map;

public interface SsrService {

    List<Map<String, Object>> getAvailableSsrCodes(String category);

    PageResult<Map<String, Object>> getSsrList(Long flightId, String status, int page, int pageSize);

    void submitSsrRequest(Long orderId, Long orderPassengerId, String ssrCode, String remark);

    void processSsrRequest(Long requestId, String action, Long adminId);

    List<Map<String, Object>> getSsrByOrder(Long orderId);
}
