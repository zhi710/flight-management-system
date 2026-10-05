package com.itemll.flight_management_system_sp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.entity.*;
import com.itemll.flight_management_system_sp.mapper.*;
import com.itemll.flight_management_system_sp.service.AdminCheckInService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itemll.flight_management_system_sp.security.FlightStatusWebSocketHandler;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端值机服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCheckInServiceImpl implements AdminCheckInService {

    private final FlightMapper flightMapper;
    private final OrderMapper orderMapper;
    private final OrderPassengerMapper orderPassengerMapper;
    private final AirportMapper airportMapper;
    private final FlightStatusWebSocketHandler webSocketHandler;

    @Override
    public Map<String, Object> searchFlight(String flightNo, String date) {
        LambdaQueryWrapper<Flight> wrapper = new LambdaQueryWrapper<Flight>()
                .eq(Flight::getFlightNo, flightNo)
                .eq(date != null, Flight::getFlightDate, java.time.LocalDate.parse(date))
                .eq(Flight::getDeleted, 0)
                .last("LIMIT 1");
        Flight flight = flightMapper.selectOne(wrapper);
        if (flight == null) throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "未找到该航班");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("flightId", flight.getId().toString());
        result.put("flightNo", flight.getFlightNo());
        result.put("date", flight.getFlightDate().toString());

        Airport dep = airportMapper.selectByCode(flight.getDepartureAirport());
        Airport arr = airportMapper.selectByCode(flight.getArrivalAirport());
        result.put("departure", dep != null ? dep.getName() + "(" + dep.getCode() + ")" : flight.getDepartureAirport());
        result.put("arrival", arr != null ? arr.getName() + "(" + arr.getCode() + ")" : flight.getArrivalAirport());
        return result;
    }

    @Override
    public Map<String, Object> getCheckInInfo(Long flightId) {
        Flight flight = flightMapper.selectById(flightId);
        if (flight == null) throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "航班不存在");

        boolean isPast = flight.getDepartureTime() != null && flight.getDepartureTime().isBefore(LocalDateTime.now());

        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getFlightId, flightId)
                        .in(Order::getStatus, "PAID", "ISSUED", "CHECKED_IN")
                        .eq(Order::getDeleted, 0));

        // 机场信息
        Airport depAirport = airportMapper.selectByCode(flight.getDepartureAirport());
        Airport arrAirport = airportMapper.selectByCode(flight.getArrivalAirport());

        // 旅客列表
        List<Map<String, Object>> passengerList = new ArrayList<>();
        int totalPassengers = 0;
        int checkedInCount = 0;
        for (Order order : orders) {
            List<OrderPassenger> passengers = orderPassengerMapper.selectList(
                    new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, order.getId()));
            for (OrderPassenger p : passengers) {
                totalPassengers++;
                boolean isCheckedIn = "CHECKED_IN".equals(p.getCheckinStatus());
                if (isCheckedIn) checkedInCount++;
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("name", p.getPassengerName());
                pm.put("checkedIn", isCheckedIn);
                pm.put("seat", p.getSeatRow() != null ? p.getSeatRow() + (p.getSeatColumn() != null ? p.getSeatColumn() : "") : null);
                passengerList.add(pm);
            }
        }

        boolean checkinOpen = flight.isCheckinOpen(LocalDateTime.now());

        Map<String, Object> result = new HashMap<>();
        result.put("flightNo", flight.getFlightNo());
        result.put("status", flight.getStatus());
        result.put("checkinOpen", checkinOpen);
        result.put("totalPassengers", totalPassengers);
        result.put("checkedIn", checkedInCount);
        result.put("isPast", isPast);
        result.put("departure", Map.of("airportName", depAirport != null ? depAirport.getName() : flight.getDepartureAirport()));
        result.put("arrival", Map.of("airportName", arrAirport != null ? arrAirport.getName() : flight.getArrivalAirport()));
        result.put("passengers", passengerList);
        return result;
    }

    @Override
    @Transactional
    public void openCheckIn(Long flightId) {
        Flight flight = flightMapper.selectById(flightId);
        if (flight == null) throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "航班不存在");
        checkFlightNotPast(flight);
        flight.setCheckinStatus(Flight.CHECKIN_OPEN);
        flightMapper.updateById(flight);
        webSocketHandler.pushFlightUpdate(flight.getFlightNo(),
                "{\"status\":\"" + flight.getStatus() + "\",\"checkinOpen\":true}");
        log.info("开放值机: flightNo={}", flight.getFlightNo());
    }

    @Override
    @Transactional
    public void closeCheckIn(Long flightId) {
        Flight flight = flightMapper.selectById(flightId);
        if (flight == null) throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "航班不存在");
        checkFlightNotPast(flight);
        flight.setCheckinStatus(Flight.CHECKIN_CLOSED);
        flightMapper.updateById(flight);
        webSocketHandler.pushFlightUpdate(flight.getFlightNo(),
                "{\"status\":\"" + flight.getStatus() + "\",\"checkinOpen\":false}");
        log.info("关闭值机: flightNo={}", flight.getFlightNo());
    }

    @Override
    @Transactional
    public void manualCheckIn(Long flightId, Integer passengerIndex, Integer seatRow, String seatColumn) {
        Flight flight = flightMapper.selectById(flightId);
        if (flight == null) throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "航班不存在");
        checkFlightNotPast(flight);

        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getFlightId, flightId)
                        .in(Order::getStatus, "PAID", "ISSUED", "CHECKED_IN")
                        .eq(Order::getDeleted, 0));
        // 按 getCheckInInfo 相同顺序构建扁平旅客列表，匹配前端传入的 passengerIndex
        int flatIdx = 0;
        for (Order order : orders) {
            List<OrderPassenger> passengers = orderPassengerMapper.selectList(
                    new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, order.getId()));
            for (OrderPassenger p : passengers) {
                if (flatIdx == (passengerIndex != null ? passengerIndex : 0)) {
                    p.setCheckinStatus("CHECKED_IN");
                    p.setSeatRow(seatRow);
                    p.setSeatColumn(seatColumn);
                    orderPassengerMapper.updateById(p);
                    order.setStatus("CHECKED_IN");
                    orderMapper.updateById(order);
                    return;
                }
                flatIdx++;
            }
        }
        log.warn("手动值机未找到匹配旅客: flightId={}, passengerIndex={}", flightId, passengerIndex);
    }

    @Override
    @Transactional
    public void cancelCheckIn(Long flightId, Integer passengerIndex) {
        Flight flight = flightMapper.selectById(flightId);
        if (flight == null) throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "航班不存在");
        checkFlightNotPast(flight);

        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getFlightId, flightId)
                        .in(Order::getStatus, "CHECKED_IN")
                        .eq(Order::getDeleted, 0));
        int flatIdx = 0;
        for (Order order : orders) {
            List<OrderPassenger> passengers = orderPassengerMapper.selectList(
                    new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, order.getId()));
            for (OrderPassenger p : passengers) {
                if (flatIdx == (passengerIndex != null ? passengerIndex : 0)) {
                    p.setCheckinStatus("NOT_CHECKED_IN");
                    p.setSeatRow(null);
                    p.setSeatColumn(null);
                    orderPassengerMapper.updateById(p);
                    order.setStatus("PAID");
                    orderMapper.updateById(order);
                    return;
                }
                flatIdx++;
            }
        }
        log.warn("取消值机未找到匹配旅客: flightId={}, passengerIndex={}", flightId, passengerIndex);
    }

    @Override
    @Transactional
    public void autoAssignSeats(Long flightId) {
        Flight flight = flightMapper.selectById(flightId);
        if (flight == null) throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "航班不存在");
        checkFlightNotPast(flight);

        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getFlightId, flightId)
                        .in(Order::getStatus, "PAID", "ISSUED"));

        int row = 10;
        String[] columns = {"A", "B", "C", "D", "E", "F"};
        int colIdx = 0;

        for (Order order : orders) {
            List<OrderPassenger> passengers = orderPassengerMapper.selectList(
                    new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, order.getId()));
            for (OrderPassenger p : passengers) {
                if (p.getSeatRow() == null) {
                    p.setSeatRow(row);
                    p.setSeatColumn(columns[colIdx % columns.length]);
                    orderPassengerMapper.updateById(p);
                    colIdx++;
                    if (colIdx >= columns.length) {
                        colIdx = 0;
                        row++;
                    }
                }
            }
        }
        log.info("自动分配座位完成: flightId={}", flightId);
    }

    /**
     * 校验航班尚未起飞，已起飞的航班不允许值机操作
     */
    private void checkFlightNotPast(Flight flight) {
        if (flight.getDepartureTime() != null && flight.getDepartureTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "航班已起飞，无法执行值机操作");
        }
    }
}
