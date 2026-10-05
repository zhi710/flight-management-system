package com.itemll.flight_management_system_sp.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.entity.Crew;
import com.itemll.flight_management_system_sp.entity.Flight;
import com.itemll.flight_management_system_sp.mapper.CrewMapper;
import com.itemll.flight_management_system_sp.mapper.FlightMapper;
import com.itemll.flight_management_system_sp.service.AdminCrewService;
import com.itemll.flight_management_system_sp.common.annotation.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端机组管理控制器
 */
@Tag(name = "管理端-机组管理", description = "排班、资质、机组动态")
@RestController
@RequestMapping("/admin/crew")
@RequiredArgsConstructor
public class AdminCrewController {

    private final AdminCrewService adminCrewService;
    private final CrewMapper crewMapper;
    private final FlightMapper flightMapper;

    @Operation(summary = "机组排班查询")
    @OperationLog(module = "机组成员", action = "查询排班列表")
    @GetMapping("/schedule")
    public Result<List<Map<String, Object>>> getCrewSchedule(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String qualification) {
        return Result.ok(adminCrewService.getCrewSchedule(startDate, endDate, department, qualification));
    }

    @Operation(summary = "创建排班（支持同时设置下次任务）")
    @OperationLog(module = "机组成员", action = "创建排班")
    @PostMapping("/schedule")
    public Result<Void> createSchedule(@RequestBody Map<String, Object> params) {
        Long crewId = parseCrewId(params.get("crewId").toString());
        Long flightId = parseFlightId(params.get("flightId").toString(),
                (String) params.get("date"));
        String role = (String) params.get("role");
        String date = (String) params.get("date");

        // 创建本次排班
        adminCrewService.createSchedule(crewId, flightId, role, date);

        // 如果有下次任务，一并创建
        Object nextFlightObj = params.get("nextFlightId");
        Object nextDateObj = params.get("nextDate");
        if (nextFlightObj != null && !nextFlightObj.toString().isEmpty()
                && nextDateObj != null && !nextDateObj.toString().isEmpty()) {
            Long nextFlightId = parseFlightId(nextFlightObj.toString(), (String) nextDateObj);
            String nextRole = params.containsKey("nextRole") && params.get("nextRole") != null
                    ? (String) params.get("nextRole") : role;
            adminCrewService.createSchedule(crewId, nextFlightId, nextRole, (String) nextDateObj);
        }
        return Result.ok();
    }

