package com.itemll.flight_management_system_sp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.enums.FlightStatus;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.entity.Flight;
import com.itemll.flight_management_system_sp.entity.FlightSubscription;
import com.itemll.flight_management_system_sp.mapper.FlightMapper;
import com.itemll.flight_management_system_sp.mapper.FlightSubscriptionMapper;
import com.itemll.flight_management_system_sp.service.FlightStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 航班动态服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightStatusServiceImpl implements FlightStatusService {

    private final FlightMapper flightMapper;
    private final FlightSubscriptionMapper subscriptionMapper;

    @Override
    public Map<String, Object> getFlightStatus(String flightNo, String date, String departure, String arrival) {
        LambdaQueryWrapper<Flight> wrapper = new LambdaQueryWrapper<Flight>()
                .eq(Flight::getFlightNo, flightNo)
                .eq(Flight::getFlightDate, LocalDate.parse(date))
                .eq(Flight::getDeleted, 0);

        Flight flight = flightMapper.selectOne(wrapper);
        if (flight == null) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "航班不存在");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("flightNo", flight.getFlightNo());
        result.put("date", date);
        result.put("status", flight.getStatus());
        result.put("statusText", getStatusText(flight.getStatus()));
        result.put("checkinOpen", flight.isCheckinOpen(LocalDateTime.now()));

        Map<String, Object> dep = new HashMap<>();
        dep.put("airport", flight.getDepartureAirport());
        dep.put("terminal", flight.getDepartureTerminal());
        dep.put("gate", flight.getDepartureGate());
        dep.put("scheduled", flight.getDepartureTime());
        result.put("departure", dep);

        Map<String, Object> arr = new HashMap<>();
        arr.put("airport", flight.getArrivalAirport());
        arr.put("terminal", flight.getArrivalTerminal());
        arr.put("scheduled", flight.getArrivalTime());
        result.put("arrival", arr);

        result.put("aircraft", flight.getAircraftId() != null ? "B738" : null);

        return result;
    }

    @Override
    public void subscribe(Long userId, String flightNo, String date, String channels, String types) {
        FlightSubscription sub = new FlightSubscription();
        sub.setUserId(userId);
        sub.setFlightNo(flightNo);
        sub.setFlightDate(LocalDate.parse(date));
        sub.setChannels(channels);
        sub.setTypes(types);
        sub.setStatus(1);
        subscriptionMapper.insert(sub);
        log.info("航班动态订阅成功: userId={}, flightNo={}", userId, flightNo);
    }

    @Override
    public void unsubscribe(Long subscriptionId, Long userId) {
        subscriptionMapper.delete(
                new LambdaQueryWrapper<FlightSubscription>()
                        .eq(FlightSubscription::getId, subscriptionId)
                        .eq(FlightSubscription::getUserId, userId));
    }

    @Override
    public List<Map<String, Object>> listSubscriptions(Long userId) {
        List<FlightSubscription> subs = subscriptionMapper.selectList(
                new LambdaQueryWrapper<FlightSubscription>()
                        .eq(FlightSubscription::getUserId, userId)
                        .eq(FlightSubscription::getStatus, 1)
                        .orderByDesc(FlightSubscription::getCreateTime));

        List<Map<String, Object>> result = new ArrayList<>();
        for (FlightSubscription s : subs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId().toString());
            m.put("flightNo", s.getFlightNo());
            m.put("flightDate", s.getFlightDate() != null ? s.getFlightDate().toString() : null);
            m.put("channels", s.getChannels() != null ? Arrays.asList(s.getChannels().split(",")) : new ArrayList<>());
            m.put("types", s.getTypes() != null ? Arrays.asList(s.getTypes().split(",")) : new ArrayList<>());
            m.put("status", s.getStatus());
            m.put("createTime", s.getCreateTime() != null ? s.getCreateTime().toString() : null);
            result.add(m);
        }
        return result;
    }

    private String getStatusText(String status) {
        return FlightStatus.labelOf(status);
    }
}
