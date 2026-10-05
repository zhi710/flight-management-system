package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.dto.CheckInDTO;
import java.util.List;
import java.util.Map;

/**
 * 值机服务接口
 */
public interface CheckInService {

    /** 查询可值机航班 */
    List<Map<String, Object>> getAvailableCheckIn(Long userId);

    /** 获取座位图 */
    Map<String, Object> getSeatMap(Long flightId);

    /** 选择座位 */
    void selectSeat(String orderId, Integer passengerIndex, Integer row, String column);

    /** 办理值机 */
    Map<String, Object> doCheckIn(CheckInDTO dto, Long userId);

    /** 获取电子登机牌 */
    Map<String, Object> getBoardingPass(String checkinId);

    /** 取消值机 */
    Map<String, Object> cancelCheckIn(String orderId, Long userId);
}
