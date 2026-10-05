package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.common.result.PageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface GroundHandlingService {

    List<Map<String, Object>> getNodesForFlight(Long flightId);

    PageResult<Map<String, Object>> getGroundHandlingList(String flightNo, String date, String status, int page, int pageSize);

    void updateNodeTime(Long nodeId, String field, String value, Long operatorId);

    Map<String, Object> getTodaySummary();

    void generateNodesForFlight(Long flightId);

    void completeNode(Long flightId, String nodeCode, LocalDateTime actualTime, Long operatorId);

    void autoCompleteByFlightStatus(Long flightId, String newStatus);

    void departFlight(Long flightId, Long operatorId);

    void arriveFlight(Long flightId, Long operatorId);

    List<Map<String, Object>> getFlightGroundSummary(String date);
}
