package com.itemll.flight_management_system_sp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itemll.flight_management_system_sp.entity.Flight;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface FlightMapper extends BaseMapper<Flight> {

    /**
     * 查询热门航线（按最低价格排序，返回每条航线的最低价航班信息）
     * 思路：用 ROW_NUMBER() 按航线分组、按价格排序，取每条航线的第一条航班
     */
    @Select("SELECT flightId, date, dep_code, dep_city, arr_code, arr_city, price FROM (" +
            "  SELECT f.id AS flightId, f.flight_date AS date, " +
            "    f.departure_airport AS dep_code, da.city AS dep_city, " +
            "    f.arrival_airport AS arr_code, aa.city AS arr_city, " +
            "    fc.total_price AS price, " +
            "    ROW_NUMBER() OVER (PARTITION BY f.departure_airport, f.arrival_airport ORDER BY fc.total_price ASC, f.id ASC) AS rn " +
            "  FROM flight f " +
            "  JOIN flight_cabin fc ON f.id = fc.flight_id " +
            "  JOIN airport da ON f.departure_airport = da.code " +
            "  JOIN airport aa ON f.arrival_airport = aa.code " +
            "  WHERE f.deleted = 0 AND fc.deleted = 0 AND f.flight_date >= CURDATE() " +
            "  AND f.status IN ('SCHEDULED','DELAYED') " +
            "  AND f.departure_time > DATE_ADD(NOW(), INTERVAL #{cutoff} MINUTE) " +
            ") ranked " +
            "WHERE rn = 1 " +
            "ORDER BY price ASC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectHotRoutes(@Param("cutoff") int cutoff, @Param("limit") int limit);

    /**
     * 查询特价机票（按总价升序，包含城市名和航空公司名）
     */
    @Select("SELECT f.id AS flightId, f.flight_date AS date, " +
            "f.departure_airport AS dep_code, da.city AS dep_city, " +
            "f.arrival_airport AS arr_code, aa.city AS arr_city, " +
            "MIN(fc.total_price) AS price, al.name AS airline " +
            "FROM flight f " +
            "JOIN flight_cabin fc ON f.id = fc.flight_id " +
            "JOIN airport da ON f.departure_airport = da.code " +
            "JOIN airport aa ON f.arrival_airport = aa.code " +
            "JOIN airline al ON f.airline_id = al.id " +
            "WHERE f.deleted = 0 AND fc.deleted = 0 " +
            "AND f.status IN ('SCHEDULED','DELAYED') " +
            "AND f.flight_date >= #{date} " +
            "AND f.departure_time > DATE_ADD(NOW(), INTERVAL #{cutoff} MINUTE) " +
            "GROUP BY f.id " +
            "ORDER BY price ASC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectDeals(@Param("date") LocalDate date, @Param("cutoff") int cutoff, @Param("limit") int limit);

    /**
     * 批量查询航班号（自定义 SQL，不带逻辑删除条件）。
     * 用于航班状态日志等历史记录的回显——航班被删除后日志仍在，需要能查到它的航班号。
     */
    @Select("<script>SELECT id AS flightId, flight_no AS flightNo FROM flight WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Map<String, Object>> selectFlightNosByIds(@Param("ids") Collection<Long> ids);
}
