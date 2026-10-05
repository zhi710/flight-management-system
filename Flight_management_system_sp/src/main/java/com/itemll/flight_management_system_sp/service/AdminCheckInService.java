package com.itemll.flight_management_system_sp.service;

import java.util.Map;

/**
 * 管理端值机服务接口
 */
public interface AdminCheckInService {
    Map<String, Object> searchFlight(String flightNo, String date);
    Map<String, Object> getCheckInInfo(Long flightId);
    void openCheckIn(Long flightId);
    void closeCheckIn(Long flightId);
    void manualCheckIn(Long flightId, Integer passengerIndex, Integer seatRow, String seatColumn);
    void cancelCheckIn(Long flightId, Integer passengerIndex);
    void autoAssignSeats(Long flightId);
}