    /** 解析机组 ID：支持"机组编号"或数字 ID（自动 trim 前后空格） */
    private Long parseCrewId(String crewIdStr) {
        String cleaned = crewIdStr.trim();
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            Crew crew = crewMapper.selectOne(
                    new LambdaQueryWrapper<Crew>().eq(Crew::getCrewId, cleaned));
            if (crew == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND,
                    "机组人员不存在: " + cleaned + "（可填机组编号如 C20260101001 或数字ID）");
            return crew.getId();
        }
    }

    /** 解析航班 ID：支持"航班号"或数字 ID */
    private Long parseFlightId(String flightIdStr, String date) {
        try {
            return Long.parseLong(flightIdStr);
        } catch (NumberFormatException e) {
            LambdaQueryWrapper<Flight> wrapper = new LambdaQueryWrapper<Flight>()
                    .eq(Flight::getFlightNo, flightIdStr)
                    .eq(Flight::getDeleted, 0);
            if (date != null && !date.isEmpty()) {
                wrapper.eq(Flight::getFlightDate, LocalDate.parse(date));
            }
            Flight flight = flightMapper.selectOne(wrapper);
            if (flight == null) {
                // 不限定日期再试
                flight = flightMapper.selectOne(
                        new LambdaQueryWrapper<Flight>()
                                .eq(Flight::getFlightNo, flightIdStr)
                                .ge(Flight::getFlightDate, LocalDate.now())
                                .eq(Flight::getDeleted, 0)
                                .orderByAsc(Flight::getFlightDate)
                                .last("LIMIT 1"));
            }
            if (flight == null) throw new BusinessException(ErrorCode.FLIGHT_NOT_FOUND,
                    "航班不存在: " + flightIdStr + "（可填航班号如 CA1234 或数字ID）");
            return flight.getId();
        }
    }

    @Operation(summary = "自动排班")
    @OperationLog(module = "机组成员", action = "自动排班")
    @PostMapping("/schedule/auto")
    public Result<Map<String, Object>> autoSchedule(@RequestBody Map<String, Object> params) {
        boolean skipWeekends = Boolean.TRUE.equals(params.get("skipWeekends"));
        return Result.ok(adminCrewService.autoSchedule(
                (String) params.get("startDate"),
                (String) params.get("endDate"),
                skipWeekends));
    }

    @Operation(summary = "删除排班")
    @OperationLog(module = "机组成员", action = "删除排班")
    @DeleteMapping("/schedule/{scheduleId}")
    public Result<Void> deleteSchedule(@PathVariable Long scheduleId) {
        adminCrewService.deleteSchedule(scheduleId);
        return Result.ok();
    }

    @Operation(summary = "获取机组资质")
    @OperationLog(module = "机组成员", action = "查询机组资质")
    @GetMapping("/{crewId}/qualifications")
    public Result<Map<String, Object>> getQualifications(@PathVariable Long crewId) {
        return Result.ok(adminCrewService.getCrewQualifications(crewId));
    }

    @Operation(summary = "添加资质")
    @OperationLog(module = "机组成员", action = "添加资质")
    @PostMapping("/{crewId}/qualifications")
    public Result<Void> addQualification(@PathVariable Long crewId, @RequestBody Map<String, Object> qual) {
        adminCrewService.addQualification(crewId, qual);
        return Result.ok();
    }

    @Operation(summary = "更新资质")
    @OperationLog(module = "机组成员", action = "更新资质")
    @PutMapping("/{crewId}/qualifications/{qualId}")
    public Result<Void> updateQualification(@PathVariable Long crewId, @PathVariable Long qualId,
                                             @RequestBody Map<String, Object> qual) {
        adminCrewService.updateQualification(crewId, qualId, qual);
        return Result.ok();
    }

    @Operation(summary = "删除资质")
    @OperationLog(module = "机组成员", action = "删除资质")
    @DeleteMapping("/{crewId}/qualifications/{qualId}")
    public Result<Void> deleteQualification(@PathVariable Long crewId, @PathVariable Long qualId) {
        adminCrewService.deleteQualification(crewId, qualId);
        return Result.ok();
    }

    @Operation(summary = "机组名单（机组资质维护用）")
    @OperationLog(module = "机组成员", action = "查询机组名单")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> listCrews(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String keyword) {
        return Result.ok(adminCrewService.listCrews(department, keyword));
    }

    @Operation(summary = "机组动态")
    @OperationLog(module = "机组成员", action = "查询机组动态")
    @GetMapping("/dynamics")
    public Result<List<Map<String, Object>>> getCrewDynamics(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String crewId) {
        return Result.ok(adminCrewService.getCrewDynamics(status, crewId));
    }

    @Operation(summary = "新增机组人员")
    @OperationLog(module = "机组成员", action = "新增机组人员")
    @PostMapping
    public Result<Map<String, Object>> createCrew(@RequestBody Map<String, Object> crew) {
        Long id = adminCrewService.createCrew(crew);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id.toString());
        return Result.ok(data);
    }

    @Operation(summary = "修改机组人员")
    @OperationLog(module = "机组成员", action = "修改机组人员")
    @PutMapping("/{crewId}")
    public Result<Void> updateCrew(@PathVariable Long crewId, @RequestBody Map<String, Object> crew) {
        adminCrewService.updateCrew(crewId, crew);
        return Result.ok();
    }

    @Operation(summary = "删除机组人员")
    @OperationLog(module = "机组成员", action = "删除机组人员")
    @DeleteMapping("/{crewId}")
    public Result<Void> deleteCrew(@PathVariable Long crewId) {
        adminCrewService.deleteCrew(crewId);
        return Result.ok();
    }
}
