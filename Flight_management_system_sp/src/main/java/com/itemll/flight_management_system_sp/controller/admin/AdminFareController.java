package com.itemll.flight_management_system_sp.controller.admin;

import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.entity.AirlineFare;
import com.itemll.flight_management_system_sp.entity.Airline;
import com.itemll.flight_management_system_sp.mapper.AirlineFareMapper;
import com.itemll.flight_management_system_sp.mapper.AirlineMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "管理端-票价管理", description = "航司舱位票价标准维护")
@RestController
@RequestMapping("/admin/fares")
@RequiredArgsConstructor
public class AdminFareController {

    private final AirlineFareMapper airlineFareMapper;
    private final AirlineMapper airlineMapper;

    @OperationLog(module = "票价管理", action = "查询票价列表", saveParams = false)
    @Operation(summary = "获取票价列表")
    @GetMapping
    public Result<List<Map<String, Object>>> getFareList(
            @RequestParam(required = false) Long airlineId) {
        LambdaQueryWrapper<AirlineFare> wrapper = new LambdaQueryWrapper<AirlineFare>()
                .eq(airlineId != null, AirlineFare::getAirlineId, airlineId)
                .orderByAsc(AirlineFare::getAirlineId)
                .orderByAsc(AirlineFare::getCabinClass);
        List<AirlineFare> fares = airlineFareMapper.selectList(wrapper);
        Set<Long> airlineIds = fares.stream().map(AirlineFare::getAirlineId).collect(Collectors.toSet());
        Map<Long, String> airlineNameMap = new HashMap<>();
        if (!airlineIds.isEmpty()) {
            airlineMapper.selectBatchIds(airlineIds)
                    .forEach(a -> airlineNameMap.put(a.getId(), a.getName()));
        }
        List<Map<String, Object>> result = fares.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId().toString());
            m.put("airlineId", f.getAirlineId());
            m.put("airlineName", airlineNameMap.get(f.getAirlineId()));
            m.put("cabinClass", f.getCabinClass());
            m.put("fare", f.getFare());
            m.put("tax", f.getTax());
            m.put("baggage", f.getBaggage());
            m.put("refundRule", f.getRefundRule());
            m.put("changeRule", f.getChangeRule());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(result);
    }

    @OperationLog(module = "票价管理", action = "新增票价")
    @Operation(summary = "新增票价标准")
    @PostMapping
    public Result<Void> createFare(@RequestBody AirlineFare fare) {
        if (fare.getAirlineId() == null || fare.getCabinClass() == null) {
            return Result.fail("航司和舱位等级不能为空");
        }
        AirlineFare existing = airlineFareMapper.selectOne(
                new LambdaQueryWrapper<AirlineFare>()
                        .eq(AirlineFare::getAirlineId, fare.getAirlineId())
                        .eq(AirlineFare::getCabinClass, fare.getCabinClass()));
        if (existing != null) {
            return Result.fail("该航司的 " + fare.getCabinClass() + " 舱位票价已存在，请使用编辑功能");
        }
        airlineFareMapper.insert(fare);
        log.info("新增票价标准: airlineId={}, cabinClass={}, fare={}", fare.getAirlineId(), fare.getCabinClass(), fare.getFare());
        return Result.ok();
    }

    @OperationLog(module = "票价管理", action = "编辑票价")
    @Operation(summary = "编辑票价标准")
    @PutMapping("/{id}")
    public Result<Void> updateFare(@PathVariable Long id, @RequestBody AirlineFare fare) {
        AirlineFare existing = airlineFareMapper.selectById(id);
        if (existing == null) return Result.fail("票价标准不存在");
        if (fare.getFare() != null) existing.setFare(fare.getFare());
        if (fare.getTax() != null) existing.setTax(fare.getTax());
        if (fare.getBaggage() != null) existing.setBaggage(fare.getBaggage());
        if (fare.getRefundRule() != null) existing.setRefundRule(fare.getRefundRule());
        if (fare.getChangeRule() != null) existing.setChangeRule(fare.getChangeRule());
        airlineFareMapper.updateById(existing);
        log.info("编辑票价标准: id={}", id);
        return Result.ok();
    }

    @OperationLog(module = "票价管理", action = "删除票价")
    @Operation(summary = "删除票价标准")
    @DeleteMapping("/{id}")
    public Result<Void> deleteFare(@PathVariable Long id) {
        airlineFareMapper.deleteById(id);
        return Result.ok();
    }
}
