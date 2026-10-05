package com.itemll.flight_management_system_sp.controller.admin;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.entity.Flight;
import com.itemll.flight_management_system_sp.entity.Gate;
import com.itemll.flight_management_system_sp.entity.GateAssignment;
import com.itemll.flight_management_system_sp.mapper.FlightMapper;
import com.itemll.flight_management_system_sp.mapper.GateAssignmentMapper;
import com.itemll.flight_management_system_sp.mapper.GateMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端登机口管理控制器
 * <p>登机口是物理资源，占用记录放在 gate_assignment（时段排班），
 * 同一登机口可被多个航班在不同时段占用。</p>
 */
@Slf4j
@Tag(name = "管理端-登机口管理", description = "登机口列表、按时段分配/释放")
@RestController
@RequestMapping("/admin/gates")
@RequiredArgsConstructor
public class AdminGateController {

    private final GateMapper gateMapper;
    private final GateAssignmentMapper gateAssignmentMapper;
    private final FlightMapper flightMapper;

    @OperationLog(module = "登机口管理", action = "查询登机口列表", saveParams = false)
    @Operation(summary = "获取登机口列表（含指定日期的排班）")
    @GetMapping
    public Result<List<Map<String, Object>>> getGates(
            @RequestParam(required = false) String terminal,
            @RequestParam(required = false) String date) {
        List<Gate> gates = gateMapper.selectList(
                new LambdaQueryWrapper<Gate>()
                        .eq(StrUtil.isNotBlank(terminal), Gate::getTerminal, terminal)
                        .orderByAsc(Gate::getGateCode));

        // 查占用记录（可按日期过滤）
        List<GateAssignment> assignments = gateAssignmentMapper.selectList(
                new LambdaQueryWrapper<GateAssignment>()
                        .eq(StrUtil.isNotBlank(date), GateAssignment::getFlightDate,
                                StrUtil.isNotBlank(date) ? LocalDate.parse(date) : null));

        Set<Long> flightIds = assignments.stream().map(GateAssignment::getFlightId).collect(Collectors.toSet());
        Map<Long, String> flightNoMap = new HashMap<>();
        if (!flightIds.isEmpty()) {
            flightMapper.selectBatchIds(flightIds).forEach(f -> flightNoMap.put(f.getId(), f.getFlightNo()));
        }

        Map<Long, List<GateAssignment>> byGate = assignments.stream()
                .collect(Collectors.groupingBy(GateAssignment::getGateId));

        List<Map<String, Object>> result = gates.stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId());
            m.put("gateCode", g.getGateCode());
            m.put("terminal", g.getTerminal());
            List<Map<String, Object>> asg = byGate.getOrDefault(g.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(GateAssignment::getStartTime,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(a -> {
                        Map<String, Object> am = new LinkedHashMap<>();
                        am.put("id", a.getId().toString());
                        am.put("flightId", a.getFlightId().toString());
                        am.put("flightNo", flightNoMap.get(a.getFlightId()));
                        am.put("flightDate", a.getFlightDate() != null ? a.getFlightDate().toString() : null);
                        am.put("startTime", a.getStartTime());
                        am.put("endTime", a.getEndTime());
                        return am;
                    }).collect(Collectors.toList());
            m.put("assignments", asg);
            return m;
        }).collect(Collectors.toList());

        return Result.ok(result);
    }

    @OperationLog(module = "登机口管理", action = "分配登机口")
    @Operation(summary = "分配登机口（按时段，含冲突校验）")
    @PostMapping("/assign")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> assignGate(@RequestBody Map<String, Object> params) {
        String gateCode = (String) params.get("gateCode");
        if (StrUtil.isBlank(gateCode)) throw new BusinessException(ErrorCode.BAD_REQUEST, "登机口不能为空");

        Object flightIdObj = params.get("flightId");
        if (flightIdObj == null || StrUtil.isBlank(flightIdObj.toString())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择航班班次");
        }
        Long flightId = Long.parseLong(flightIdObj.toString());

        Gate gate = gateMapper.selectOne(
                new LambdaQueryWrapper<Gate>().eq(Gate::getGateCode, gateCode));
        if (gate == null) throw new BusinessException(ErrorCode.NOT_FOUND, "登机口 " + gateCode + " 不存在");

        Flight flight = flightMapper.selectById(flightId);
        if (flight == null) throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND, "航班不存在");

        // 同一航班只能分配一个登机口
        GateAssignment dup = gateAssignmentMapper.selectOne(
                new LambdaQueryWrapper<GateAssignment>().eq(GateAssignment::getFlightId, flight.getId()));
        if (dup != null) throw new BusinessException(ErrorCode.CONFLICT, "该航班已分配登机口，请先释放");

        // 时段默认值：未指定时用航班计划时刻
        LocalDateTime start = parseDateTime((String) params.get("startTime"));
        LocalDateTime end = parseDateTime((String) params.get("endTime"));
        if (start == null) start = flight.getDepartureTime();
        if (end == null) end = flight.getArrivalTime() != null ? flight.getArrivalTime()
                : (start != null ? start.plusHours(2) : null);

        // 冲突校验：同一登机口时段不能重叠
        List<GateAssignment> existing = gateAssignmentMapper.selectList(
                new LambdaQueryWrapper<GateAssignment>().eq(GateAssignment::getGateId, gate.getId()));
        for (GateAssignment a : existing) {
            if (overlap(a.getStartTime(), a.getEndTime(), start, end)) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "登机口 " + gateCode + " 在 " + fmt(start) + " ~ " + fmt(end) + " 已被占用");
            }
        }

        GateAssignment assignment = new GateAssignment();
        assignment.setGateId(gate.getId());
        assignment.setFlightId(flight.getId());
        assignment.setFlightDate(flight.getFlightDate());
        assignment.setStartTime(start);
        assignment.setEndTime(end);
        gateAssignmentMapper.insert(assignment);

        flight.setDepartureGate(gateCode);
        flightMapper.updateById(flight);

        log.info("登机口分配: gate={}, flightId={}", gateCode, flightId);
        return Result.ok();
    }

    @OperationLog(module = "登机口管理", action = "释放登机口")
    @Operation(summary = "释放登机口（按分配记录）")
    @PostMapping("/release")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> releaseGate(@RequestBody Map<String, Object> params) {
        Object idObj = params.get("assignmentId");
        if (idObj == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "assignmentId 不能为空");
        Long assignmentId = Long.parseLong(idObj.toString());

        GateAssignment assignment = gateAssignmentMapper.selectById(assignmentId);
        if (assignment == null) throw new BusinessException(ErrorCode.NOT_FOUND, "分配记录不存在");
        gateAssignmentMapper.deleteById(assignmentId);

        Flight flight = flightMapper.selectById(assignment.getFlightId());
        if (flight != null && flight.getDepartureGate() != null) {
            flight.setDepartureGate(null);
            flightMapper.updateById(flight);
        }
        return Result.ok();
    }

    private boolean overlap(LocalDateTime aStart, LocalDateTime aEnd, LocalDateTime bStart, LocalDateTime bEnd) {
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) return false;
        return bStart.isBefore(aEnd) && bEnd.isAfter(aStart);
    }

    private LocalDateTime parseDateTime(String s) {
        if (StrUtil.isBlank(s)) return null;
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        }
    }

    private String fmt(LocalDateTime t) {
        return t != null ? t.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) : "";
    }
}
