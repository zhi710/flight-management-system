package com.itemll.flight_management_system_sp.service;

import java.util.List;
import java.util.Map;

/**
 * 管理端机组服务接口
 */
public interface AdminCrewService {
    List<Map<String, Object>> getCrewSchedule(String startDate, String endDate, String department, String qualification);
    void createSchedule(Long crewId, Long flightId, String role, String date);
    void deleteSchedule(Long scheduleId);
    Map<String, Object> autoSchedule(String startDate, String endDate, boolean skipWeekends);
    Map<String, Object> getCrewQualifications(Long crewId);
    void addQualification(Long crewId, Map<String, Object> qual);
    void updateQualification(Long crewId, Long qualId, Map<String, Object> qual);
    void deleteQualification(Long crewId, Long qualId);
    List<Map<String, Object>> listCrews(String department, String keyword);
    List<Map<String, Object>> getCrewDynamics(String status, String crewId);

    /** 新增机组人员，返回新人员 ID */
    Long createCrew(Map<String, Object> crew);

    /** 修改机组人员资料（工号/姓名/性别/部门/联系方式/状态） */
    void updateCrew(Long id, Map<String, Object> crew);

    /** 删除机组人员（软删；仍有今天及以后的排班时拒绝） */
    void deleteCrew(Long id);
}
