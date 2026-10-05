package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.dto.AdminFlightCreateDTO;
import java.util.List;
import java.util.Map;

/**
 * 管理端航班服务接口
 */
public interface AdminFlightService {

    PageResult<Map<String, Object>> getFlightList(String date, String route, String status, String airline, String keyword, int page, int pageSize);
    Map<String, Object> createFlight(AdminFlightCreateDTO dto);
    void updateFlight(Long flightId, AdminFlightCreateDTO dto);
    void deleteFlight(Long flightId);
    void batchOperate(List<Long> flightIds, String action, Map<String, Object> params);
    Map<String, Object> getSchedule(String view, String startDate, String endDate, String route);
    void handleIrregular(Long flightId, String type, String reason, Map<String, Object> newSchedule, boolean notify, Map<String, Object> arrangements);
    List<Map<String, Object>> getFlightLogs(Long flightId);
}
