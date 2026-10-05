package com.itemll.flight_management_system_sp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itemll.flight_management_system_sp.entity.Airport;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AirportMapper extends BaseMapper<Airport> {

    /** 根据 IATA 三字码查询机场 */
    @Select("SELECT * FROM airport WHERE code = #{code} AND deleted = 0")
    Airport selectByCode(@Param("code") String code);
}
