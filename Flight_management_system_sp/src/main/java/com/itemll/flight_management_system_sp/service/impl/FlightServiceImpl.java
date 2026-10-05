package com.itemll.flight_management_system_sp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.enums.FlightStatus;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.dto.FlightSearchDTO;
import com.itemll.flight_management_system_sp.entity.*;
import com.itemll.flight_management_system_sp.mapper.*;
import com.itemll.flight_management_system_sp.service.FlightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 航班服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightServiceImpl implements FlightService {

    private final FlightMapper flightMapper;
    private final FlightCabinMapper flightCabinMapper;
    private final AirlineMapper airlineMapper;
    private final AirportMapper airportMapper;
    private final AircraftMapper aircraftMapper;
    private final AircraftTypeMapper aircraftTypeMapper;
    private final SysConfigMapper sysConfigMapper;

    @Override
    public List<Map<String, Object>> getFlightBoard(String date) {
        LocalDate d = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        List<Flight> flights = flightMapper.selectList(
                new LambdaQueryWrapper<Flight>()
                        .eq(Flight::getFlightDate, d)
                        .eq(Flight::getDeleted, 0)
                        .orderByAsc(Flight::getDepartureTime));

        // 预加载机场与航司，避免每条航班逐次查询
        Map<String, Airport> airportByCode = airportMapper.selectList(
                        new LambdaQueryWrapper<Airport>().eq(Airport::getDeleted, 0))
                .stream().collect(Collectors.toMap(Airport::getCode, a -> a, (x, y) -> x));
        Map<Long, Airline> airlineById = airlineMapper.selectList(
                        new LambdaQueryWrapper<Airline>().eq(Airline::getDeleted, 0))
                .stream().collect(Collectors.toMap(Airline::getId, a -> a, (x, y) -> x));

        return flights.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("flightId", f.getId().toString());
            m.put("flightNo", f.getFlightNo());
            m.put("departure", f.getDepartureAirport());
            m.put("arrival", f.getArrivalAirport());
            m.put("departureTime", f.getDepartureTime());
            m.put("arrivalTime", f.getArrivalTime());
            m.put("status", f.getStatus());
            m.put("statusText", FlightStatus.labelOf(f.getStatus()));
            m.put("gate", f.getDepartureGate());
            m.put("departureTerminal", f.getDepartureTerminal());
            m.put("arrivalTerminal", f.getArrivalTerminal());
            m.put("actualDepartureTime", f.getActualDepartureTime());
            m.put("actualArrivalTime", f.getActualArrivalTime());
            m.put("punctuality", f.getPunctuality());

            Airport dep = airportByCode.get(f.getDepartureAirport());
            Airport arr = airportByCode.get(f.getArrivalAirport());
            m.put("departureCity", dep != null ? dep.getCity() : null);
            m.put("departureAirportName", dep != null ? dep.getName() : null);
            m.put("arrivalCity", arr != null ? arr.getCity() : null);
            m.put("arrivalAirportName", arr != null ? arr.getName() : null);

            Airline airline = airlineById.get(f.getAirlineId());
            m.put("airlineCode", airline != null ? airline.getCode() : null);
            m.put("airlineName", airline != null ? airline.getName() : null);
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> listAirports() {
        return airportMapper.selectList(
                        new LambdaQueryWrapper<Airport>().eq(Airport::getDeleted, 0).orderByAsc(Airport::getCode))
                .stream().map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", a.getCode());
                    m.put("name", a.getName());
                    m.put("city", a.getCity());
                    return m;
                }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> searchFlights(FlightSearchDTO dto) {
        LambdaQueryWrapper<Flight> wrapper = new LambdaQueryWrapper<Flight>()
                .eq(Flight::getDeleted, 0);

        // 出发/到达可选（改签搜索时可能为空）：支持城市名 / 三字码 / 机场名，例如「北京」会覆盖 PEK、PKX
        if (StrUtil.isNotBlank(dto.getDeparture())) {
            List<String> depCodes = resolveAirportCodes(dto.getDeparture());
            if (depCodes.isEmpty()) {
                // 机场表里查不到 → 按原样精确匹配，结果为空（避免非法输入返回全量航班）
                wrapper.eq(Flight::getDepartureAirport, dto.getDeparture().trim());
            } else {
                wrapper.in(Flight::getDepartureAirport, depCodes);
            }
        }
        if (StrUtil.isNotBlank(dto.getArrival())) {
            List<String> arrCodes = resolveAirportCodes(dto.getArrival());
            if (arrCodes.isEmpty()) {
                wrapper.eq(Flight::getArrivalAirport, dto.getArrival().trim());
            } else {
                wrapper.in(Flight::getArrivalAirport, arrCodes);
            }
        }

        // 出发日期可选，为空时查询所有日期
        if (dto.getDepartDate() != null && !dto.getDepartDate().isBlank()) {
            wrapper.eq(Flight::getFlightDate, LocalDate.parse(dto.getDepartDate()));
        }

        // 只展示今天及之后、仍处于可售状态的航班：
        // 出发时刻已过停售截止（默认起飞前 45 分钟）的当日航班、以及已结束/取消的航班，不再出现在搜索结果
        wrapper.ge(Flight::getFlightDate, LocalDate.now());
        wrapper.in(Flight::getStatus, "SCHEDULED", "DELAYED");
        wrapper.apply("departure_time > DATE_ADD(NOW(), INTERVAL {0} MINUTE)", getSaleCloseMinutes());

        wrapper.orderByAsc(Flight::getFlightDate)
               .orderByAsc(Flight::getDepartureTime);

        // 限制最多返回 200 条，避免全表扫描
        wrapper.last("LIMIT 200");

        List<Flight> flights = flightMapper.selectList(wrapper);

        // 组装搜索结果
        List<Map<String, Object>> flightList = flights.stream().map(flight -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("flightId", flight.getId().toString());
            item.put("flightNo", flight.getFlightNo());

            // 航司信息
            Airline airline = airlineMapper.selectById(flight.getAirlineId());
            if (airline != null) {
                Map<String, Object> al = new HashMap<>();
                al.put("code", airline.getCode());
                al.put("name", airline.getName());
                al.put("logo", airline.getLogo());
                item.put("airline", al);
            }

            // 出发/到达信息
            Airport dep = airportMapper.selectByCode(flight.getDepartureAirport());
            Airport arr = airportMapper.selectByCode(flight.getArrivalAirport());
            item.put("departure", buildAirportInfo(dep, flight, true));
            item.put("arrival", buildAirportInfo(arr, flight, false));

            item.put("duration", flight.getDuration());
            item.put("stops", flight.getStops());
            item.put("punctuality", flight.getPunctuality());

            // 舱位信息
            List<FlightCabin> cabins = flightCabinMapper.selectList(
                    new LambdaQueryWrapper<FlightCabin>()
                            .eq(FlightCabin::getFlightId, flight.getId())
                            .eq(FlightCabin::getDeleted, 0));
            List<Map<String, Object>> cabinList = cabins.stream().map(c -> {
                Map<String, Object> cm = new HashMap<>();
                cm.put("class", c.getCabinClass());
                cm.put("className", c.getCabinName());
                cm.put("fare", c.getFare());
                cm.put("tax", c.getTax());
                cm.put("totalPrice", c.getTotalPrice());
                cm.put("seats", c.getAvailableSeats());
                cm.put("baggage", c.getBaggage());
                // 前台结果卡的舱位行要展示退改规则，此前没返回 → 那一栏一直是空白
                cm.put("refundRule", c.getRefundRule());
                return cm;
            }).collect(Collectors.toList());
            item.put("cabins", cabinList);

            // 服务信息
            Map<String, Object> services = new HashMap<>();
            services.put("wifi", flight.getWifi() == 1);
            services.put("meal", flight.getMealProvided() == 1);
            services.put("power", flight.getPower() == 1);
            item.put("services", services);

            return item;
        }).collect(Collectors.toList());

        // 构建返回
        Map<String, Object> result = new HashMap<>();
        result.put("searchId", "SR" + System.currentTimeMillis());
        result.put("flights", flightList);
        return result;
    }

    @Override
    public Map<String, Object> getFlightDetail(Long flightId) {
        Flight flight = flightMapper.selectById(flightId);
        if (flight == null) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "航班不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("flightId", flight.getId().toString());
        result.put("flightNo", flight.getFlightNo());

        Airline airline = airlineMapper.selectById(flight.getAirlineId());
        if (airline != null) {
            Map<String, Object> al = new HashMap<>();
            al.put("code", airline.getCode());
            al.put("name", airline.getName());
            result.put("airline", al);
        }

        Airport dep = airportMapper.selectByCode(flight.getDepartureAirport());
        Airport arr = airportMapper.selectByCode(flight.getArrivalAirport());
        result.put("departure", buildAirportInfo(dep, flight, true));
        result.put("arrival", buildAirportInfo(arr, flight, false));
        result.put("duration", flight.getDuration());

        // 机型信息
        if (flight.getAircraftId() != null) {
            Aircraft aircraft = aircraftMapper.selectById(flight.getAircraftId());
            if (aircraft != null) {
                AircraftType type = aircraftTypeMapper.selectById(aircraft.getAircraftTypeId());
                Map<String, Object> ac = new HashMap<>();
                if (type != null) {
                    ac.put("type", type.getCode());
                    ac.put("name", type.getName());
                    ac.put("seatLayout", type.getSeatLayout());
                    ac.put("totalSeats", type.getTotalSeats());
                }
                result.put("aircraft", ac);
            }
        }

        // 准点率
        Map<String, Object> punctuality = new HashMap<>();
        punctuality.put("rate", flight.getPunctuality());
        punctuality.put("sampleDays", 30);
        result.put("punctuality", punctuality);

        // 舱位详情
        List<FlightCabin> cabins = flightCabinMapper.selectList(
                new LambdaQueryWrapper<FlightCabin>()
                        .eq(FlightCabin::getFlightId, flightId)
                        .eq(FlightCabin::getDeleted, 0));
        result.put("cabins", cabins.stream().map(c -> {
            Map<String, Object> cm = new HashMap<>();
            cm.put("class", c.getCabinClass());
            cm.put("className", c.getCabinName());
            cm.put("fare", c.getFare());
            cm.put("tax", c.getTax());
            cm.put("totalPrice", c.getTotalPrice());
            cm.put("seats", c.getAvailableSeats());
            cm.put("baggage", c.getBaggage());
            cm.put("refundRule", c.getRefundRule());
            cm.put("changeRule", c.getChangeRule());
            return cm;
        }).collect(Collectors.toList()));

        return result;
    }

    @Override
    public List<Map<String, Object>> getHotRoutes(String city, int limit) {
        List<Map<String, Object>> rows = flightMapper.selectHotRoutes(getSaleCloseMinutes(), limit);
        return rows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("flightId", row.get("flightId").toString());
            item.put("departure", buildCityInfo(row, "dep"));
            item.put("arrival", buildCityInfo(row, "arr"));
            item.put("price", row.get("price"));
            item.put("date", row.get("date"));
            return item;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getDeals(String city, int limit) {
        List<Map<String, Object>> rows = flightMapper.selectDeals(LocalDate.now(), getSaleCloseMinutes(), limit);
        return rows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("flightId", row.get("flightId").toString());
            item.put("departure", buildCityInfo(row, "dep"));
            item.put("arrival", buildCityInfo(row, "arr"));
            item.put("price", row.get("price"));
            item.put("date", row.get("date"));
            item.put("airline", row.get("airline"));
            return item;
        }).collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    /**
     * 把旅客输入的「城市名 / 三字码 / 机场名」解析成机场三字码集合。
     * <p>例如输入「北京」会返回 PEK、PKX；输入「PEK」返回 PEK；输入「首都」返回 PEK。</p>
     *
     * @return 匹配到的三字码；机场表中查不到时返回空集合
     */
    private List<String> resolveAirportCodes(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return Collections.emptyList();
        }
        String kw = keyword.trim();
        return airportMapper.selectList(new LambdaQueryWrapper<Airport>()
                        .and(w -> w.eq(Airport::getCode, kw)
                                .or().like(Airport::getCity, kw)
                                .or().like(Airport::getName, kw)))
                .stream()
                .map(Airport::getCode)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 读取「停售截止时间」——起飞前多少分钟起不再展示/售卖，默认 45 分钟。
     * 可通过管理端「系统配置 → 售票配置 → 停售截止时间」调整（sys_config.saleCloseMinutes）。
     */
    private int getSaleCloseMinutes() {
        SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, "saleCloseMinutes"));
        if (c == null || StrUtil.isBlank(c.getConfigValue())) {
            return 45;
        }
        try {
            return Integer.parseInt(c.getConfigValue().trim());
        } catch (NumberFormatException e) {
            return 45;
        }
    }

    /**
     * 构建城市信息（用于热门航线和特价机票）
     * @param prefix "dep" 或 "arr"
     */
    private Map<String, Object> buildCityInfo(Map<String, Object> row, String prefix) {
        Map<String, Object> info = new HashMap<>();
        info.put("code", row.get(prefix + "_code"));
        info.put("city", row.get(prefix + "_city"));
        return info;
    }

    private Map<String, Object> buildAirportInfo(Airport airport, Flight flight, boolean isDeparture) {
        Map<String, Object> info = new HashMap<>();
        if (airport == null) return info;
        info.put("airport", airport.getCode());
        info.put("airportName", airport.getName());
        info.put("city", airport.getCity());
        if (isDeparture) {
            info.put("terminal", flight.getDepartureTerminal());
            info.put("gate", flight.getDepartureGate());
            info.put("dateTime", flight.getDepartureTime());
        } else {
            info.put("terminal", flight.getArrivalTerminal());
            info.put("dateTime", flight.getArrivalTime());
        }
        return info;
    }
}
