package com.itemll.flight_management_system_sp.service;

import java.util.List;
import java.util.Map;

public interface CrewComplianceService {

    /**
     * 合规状态自动刷新：① 按 expire_date 重算每项资质状态（VALID / EXPIRING_SOON / EXPIRED）
     * ② 飞行部机组资质全部失效时自动置为停飞 GROUNDED，恢复后自动转回 STANDBY。
     * 排班、动态、合规看板等入口调用，保证展示与校验始终基于最新状态。
     */
    void refreshComplianceStatus();

    List<Map<String, Object>> getCrewFlightSummary(Long crewId, int days);

    List<Map<String, Object>> getComplianceWarnings();

    Map<String, Object> getDashboard();

    void recordFlightTime(Long crewId, Long flightId, String role, int flightMinutes, int dutyMinutes);
}
