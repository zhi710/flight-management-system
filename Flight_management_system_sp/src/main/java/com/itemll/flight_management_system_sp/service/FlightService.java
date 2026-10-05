package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.dto.FlightSearchDTO;
import java.util.List;
import java.util.Map;

/**
 * 航班服务接口
 */
public interface FlightService {

    /** 搜索航班 */
    Map<String, Object> searchFlights(FlightSearchDTO dto);

    /** 获取航班详情 */
    Map<String, Object> getFlightDetail(Long flightId);

    /** 热门航线 */
    List<Map<String, Object>> getHotRoutes(String city, int limit);

    /** 特价机票 */
    List<Map<String, Object>> getDeals(String city, int limit);

    /** 航班大屏（指定日期的航班动态，date 为空取当天） */
    List<Map<String, Object>> getFlightBoard(String date);

    /** 机场列表（大屏机场选择器用） */
    List<Map<String, Object>> listAirports();
}
