package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.common.result.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 不正常航班（IROPS）管理服务
 */
public interface AdminIropService {

    /** 查询不正常航班操作记录 */
    PageResult<Map<String, Object>> getIropList(String flightNo, String type, String status, int page, int pageSize);

    /** 查询受影响的旅客列表 */
    List<Map<String, Object>> getAffectedPassengers(Long flightId);

    /** 查询自动改签结果列表 */
    PageResult<Map<String, Object>> getRebookingList(Long flightId, String status, int page, int pageSize);

    /** 确认/拒绝自动改签 */
    void confirmRebooking(Long rebookingId, String action, Long adminId);

    /** 手动发起改签 */
    Map<String, Object> manualRebook(Long orderId, Long newFlightId, String newCabinClass, Long adminId);

    /** 查看补偿规则 */
    Map<String, Object> getCompensationRules();

    /** 获取IATA延误原因代码列表 */
    List<Map<String, String>> getIataDelayCodes();

    /** 重新通知该IROPS操作的所有受影响旅客 */
    void notifyPassengers(Long operationId);
}
