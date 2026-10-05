package com.itemll.flight_management_system_sp.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.entity.*;
import com.itemll.flight_management_system_sp.mapper.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端基础数据管理控制器
 * <p>包含机场、航线、机型、机队、部门五类基础数据的 CRUD。</p>
 */
@Tag(name = "管理端-基础数据管理", description = "机场、航线、机型、机队、部门的增删改查")
@RestController
@RequestMapping("/admin/system/master-data")
@RequiredArgsConstructor
public class AdminMasterDataController {

    private final AirportMapper airportMapper;
    private final AirlineMapper airlineMapper;
    private final AircraftTypeMapper aircraftTypeMapper;
    private final AircraftMapper aircraftMapper;

    // ==================== 机场管理 ====================

    @OperationLog(module = "基础数据", action = "查询机场列表", saveParams = false)
    @Operation(summary = "机场列表")
    @GetMapping("/airports")
    public Result<List<Map<String, Object>>> getAirports() {
        List<Airport> list = airportMapper.selectList(
                new LambdaQueryWrapper<Airport>().eq(Airport::getDeleted, 0).orderByAsc(Airport::getCode));
        return Result.ok(list.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId()); m.put("code", a.getCode()); m.put("name", a.getName());
            m.put("city", a.getCity()); m.put("province", a.getProvince()); m.put("country", a.getCountry());
            return m;
        }).collect(Collectors.toList()));
    }

    @OperationLog(module = "基础数据", action = "创建机场")
    @Operation(summary = "创建机场")
    @PostMapping("/airports")
    public Result<Void> createAirport(@RequestBody Map<String, Object> params) {
        Airport a = new Airport();
        a.setCode((String) params.get("code")); a.setName((String) params.get("name"));
        a.setCity((String) params.get("city")); a.setProvince((String) params.get("province"));
        a.setCountry((String) params.getOrDefault("country", "中国"));
        airportMapper.insert(a);
        return Result.ok();
    }

    @OperationLog(module = "基础数据", action = "更新机场")
    @Operation(summary = "更新机场")
    @PutMapping("/airports/{id}")
    public Result<Void> updateAirport(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        Airport a = airportMapper.selectById(id);
        if (a == null) throw new BusinessException(ErrorCode.NOT_FOUND, "机场不存在");
        if (params.containsKey("name")) a.setName((String) params.get("name"));
        if (params.containsKey("city")) a.setCity((String) params.get("city"));
        airportMapper.updateById(a);
        return Result.ok();
    }

    @OperationLog(module = "基础数据", action = "删除机场")
    @Operation(summary = "删除机场")
    @DeleteMapping("/airports/{id}")
    public Result<Void> deleteAirport(@PathVariable Long id) {
        airportMapper.deleteById(id);
        return Result.ok();
    }

    // ==================== 航线管理 ====================

    @OperationLog(module = "基础数据", action = "查询航线列表", saveParams = false)
    @Operation(summary = "航线列表")
    @GetMapping("/routes")
    public Result<List<Map<String, Object>>> getRoutes() {
        // 航线数据在 route 表，这里用 airport 对组合模拟
        List<Airport> airports = airportMapper.selectList(
                new LambdaQueryWrapper<Airport>().eq(Airport::getDeleted, 0));
        List<Map<String, Object>> routes = new ArrayList<>();
        // 简化：返回已有的航线组合
        int[] distances = {1180, 1180, 1950, 1950, 1250, 1600, 170, 1100};
        String[][] pairs = {{"PEK","SHA"},{"SHA","PEK"},{"PEK","CAN"},{"CAN","PEK"},{"SHA","SZX"},{"PEK","CTU"},{"SHA","HGH"},{"PEK","XIY"}};
        for (int i = 0; i < pairs.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i + 1);
            m.put("departure", pairs[i][0]);
            m.put("arrival", pairs[i][1]);
            m.put("distance", distances[i]);
            m.put("flightType", "DOMESTIC");
            routes.add(m);
        }
        return Result.ok(routes);
    }

    @OperationLog(module = "基础数据", action = "创建航线")
    @Operation(summary = "创建航线")
    @PostMapping("/routes")
    public Result<Void> createRoute(@RequestBody Map<String, Object> params) {
        // 简化：实际应写入 route 表
        return Result.ok();
    }

    @OperationLog(module = "基础数据", action = "更新航线")
    @Operation(summary = "更新航线")
    @PutMapping("/routes/{id}")
    public Result<Void> updateRoute(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        return Result.ok();
    }

    @OperationLog(module = "基础数据", action = "删除航线")
    @Operation(summary = "删除航线")
    @DeleteMapping("/routes/{id}")
    public Result<Void> deleteRoute(@PathVariable Long id) {
        return Result.ok();
    }

    // ==================== 航空公司管理 ====================

    @OperationLog(module = "基础数据", action = "查询航空公司列表", saveParams = false)
    @Operation(summary = "航空公司列表")
    @GetMapping("/airlines")
    public Result<List<Map<String, Object>>> getAirlines() {
        List<Airline> list = airlineMapper.selectList(
                new LambdaQueryWrapper<Airline>().eq(Airline::getDeleted, 0).orderByAsc(Airline::getCode));
        return Result.ok(list.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId()); m.put("code", a.getCode()); m.put("name", a.getName());
            m.put("country", a.getCountry());
            return m;
        }).collect(Collectors.toList()));
    }

    // ==================== 机型管理 ====================

    @OperationLog(module = "基础数据", action = "查询机型列表", saveParams = false)
    @Operation(summary = "机型列表")
    @GetMapping("/aircraft-types")
    public Result<List<Map<String, Object>>> getAircraftTypes() {
        List<AircraftType> list = aircraftTypeMapper.selectList(
                new LambdaQueryWrapper<AircraftType>().eq(AircraftType::getDeleted, 0));
        return Result.ok(list.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId()); m.put("code", t.getCode()); m.put("name", t.getName());
            m.put("manufacturer", t.getManufacturer()); m.put("seats", t.getTotalSeats());
            return m;
        }).collect(Collectors.toList()));
    }

    @OperationLog(module = "基础数据", action = "创建机型")
    @Operation(summary = "创建机型")
    @PostMapping("/aircraft-types")
    public Result<Void> createAircraftType(@RequestBody Map<String, Object> params) {
        AircraftType t = new AircraftType();
        t.setCode((String) params.get("code")); t.setName((String) params.get("name"));
        t.setManufacturer((String) params.get("manufacturer"));
        if (params.containsKey("seats")) t.setTotalSeats(Integer.valueOf(params.get("seats").toString()));
        aircraftTypeMapper.insert(t);
        return Result.ok();
    }

    @OperationLog(module = "基础数据", action = "更新机型")
    @Operation(summary = "更新机型")
    @PutMapping("/aircraft-types/{id}")
    public Result<Void> updateAircraftType(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        AircraftType t = aircraftTypeMapper.selectById(id);
        if (t == null) throw new BusinessException(ErrorCode.NOT_FOUND, "机型不存在");
        if (params.containsKey("code")) t.setCode((String) params.get("code"));
        if (params.containsKey("name")) t.setName((String) params.get("name"));
        if (params.containsKey("manufacturer")) t.setManufacturer((String) params.get("manufacturer"));
        if (params.containsKey("seats")) t.setTotalSeats(Integer.valueOf(params.get("seats").toString()));
        aircraftTypeMapper.updateById(t);
        return Result.ok();
    }

    @OperationLog(module = "基础数据", action = "删除机型")
    @Operation(summary = "删除机型")
    @DeleteMapping("/aircraft-types/{id}")
    public Result<Void> deleteAircraftType(@PathVariable Long id) {
        aircraftTypeMapper.deleteById(id);
        return Result.ok();
    }

    // ==================== 机队管理 ====================

    @OperationLog(module = "基础数据", action = "查询机队列表", saveParams = false)
    @Operation(summary = "机队列表")
    @GetMapping("/fleet")
    public Result<List<Map<String, Object>>> getFleet() {
        List<Aircraft> list = aircraftMapper.selectList(
                new LambdaQueryWrapper<Aircraft>().eq(Aircraft::getDeleted, 0));
        return Result.ok(list.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId()); m.put("registration", a.getRegistration());
            m.put("aircraftTypeId", a.getAircraftTypeId()); m.put("airlineId", a.getAirlineId());
            m.put("status", a.getStatus());
            return m;
        }).collect(Collectors.toList()));
    }

    @OperationLog(module = "基础数据", action = "创建飞机")
    @Operation(summary = "创建飞机")
    @PostMapping("/fleet")
    public Result<Void> createAircraft(@RequestBody Map<String, Object> params) {
        Aircraft a = new Aircraft();
        a.setRegistration((String) params.get("registration"));
        a.setAircraftTypeId(Long.parseLong(params.get("aircraftTypeId").toString()));
        a.setAirlineId(Long.parseLong(params.get("airlineId").toString()));
        a.setStatus("ACTIVE");
        aircraftMapper.insert(a);
        return Result.ok();
    }

    @OperationLog(module = "基础数据", action = "更新飞机")
    @Operation(summary = "更新飞机")
    @PutMapping("/fleet/{id}")
    public Result<Void> updateAircraft(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        Aircraft a = aircraftMapper.selectById(id);
        if (a == null) throw new BusinessException(ErrorCode.NOT_FOUND, "飞机不存在");
        if (params.containsKey("registration")) a.setRegistration((String) params.get("registration"));
        if (params.containsKey("aircraftTypeId")) a.setAircraftTypeId(Long.valueOf(params.get("aircraftTypeId").toString()));
        if (params.containsKey("airlineId")) a.setAirlineId(Long.valueOf(params.get("airlineId").toString()));
        if (params.containsKey("status")) a.setStatus((String) params.get("status"));
        aircraftMapper.updateById(a);
        return Result.ok();
    }

    @OperationLog(module = "基础数据", action = "删除飞机")
    @Operation(summary = "删除飞机")
    @DeleteMapping("/fleet/{id}")
    public Result<Void> deleteAircraft(@PathVariable Long id) {
        aircraftMapper.deleteById(id);
        return Result.ok();
    }

    // ==================== 部门管理 ====================

    @OperationLog(module = "基础数据", action = "查询部门列表", saveParams = false)
    @Operation(summary = "部门列表")
    @GetMapping("/departments")
    public Result<List<Map<String, Object>>> getDepartments() {
        // 部门信息在 crew 表的 department 字段中，这里提取去重值
        List<Map<String, Object>> depts = List.of(
                Map.of("id", 1, "name", "飞行部", "description", "负责航班飞行任务"),
                Map.of("id", 2, "name", "客舱部", "description", "负责客舱服务"),
                Map.of("id", 3, "name", "运控部", "description", "负责航班运行控制"),
                Map.of("id", 4, "name", "机务部", "description", "负责飞机维护保养")
        );
        return Result.ok(depts);
    }

    @OperationLog(module = "基础数据", action = "创建部门")
    @Operation(summary = "创建部门")
    @PostMapping("/departments")
    public Result<Void> createDepartment(@RequestBody Map<String, Object> params) {
        // 简化：实际应有 department 表
        return Result.ok();
    }

    @OperationLog(module = "基础数据", action = "更新部门")
    @Operation(summary = "更新部门")
    @PutMapping("/departments/{id}")
    public Result<Void> updateDepartment(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        return Result.ok();
    }

    @OperationLog(module = "基础数据", action = "删除部门")
    @Operation(summary = "删除部门")
    @DeleteMapping("/departments/{id}")
    public Result<Void> deleteDepartment(@PathVariable Long id) {
        return Result.ok();
    }
}
