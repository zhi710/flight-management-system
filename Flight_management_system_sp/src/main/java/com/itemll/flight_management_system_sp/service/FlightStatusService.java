package com.itemll.flight_management_system_sp.service;

import java.util.List;
import java.util.Map;

/**
 * 航班动态服务接口
 */
public interface FlightStatusService {

    /** 查询航班动态 */
    Map<String, Object> getFlightStatus(String flightNo, String date, String departure, String arrival);

    /** 订阅航班动态 */
    void subscribe(Long userId, String flightNo, String date, String channels, String types);

    /** 取消订阅 */
    void unsubscribe(Long subscriptionId, Long userId);

    /** 查询我的订阅列表 */
    List<Map<String, Object>> listSubscriptions(Long userId);
}
