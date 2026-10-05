package com.itemll.flight_management_system_sp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.itemll.flight_management_system_sp.entity.Crew;
import com.itemll.flight_management_system_sp.entity.CrewQualification;
import com.itemll.flight_management_system_sp.entity.CrewFlightTime;
import com.itemll.flight_management_system_sp.entity.CrewSchedule;
import com.itemll.flight_management_system_sp.entity.Flight;
import com.itemll.flight_management_system_sp.entity.Aircraft;
import com.itemll.flight_management_system_sp.entity.AircraftType;
import com.itemll.flight_management_system_sp.mapper.CrewFlightTimeMapper;
import com.itemll.flight_management_system_sp.mapper.CrewMapper;
import com.itemll.flight_management_system_sp.mapper.CrewQualificationMapper;
import com.itemll.flight_management_system_sp.mapper.CrewScheduleMapper;
import com.itemll.flight_management_system_sp.mapper.FlightMapper;
import com.itemll.flight_management_system_sp.mapper.AircraftMapper;
import com.itemll.flight_management_system_sp.mapper.AircraftTypeMapper;
import com.itemll.flight_management_system_sp.service.AdminCrewService;
import com.itemll.flight_management_system_sp.service.CrewComplianceService;
import com.itemll.flight_management_system_sp.service.CrewStatusService;
import cn.hutool.core.util.StrUtil;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端机组服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCrewServiceImpl implements AdminCrewService {

    private final CrewMapper crewMapper;
    private final CrewScheduleMapper scheduleMapper;
    private final CrewQualificationMapper qualMapper;
    private final CrewFlightTimeMapper crewFlightTimeMapper;
    private final FlightMapper flightMapper;
    private final AircraftMapper aircraftMapper;
    private final AircraftTypeMapper aircraftTypeMapper;
    private final CrewComplianceService crewComplianceService;
    private final CrewStatusService crewStatusService;

    @Override
    public List<Map<String, Object>> getCrewSchedule(String startDate, String endDate, String department, String qualification) {
        // 如果指定了机型资质，先查出具备该资质的机组人员ID
        Set<Long> qualifiedCrewIds = null;
        if (StrUtil.isNotBlank(qualification)) {
            List<CrewQualification> matchingQuals = qualMapper.selectList(
                    new LambdaQueryWrapper<CrewQualification>()
                            .like(CrewQualification::getName, qualification)
                            .eq(CrewQualification::getDeleted, 0));
            qualifiedCrewIds = matchingQuals.stream()
                    .map(CrewQualification::getCrewId)
                    .collect(Collectors.toSet());
            if (qualifiedCrewIds.isEmpty()) {
                return Collections.emptyList(); // 无匹配资质，直接返回空
            }
        }

        // 如果指定了部门，先查出该部门的所有机组人员ID
        Set<Long> deptCrewIds = null;
        if (StrUtil.isNotBlank(department)) {
            List<Crew> deptCrews = crewMapper.selectList(
                    new LambdaQueryWrapper<Crew>()
                            .like(Crew::getDepartment, department)
                            .eq(Crew::getDeleted, 0));
            deptCrewIds = deptCrews.stream().map(Crew::getId).collect(Collectors.toSet());
            if (deptCrewIds.isEmpty()) {
                return Collections.emptyList();
            }
        }

        // 合并筛选条件：有资质的 AND 有所属部门的
        Set<Long> filteredCrewIds = null;
        if (qualifiedCrewIds != null && deptCrewIds != null) {
            qualifiedCrewIds.retainAll(deptCrewIds);
            filteredCrewIds = qualifiedCrewIds;
        } else if (qualifiedCrewIds != null) {
            filteredCrewIds = qualifiedCrewIds;
        } else if (deptCrewIds != null) {
            filteredCrewIds = deptCrewIds;
        }

        // 查排班
        LambdaQueryWrapper<CrewSchedule> scheduleWrapper = new LambdaQueryWrapper<CrewSchedule>()
                .ge(CrewSchedule::getScheduleDate, LocalDate.parse(startDate))
                .le(CrewSchedule::getScheduleDate, LocalDate.parse(endDate))
                .eq(CrewSchedule::getDeleted, 0)
                .orderByAsc(CrewSchedule::getScheduleDate);

        if (filteredCrewIds != null && !filteredCrewIds.isEmpty()) {
            scheduleWrapper.in(CrewSchedule::getCrewId, filteredCrewIds);
        } else if (filteredCrewIds != null) {
            return Collections.emptyList();
        }

        List<CrewSchedule> schedules = scheduleMapper.selectList(scheduleWrapper);
        if (schedules.isEmpty()) return Collections.emptyList();

        // 批量预加载机组和航班
        Set<Long> crewIds = schedules.stream().map(CrewSchedule::getCrewId).collect(Collectors.toSet());
        Set<Long> flightIds = schedules.stream().map(CrewSchedule::getFlightId).collect(Collectors.toSet());
        Map<Long, Crew> crewMap = crewMapper.selectBatchIds(crewIds).stream()
                .collect(Collectors.toMap(Crew::getId, c -> c));
        Map<Long, Flight> flightMap = flightMapper.selectBatchIds(flightIds).stream()
                .collect(Collectors.toMap(Flight::getId, f -> f));

        return schedules.stream().map(s -> {
            Crew crew = crewMap.get(s.getCrewId());
            Flight flight = flightMap.get(s.getFlightId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("scheduleId", s.getId().toString());
            m.put("crewId", crew != null ? crew.getCrewId() : String.valueOf(s.getCrewId()));
            m.put("name", crew != null ? crew.getName() : null);
            m.put("department", crew != null ? crew.getDepartment() : null);
            m.put("role", s.getRole());
            m.put("flightId", s.getFlightId().toString());
            if (flight != null) {
                m.put("flightNo", flight.getFlightNo());
                m.put("route", flight.getDepartureAirport() + "-" + flight.getArrivalAirport());
            } else {
                m.put("flightNo", null);
                m.put("route", null);
            }
            m.put("date", s.getScheduleDate() != null ? s.getScheduleDate().toString() : null);
            m.put("status", s.getStatus());
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void createSchedule(Long crewId, Long flightId, String role, String date) {
        // 合规状态先行刷新，保证资质状态/停飞标志为最新后再校验
        crewComplianceService.refreshComplianceStatus();

        LocalDate scheduleDate = LocalDate.parse(date);
        Flight flight = flightMapper.selectById(flightId);
        int flightMin = (flight != null && flight.getDuration() != null) ? flight.getDuration() : 120;

        Crew crew = crewMapper.selectById(crewId);
        if (crew == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "机组人员不存在: " + crewId);
        }

        // 1) 状态校验：仅 STANDBY / FLYING 机组可再排班
        String status = crew.getStatus();
        if (!List.of("STANDBY", "FLYING").contains(status)) {
            String reason = switch (status == null ? "" : status) {
                case "GROUNDED" -> crew.getName() + "：资质已失效处于停飞状态，无法排班，请先更新资质";
                case "REST" -> crew.getName() + "：正在强制休息中，无法排班";
                case "TRAINING" -> crew.getName() + "：正在培训中，无法排班";
                case "LEAVE", "LOA" -> crew.getName() + "：处于休假/停飞状态，无法排班";
                default -> crew.getName() + "：当前状态（" + status + "）不可排班";
            };
            throw new BusinessException(ErrorCode.CREW_SCHEDULE_CONFLICT, reason);
        }

        // 2) 当日重复排班：每人每天仅执行一班
        Long sameDayCount = scheduleMapper.selectCount(new LambdaQueryWrapper<CrewSchedule>()
                .eq(CrewSchedule::getCrewId, crewId)
                .eq(CrewSchedule::getScheduleDate, scheduleDate)
                .eq(CrewSchedule::getDeleted, 0));
        if (sameDayCount > 0) {
            throw new BusinessException(ErrorCode.CREW_SCHEDULE_CONFLICT,
                    crew.getName() + "：在 " + scheduleDate + " 已有排班，每人每天仅执行一班，请改期");
        }

        // 3) 资质匹配：过期/失效资质不能作为排班依据；
        //    机长/副驾驶还须持有「与本航班机型匹配且未过期」的机型资质(AIRCRAFT)，通用执照不能顶机型证
        List<CrewQualification> quals = qualMapper.selectList(new LambdaQueryWrapper<CrewQualification>()
                .eq(CrewQualification::getCrewId, crewId)
                .eq(CrewQualification::getDeleted, 0));
        boolean isPilot = "CAPTAIN".equals(role) || "FO".equals(role);
        String aircraftCode = aircraftCodeOf(flight);
        boolean needAircraftCheck = isPilot && StrUtil.isNotBlank(aircraftCode);
        // 有资质都要校验；飞行员且能确定机型时，即便资质为空也拦截（避免无机型证仍执飞）
        if (!quals.isEmpty() || needAircraftCheck) {
            boolean hasEffectiveForRole;
            if (needAircraftCheck) {
                hasEffectiveForRole = quals.stream()
                        .anyMatch(q -> qualFliesAircraft(q, role, aircraftCode));
            } else {
                hasEffectiveForRole = quals.stream()
                        .anyMatch(q -> qualEffective(q) && qualGrantsRole(q, role, crew));
            }
            if (!hasEffectiveForRole) {
                List<String> expiredGranters = quals.stream()
                        .filter(q -> !qualEffective(q)
                                && (needAircraftCheck ? qualRoleMatch(q, role) : qualGrantsRole(q, role, crew)))
                        .map(CrewQualification::getName)
                        .collect(Collectors.toList());
                String tip;
                if (needAircraftCheck) {
                    tip = expiredGranters.isEmpty()
                            ? "名下暂无适配 " + aircraftCode + " 机型且有效的机型资质，无法以"
                            + roleLabel(role) + "执飞该机型航班，请先添加/续证机型资质"
                            : "名下「" + String.join("、", expiredGranters) + "」已过期，无有效 "
                            + aircraftCode + " 机型资质";
                } else {
                    tip = expiredGranters.isEmpty()
                            ? "名下暂无「" + roleLabel(role) + "」相关资质，无法按此角色排班"
                            : "名下「" + String.join("、", expiredGranters) + "」已过期，无有效" + roleLabel(role) + "资质";
                }
                throw new BusinessException(ErrorCode.CREW_QUAL_NOT_MET, crew.getName() + "：" + tip);
            }
        }

        // 4) 月飞行时长上限：本日历月累计达到 100 小时即不可再排
        LocalDate monthStart = scheduleDate.withDayOfMonth(1);
        Integer existingMin = crewFlightTimeMapper.selectList(
                        new LambdaQueryWrapper<CrewFlightTime>()
                                .eq(CrewFlightTime::getCrewId, crewId)
                                .between(CrewFlightTime::getFlightDate, monthStart, scheduleDate))
                .stream().mapToInt(CrewFlightTime::getFlightHours).sum();
        if (existingMin + flightMin >= 100 * 60) {
            throw new BusinessException(ErrorCode.CREW_FLIGHT_TIME_EXCEEDED,
                    String.format("该机组本月已飞 %.1f 小时，无法再排 %.0f 分钟的航班（将超100h上限）",
                            existingMin / 60.0, (double) flightMin));
        }

        CrewSchedule schedule = new CrewSchedule();
        schedule.setCrewId(crewId);
        schedule.setFlightId(flightId);
        schedule.setRole(role);
        schedule.setScheduleDate(scheduleDate);
        schedule.setStatus("ASSIGNED");
        scheduleMapper.insert(schedule);

        // 有排班就标记为 FLYING（删除时自动恢复）
        crewMapper.update(null, new LambdaUpdateWrapper<Crew>()
                .eq(Crew::getId, crewId)
                .set(Crew::getStatus, "FLYING"));
    }

    /** 资质是否仍有效（无到期日视为长期有效） */
    private boolean qualEffective(CrewQualification q) {
        return q.getExpireDate() == null || !q.getExpireDate().isBefore(LocalDate.now());
    }

    /** 资质是否可用于该角色（仅按名称/等级推断，是否过期由调用方另行判断） */
    private boolean qualGrantsRole(CrewQualification q, String role, Crew crew) {
        if (q == null) return false;
        String name = q.getName() == null ? "" : q.getName();
        return switch (role) {
            case "CAPTAIN" -> "CAPTAIN".equals(q.getLevel()) || name.contains("机长");
            case "FO" -> "FO".equals(q.getLevel()) || name.contains("副驾驶") || name.contains("副驾");
            case "FA" -> name.contains("乘务") || "SENIOR".equals(q.getLevel())
                    || ("客舱部".equals(crew.getDepartment()) && "LICENSE".equals(q.getType()));
            default -> false;
        };
    }

    private String roleLabel(String role) {
        return switch (role) {
            case "CAPTAIN" -> "机长";
            case "FO" -> "副驾驶";
            case "FA" -> "乘务员";
            default -> role;
        };
    }

    /** 机型是否可用于该角色（仅按等级/名称推断，是否过期/机型匹配由调用方判断；乘务不限机型） */
    private boolean qualRoleMatch(CrewQualification q, String role) {
        if (q == null) return false;
        String name = q.getName() == null ? "" : q.getName();
        return switch (role) {
            case "CAPTAIN" -> "CAPTAIN".equals(q.getLevel()) || name.contains("机长");
            case "FO" -> "FO".equals(q.getLevel()) || name.contains("副驾驶") || name.contains("副驾");
            default -> true;
        };
    }

    /** 是否为本航班机型的有效机型资质：类型=AIRCRAFT、未过期、名称含该机型码、且可任该角色 */
    private boolean qualFliesAircraft(CrewQualification q, String role, String aircraftCode) {
        if (q == null || aircraftCode == null || !"AIRCRAFT".equals(q.getType())) return false;
        if (!qualEffective(q)) return false;
        if (!qualRoleMatch(q, role)) return false;
        String name = q.getName() == null ? "" : q.getName();
        // 忽略大小写与连字符/空格，避免 "B738" 与 "B-738"/"737-800" 写法差异
        String normCode = aircraftCode.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        String normName = name.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return !normCode.isEmpty() && normName.contains(normCode);
    }

    /** 解析航班实际执飞机型代码（flight → aircraft → aircraft_type.code），无法确定时返回 null */
    private String aircraftCodeOf(Flight flight) {
        if (flight == null || flight.getAircraftId() == null) return null;
        Aircraft aircraft = aircraftMapper.selectById(flight.getAircraftId());
        if (aircraft == null || aircraft.getAircraftTypeId() == null) return null;
        AircraftType type = aircraftTypeMapper.selectById(aircraft.getAircraftTypeId());
        return type == null ? null : type.getCode();
    }

    @Override
    public void deleteSchedule(Long scheduleId) {
        CrewSchedule cs = scheduleMapper.selectById(scheduleId);
        if (cs == null) return;
        scheduleMapper.deleteById(scheduleId);
        // 如果该机组已无任何排班记录，恢复为 STANDBY
        Long count = scheduleMapper.selectCount(
                new LambdaQueryWrapper<CrewSchedule>()
                        .eq(CrewSchedule::getCrewId, cs.getCrewId())
                        .eq(CrewSchedule::getDeleted, 0));
        if (count == 0) {
            crewMapper.update(null, new LambdaUpdateWrapper<Crew>()
                    .eq(Crew::getId, cs.getCrewId())
                    .set(Crew::getStatus, "STANDBY")
                    .set(Crew::getStatusUntil, null)
                    .set(Crew::getStatusReason, null));
            log.info("机组 {} 已无排班，状态恢复为 STANDBY", cs.getCrewId());
        }
    }

    @Override
    @Transactional
    public Map<String, Object> autoSchedule(String startDate, String endDate, boolean skipWeekends) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        // 合规状态先行刷新：停飞机组从源头不进入候选池
        crewComplianceService.refreshComplianceStatus();

        // 1. 查找日期范围内计划状态的航班（排除已取消/延误的）
        List<Flight> rangeFlights = flightMapper.selectList(
                new LambdaQueryWrapper<Flight>()
                        .between(Flight::getFlightDate, start, end)
                        .eq(Flight::getStatus, "SCHEDULED")
                        .eq(Flight::getDeleted, 0)
                        .orderByAsc(Flight::getFlightDate)
                        .orderByAsc(Flight::getDepartureTime));

        // 跳过周末
        if (skipWeekends) {
            rangeFlights = rangeFlights.stream()
                    .filter(f -> {
                        java.time.DayOfWeek dow = f.getFlightDate().getDayOfWeek();
                        return dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY;
                    })
                    .collect(Collectors.toList());
        }

        // 2. 查出已有排班，区分：已排满 / 部分排 / 未排
        List<CrewSchedule> existingSchedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<CrewSchedule>()
                        .between(CrewSchedule::getScheduleDate, start, end)
                        .eq(CrewSchedule::getDeleted, 0));
        // 每个航班已有的角色 + crewId
        Map<Long, Map<String, List<Long>>> flightExistingRoles = new HashMap<>();
        for (CrewSchedule s : existingSchedules) {
            flightExistingRoles.computeIfAbsent(s.getFlightId(), k -> new HashMap<>())
                    .computeIfAbsent(s.getRole(), k -> new ArrayList<>()).add(s.getCrewId());
        }

        // 完全排满的航班（每个角色 ≥ 1 人）
        Set<Long> fullFlightIds = new HashSet<>();
        for (Map.Entry<Long, Map<String, List<Long>>> entry : flightExistingRoles.entrySet()) {
            Map<String, List<Long>> roles = entry.getValue();
            if (roles.containsKey("CAPTAIN") && roles.containsKey("FO") && roles.containsKey("FA")) {
                fullFlightIds.add(entry.getKey());
            }
        }

        // 需要排班的航班（未排 + 部分排）
        List<Flight> needSchedule = rangeFlights.stream()
                .filter(f -> !fullFlightIds.contains(f.getId()))
                .collect(Collectors.toList());

        if (needSchedule.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("assignedFlights", 0);
            result.put("assignedCrew", 0);
            result.put("message", "日期范围内所有航班均已排班完毕");
            return result;
        }

        // 3. 可用机组：STANDBY 和已排班机组均可复用
        List<Crew> availableCrew = crewMapper.selectList(
                new LambdaQueryWrapper<Crew>()
                        .in(Crew::getStatus, "STANDBY", "FLYING")
                        .eq(Crew::getDeleted, 0));
        if (availableCrew.isEmpty()) {
            return Map.of("assignedFlights", 0, "assignedCrew", 0,
                    "message", "无可用机组，当前 STANDBY 和 FLYING 均为空");
        }

        // 4. 批量查所有候选机组的资质，按角色分组
        Set<Long> allCrewIds = availableCrew.stream().map(Crew::getId).collect(Collectors.toSet());
        List<CrewQualification> allQuals = qualMapper.selectList(
                new LambdaQueryWrapper<CrewQualification>()
                        .in(CrewQualification::getCrewId, allCrewIds)
                        .eq(CrewQualification::getDeleted, 0));
        Map<Long, List<CrewQualification>> crewQualsMap = allQuals.stream()
                .collect(Collectors.groupingBy(CrewQualification::getCrewId));

        // 分组到可担任角色
        List<Crew> captains = new ArrayList<>();
        List<Crew> firstOfficers = new ArrayList<>();
        List<Crew> attendants = new ArrayList<>();

        for (Crew c : availableCrew) {
            List<CrewQualification> cQuals = allQuals.stream()
                    .filter(q -> q.getCrewId().equals(c.getId()))
                    .collect(Collectors.toList());
            // 可任角色（有效资质 + 部门兜底），与「机组名单」页的展示口径一致
            List<String> roles = rolesOf(c, cQuals);
            if (roles.contains("CAPTAIN")) captains.add(c);
            if (roles.contains("FO")) firstOfficers.add(c);
            if (roles.contains("FA")) attendants.add(c);
        }

        // 5. 预加载所有可选机组的本月已飞小时数
        LocalDate monthStart = start.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        List<CrewFlightTime> allMonthTimes = crewFlightTimeMapper.selectList(
                new LambdaQueryWrapper<CrewFlightTime>()
                        .in(CrewFlightTime::getCrewId, allCrewIds)
                        .between(CrewFlightTime::getFlightDate, monthStart, monthEnd));
        Map<Long, Integer> monthlyHoursMap = new HashMap<>();
        for (CrewFlightTime ct : allMonthTimes) {
            monthlyHoursMap.merge(ct.getCrewId(), ct.getFlightHours(), Integer::sum);
        }

        // 6. 循环为每个航班补缺，每人每天只排一班
        Map<LocalDate, Set<Long>> crewByDate = new HashMap<>();
        Set<Long> overLimitCrewIds = new HashSet<>();
        int assignedFlights = 0;
        int assignedCrewTotal = 0;

        for (Flight flight : needSchedule) {
            LocalDate flightDate = flight.getFlightDate();
            Set<Long> busyToday = crewByDate.getOrDefault(flightDate, new HashSet<>());
            // 这个航班已有排班的角色和人员
            Map<String, List<Long>> flightCurRoles = flightExistingRoles.getOrDefault(flight.getId(), Collections.emptyMap());
            Set<Long> flightCurCrewIds = flightCurRoles.values().stream()
                    .flatMap(List::stream).collect(Collectors.toSet());
            // 已有人员当天也算 busy
            for (Long existingCrewId : flightCurCrewIds) {
                busyToday.add(existingCrewId);
            }
            // 机长/副驾驶须持有与本航班机型匹配且有效的机型资质
            String aircraftCode = aircraftCodeOf(flight);
            Crew captain = flightCurRoles.containsKey("CAPTAIN") ? null
                    : pickPilot(captains, crewQualsMap, "CAPTAIN", aircraftCode, busyToday,
                            flightCurCrewIds, flight, monthlyHoursMap);
            Crew fo = flightCurRoles.containsKey("FO") ? null
                    : pickPilot(firstOfficers, crewQualsMap, "FO", aircraftCode, busyToday,
                            flightCurCrewIds, flight, monthlyHoursMap);
            Crew fa = flightCurRoles.containsKey("FA") ? null
                    : pickAvailable(attendants, busyToday, flightCurCrewIds, flight, monthlyHoursMap);

            // 检查必要角色是否齐全
            boolean needCaptain = !flightCurRoles.containsKey("CAPTAIN");
            boolean needFO = !flightCurRoles.containsKey("FO");
            boolean needFA = !flightCurRoles.containsKey("FA");
            boolean captainOk = !needCaptain || captain != null;
            boolean foOk = !needFO || fo != null;
            boolean faOk = !needFA || fa != null;

            if (!captainOk || !foOk || !faOk) {
                log.info("自动排班: 航班 {} ({}) 缺少角色{}，跳过",
                        flight.getFlightNo(), flightDate,
                        (!captainOk ? "机长" : "") + (!foOk ? "副驾" : "") + (!faOk ? "乘务" : ""));
                captureOverLimit(captains, busyToday, flightCurCrewIds, flight, monthlyHoursMap, overLimitCrewIds);
                captureOverLimit(firstOfficers, busyToday, flightCurCrewIds, flight, monthlyHoursMap, overLimitCrewIds);
                captureOverLimit(attendants, busyToday, flightCurCrewIds, flight, monthlyHoursMap, overLimitCrewIds);
                continue;
            }

            // 只创建缺失角色的排班记录
            if (needCaptain) {
                CrewSchedule cs = new CrewSchedule();
                cs.setCrewId(captain.getId()); cs.setFlightId(flight.getId());
                cs.setRole("CAPTAIN"); cs.setScheduleDate(flightDate); cs.setStatus("ASSIGNED");
                scheduleMapper.insert(cs);
                crewByDate.computeIfAbsent(flightDate, k -> new HashSet<>()).add(captain.getId());
                monthlyHoursMap.merge(captain.getId(), flight.getDuration() != null ? flight.getDuration() : 120, Integer::sum);
                assignedCrewTotal++;
            }
            if (needFO) {
                CrewSchedule cs = new CrewSchedule();
                cs.setCrewId(fo.getId()); cs.setFlightId(flight.getId());
                cs.setRole("FO"); cs.setScheduleDate(flightDate); cs.setStatus("ASSIGNED");
                scheduleMapper.insert(cs);
                crewByDate.computeIfAbsent(flightDate, k -> new HashSet<>()).add(fo.getId());
                monthlyHoursMap.merge(fo.getId(), flight.getDuration() != null ? flight.getDuration() : 120, Integer::sum);
                assignedCrewTotal++;
            }
            if (needFA) {
                CrewSchedule cs = new CrewSchedule();
                cs.setCrewId(fa.getId()); cs.setFlightId(flight.getId());
                cs.setRole("FA"); cs.setScheduleDate(flightDate); cs.setStatus("ASSIGNED");
                scheduleMapper.insert(cs);
                crewByDate.computeIfAbsent(flightDate, k -> new HashSet<>()).add(fa.getId());
                monthlyHoursMap.merge(fa.getId(), flight.getDuration() != null ? flight.getDuration() : 120, Integer::sum);
                assignedCrewTotal++;
            }

            assignedFlights++;

            log.info("自动排班: {} ({}) → 机长:{}, 副驾:{}, 乘务:{}",
                    flight.getFlightNo(), flightDate, captain.getName(), fo.getName(), fa.getName());
        }

        // 超限机组不再在此处改状态：其状态由机组状态机统一推进
        // （航班到达 → 休息 → 休息到期时若本月已超 100h 则转停飞 → 培训 → 待命）

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assignedFlights", assignedFlights);
        result.put("assignedCrew", assignedCrewTotal);
        result.put("totalFlights", needSchedule.size());
        result.put("overLimitCrew", overLimitCrewIds.size());
        result.put("message", assignedFlights > 0
                ? "成功为 " + assignedFlights + " 个航班排班"
                : "未能排班，请检查空闲机组数量");
        return result;
    }

    private void captureOverLimit(List<Crew> pool, Set<Long> busyToday, Set<Long> alreadyOnFlight,
                                   Flight flight, Map<Long, Integer> monthlyHours, Set<Long> overLimit) {
        int flightMin = flight.getDuration() != null ? flight.getDuration() : 120;
        for (Crew c : pool) {
            if (busyToday.contains(c.getId()) || alreadyOnFlight.contains(c.getId())) continue;
            int existing = monthlyHours.getOrDefault(c.getId(), 0);
            if (existing + flightMin >= 100 * 60) {
                overLimit.add(c.getId());
            }
        }
    }

    private Crew pickAvailable(List<Crew> pool, Set<Long> busyToday, Set<Long> alreadyOnFlight,
                                Flight flight, Map<Long, Integer> monthlyHours) {
        // 优先选 STANDBY（还未排班的人），其次选 FLYING
        for (Crew c : pool) {
            if (busyToday.contains(c.getId()) || alreadyOnFlight.contains(c.getId())) continue;
            if (!"STANDBY".equals(c.getStatus())) continue;
            // 合规检查...
            int existing = monthlyHours.getOrDefault(c.getId(), 0);
            int flightMin = flight.getDuration() != null ? flight.getDuration() : 120;
            if (existing + flightMin >= 100 * 60) { log.info("自动排班: {} 超限跳过", c.getName()); continue; }
            return c;
        }
        // 没有 STANDBY 再用 FLYING
        for (Crew c : pool) {
            if (busyToday.contains(c.getId()) || alreadyOnFlight.contains(c.getId())) continue;
            int existing = monthlyHours.getOrDefault(c.getId(), 0);
            int flightMin = flight.getDuration() != null ? flight.getDuration() : 120;
            if (existing + flightMin >= 100 * 60) continue;
            return c;
        }
        return null;
    }

    /**
     * 挑选机长/副驾驶：仅从「持有本航班机型且有效的机型资质」的机组中选。
     * 机型无法确定时退回原逻辑（不按机型过滤）。
     */
    private Crew pickPilot(List<Crew> pool, Map<Long, List<CrewQualification>> crewQualsMap, String role,
                           String aircraftCode, Set<Long> busyToday, Set<Long> alreadyOnFlight,
                           Flight flight, Map<Long, Integer> monthlyHours) {
        if (StrUtil.isBlank(aircraftCode)) {
            return pickAvailable(pool, busyToday, alreadyOnFlight, flight, monthlyHours);
        }
        List<Crew> eligible = pool.stream().filter(c -> {
            List<CrewQualification> qs = crewQualsMap.getOrDefault(c.getId(), Collections.emptyList());
            return qs.stream().anyMatch(q -> qualFliesAircraft(q, role, aircraftCode));
        }).collect(Collectors.toList());
        if (eligible.isEmpty()) return null;
        return pickAvailable(eligible, busyToday, alreadyOnFlight, flight, monthlyHours);
    }

    @Override
    public Map<String, Object> getCrewQualifications(Long crewId) {
        // 先校准资质状态与停飞/复飞，保证展示即为最新
        crewComplianceService.refreshComplianceStatus();
        Crew crew = crewMapper.selectById(crewId);
        List<CrewQualification> quals = qualMapper.selectList(
                new LambdaQueryWrapper<CrewQualification>()
                        .eq(CrewQualification::getCrewId, crewId)
                        .eq(CrewQualification::getDeleted, 0)
                        .orderByAsc(CrewQualification::getExpireDate));

        Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(crewId)); // 雪花ID以字符串下发，避免前端精度丢失
        result.put("crewId", crew != null ? crew.getCrewId() : null);
        result.put("name", crew != null ? crew.getName() : null);
        result.put("department", crew != null ? crew.getDepartment() : null);
        result.put("qualifications", quals.stream().map(q -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.getId().toString());
            m.put("type", q.getType());
            m.put("name", q.getName());
            m.put("number", q.getNumber());
            m.put("level", q.getLevel());
            m.put("issueDate", q.getIssueDate() == null ? null : q.getIssueDate().toString());
            m.put("expireDate", q.getExpireDate() == null ? null : q.getExpireDate().toString());
            m.put("status", q.getStatus());
            return m;
        }).collect(Collectors.toList()));
        return result;
    }

    @Override
    public void addQualification(Long crewId, Map<String, Object> qual) {
        if (crewMapper.selectById(crewId) == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "机组不存在");
        }
        CrewQualification cq = new CrewQualification();
        cq.setCrewId(crewId);
        cq.setType(strOr(qual, "type", "OTHER"));
        String name = strOr(qual, "name", null);
        if (StrUtil.isBlank(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写资质名称");
        }
        cq.setName(name);
        cq.setNumber(strOr(qual, "number", null));
        cq.setLevel(strOr(qual, "level", null));
        cq.setIssueDate(dateOr(qual, "issueDate"));
        cq.setExpireDate(dateOr(qual, "expireDate"));
        cq.setStatus(computeQualStatus(cq)); // 按到期日自动判定
        qualMapper.insert(cq);
        // 保存后立即校准状态并联动停飞/复飞
        crewComplianceService.refreshComplianceStatus();
        log.info("机组 {} 新增资质『{}』，到期 {}，状态 {}", crewId, cq.getName(), cq.getExpireDate(), cq.getStatus());
    }

    @Override
    public void updateQualification(Long crewId, Long qualId, Map<String, Object> qual) {
        CrewQualification cq = qualMapper.selectById(qualId);
        if (cq == null || !cq.getCrewId().equals(crewId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "资质记录不存在");
        }
        if (qual.containsKey("type")) cq.setType(strOr(qual, "type", "OTHER"));
        if (qual.containsKey("name")) {
            String name = strOr(qual, "name", null);
            if (StrUtil.isBlank(name)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写资质名称");
            }
            cq.setName(name);
        }
        if (qual.containsKey("number")) cq.setNumber(strOr(qual, "number", null));
        if (qual.containsKey("level")) cq.setLevel(strOr(qual, "level", null));
        if (qual.containsKey("issueDate")) cq.setIssueDate(dateOr(qual, "issueDate"));
        if (qual.containsKey("expireDate")) cq.setExpireDate(dateOr(qual, "expireDate"));
        cq.setStatus(computeQualStatus(cq)); // 状态始终由到期日自动计算，禁止手填
        qualMapper.updateById(cq);
        // 保存后立即校准状态并联动停飞/复飞（续证后可自动复飞，过期后自动停飞）
        crewComplianceService.refreshComplianceStatus();
        log.info("机组 {} 更新资质『{}』，到期 {}，状态 {}", crewId, cq.getName(), cq.getExpireDate(), cq.getStatus());
    }

    @Override
    public void deleteQualification(Long crewId, Long qualId) {
        CrewQualification cq = qualMapper.selectById(qualId);
        if (cq == null || !cq.getCrewId().equals(crewId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "资质记录不存在");
        }
        qualMapper.deleteById(qualId);
        crewComplianceService.refreshComplianceStatus();
        log.info("机组 {} 删除资质『{}』", crewId, cq.getName());
    }

    /** 机组名单（机组资质维护页用）：按部门/关键字过滤，附带名下有效资质数 */
    @Override
    public List<Map<String, Object>> listCrews(String department, String keyword) {
        crewComplianceService.refreshComplianceStatus();
        LambdaQueryWrapper<Crew> wrapper = new LambdaQueryWrapper<Crew>()
                .eq(Crew::getDeleted, 0)
                .like(StrUtil.isNotBlank(department), Crew::getDepartment, department)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(Crew::getCrewId, keyword).or().like(Crew::getName, keyword))
                .orderByAsc(Crew::getCrewId);
        List<Crew> crews = crewMapper.selectList(wrapper);
        if (crews.isEmpty()) return Collections.emptyList();

        List<CrewQualification> allQuals = qualMapper.selectList(
                new LambdaQueryWrapper<CrewQualification>().eq(CrewQualification::getDeleted, 0));
        Map<Long, List<CrewQualification>> qualsByCrew = allQuals.stream()
                .collect(Collectors.groupingBy(CrewQualification::getCrewId));

        return crews.stream().map(c -> {
            List<CrewQualification> cQuals = qualsByCrew.getOrDefault(c.getId(), Collections.emptyList());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId().toString());
            m.put("crewId", c.getCrewId());
            m.put("name", c.getName());
            m.put("gender", c.getGender());
            m.put("phone", c.getPhone());
            m.put("email", c.getEmail());
            m.put("department", c.getDepartment());
            m.put("status", c.getStatus());
            m.put("statusUntil", c.getStatusUntil());
            m.put("statusReason", c.getStatusReason());
            m.put("qualCount", (long) cQuals.size());
            // 可担任角色（依据有效资质 + 部门兜底）：供「手动排班指定到人」时限定可选角色
            m.put("roles", rolesOf(c, cQuals));
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Long createCrew(Map<String, Object> body) {
        String crewId = trim(body.get("crewId"));
        String name = trim(body.get("name"));
        if (StrUtil.isBlank(crewId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写机组编号");
        }
        if (StrUtil.isBlank(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写姓名");
        }
        Long dup = crewMapper.selectCount(new LambdaQueryWrapper<Crew>()
                .eq(Crew::getCrewId, crewId));
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "机组编号已存在：" + crewId);
        }

        Crew crew = new Crew();
        crew.setCrewId(crewId);
        crew.setName(name);
        crew.setGender(trim(body.get("gender")));
        crew.setPhone(trim(body.get("phone")));
        crew.setEmail(trim(body.get("email")));
        crew.setDepartment(StrUtil.blankToDefault(trim(body.get("department")), "飞行部"));
        // 新人员一律从待命开始，后续由状态机（排班/到达/合规）推进
        crew.setStatus("STANDBY");

        // crew_id 上有唯一索引，已删除的行仍占着该工号：此时复用原记录，而不是插入新行
        Crew removed = crewMapper.selectByCrewIdIncludeDeleted(crewId);
        if (removed != null) {
            crew.setId(removed.getId());
            crewMapper.revive(crew);
            log.info("恢复已删除的机组人员 {} {}", crew.getCrewId(), crew.getName());
            return removed.getId();
        }
        crewMapper.insert(crew);
        log.info("新增机组人员 {} {}", crew.getCrewId(), crew.getName());
        return crew.getId();
    }

    @Override
    @Transactional
    public void updateCrew(Long id, Map<String, Object> body) {
        Crew crew = crewMapper.selectById(id);
        if (crew == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "机组人员不存在");
        }

        String newCrewId = trim(body.get("crewId"));
        if (StrUtil.isNotBlank(newCrewId) && !newCrewId.equals(crew.getCrewId())) {
            // 同样要连已删除的行一起查，否则改工号会撞唯一索引
            Crew clash = crewMapper.selectByCrewIdIncludeDeleted(newCrewId);
            if (clash != null && !clash.getId().equals(id)) {
                throw new BusinessException(ErrorCode.CONFLICT, "机组编号已存在：" + newCrewId);
            }
            crew.setCrewId(newCrewId);
        }
        if (body.containsKey("name")) {
            String name = trim(body.get("name"));
            if (StrUtil.isBlank(name)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "姓名不能为空");
            }
            crew.setName(name);
        }
        if (body.containsKey("gender")) crew.setGender(trim(body.get("gender")));
        if (body.containsKey("phone")) crew.setPhone(trim(body.get("phone")));
        if (body.containsKey("email")) crew.setEmail(trim(body.get("email")));
        if (body.containsKey("department")) crew.setDepartment(trim(body.get("department")));

        // 状态允许人工干预，但人工改状态时清掉状态机遗留的到期时间/原因，避免自动推进把人工设置覆盖掉
        boolean statusChanged = false;
        if (body.containsKey("status")) {
            String status = trim(body.get("status"));
            if (StrUtil.isNotBlank(status) && !status.equals(crew.getStatus())) {
                crew.setStatus(status);
                statusChanged = true;
            }
        }
        crewMapper.updateById(crew);
        if (statusChanged) {
            crewMapper.update(null, new LambdaUpdateWrapper<Crew>()
                    .eq(Crew::getId, id)
                    .set(Crew::getStatusUntil, null)
                    .set(Crew::getStatusReason, null));
        }
        log.info("修改机组人员 {} 状态={}", crew.getCrewId(), crew.getStatus());
    }

    @Override
    @Transactional
    public void deleteCrew(Long id) {
        Crew crew = crewMapper.selectById(id);
        if (crew == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "机组人员不存在");
        }
        // 未执行完的排班（今天及以后）不允许直接删人，否则排班表会出现无主的机组
        Long pending = scheduleMapper.selectCount(new LambdaQueryWrapper<CrewSchedule>()
                .eq(CrewSchedule::getCrewId, id)
                .ge(CrewSchedule::getScheduleDate, LocalDate.now()));
        if (pending != null && pending > 0) {
            throw new BusinessException(ErrorCode.CREW_SCHEDULE_CONFLICT,
                    crew.getName() + " 还有 " + pending + " 条今天及以后的排班，请先删除排班再删除人员");
        }
        // 只软删人员本身：资质/历史排班保留，后续用同一工号重新添加时会复用原记录（id 不变）
        crewMapper.deleteById(id);
        log.info("删除机组人员 {} {}", crew.getCrewId(), crew.getName());
    }

    /** 该机组当前可担任的角色（CAPTAIN/FO/FA），依据有效资质 + 部门兜底推断 */
    private List<String> rolesOf(Crew c, List<CrewQualification> cQuals) {
        // 只有仍有效的资质才能作为角色依据（过期资质一律不算）
        boolean hasExpiredQual = cQuals.stream().anyMatch(q -> !qualEffective(q));

        boolean canCaptain = cQuals.stream().anyMatch(q ->
                qualEffective(q) && ("CAPTAIN".equals(q.getLevel()) ||
                        (q.getName() != null && q.getName().contains("机长"))));
        boolean canFO = cQuals.stream().anyMatch(q ->
                qualEffective(q) && ("FO".equals(q.getLevel()) ||
                        (q.getName() != null && (q.getName().contains("副驾") || q.getName().contains("副驾驶")))));
        boolean canFA = "客舱部".equals(c.getDepartment()) ||
                (c.getName() != null && c.getName().contains("乘务"));

        // 没有明确资质的，按部门兜底（存在已过期资质时不再兜底，避免"证过期仍能飞"）
        if (!canCaptain && !canFO && !canFA && !hasExpiredQual) {
            if ("飞行部".equals(c.getDepartment())) {
                canFO = true; // 飞行部默认可做副驾驶
            } else if ("客舱部".equals(c.getDepartment())) {
                canFA = true;
            }
        }

        List<String> roles = new ArrayList<>();
        if (canCaptain) roles.add("CAPTAIN");
        if (canFO) roles.add("FO");
        if (canFA) roles.add("FA");
        return roles;
    }

    /** 取字符串值并 trim，null 安全 */
    private String trim(Object v) {
        return v == null ? null : v.toString().trim();
    }

    /** 取字符串值：缺省/空白时返回默认值 */
    private String strOr(Map<String, Object> qual, String key, String def) {
        Object v = qual.get(key);
        if (v == null || StrUtil.isBlank(v.toString())) return def;
        return v.toString().trim();
    }

    /** 取日期值（允许为空 = 长期有效） */
    private LocalDate dateOr(Map<String, Object> qual, String key) {
        Object v = qual.get(key);
        if (v == null || StrUtil.isBlank(v.toString())) return null;
        return LocalDate.parse(v.toString().trim());
    }

    /** 按到期日判定资质状态（与合规刷新同一口径） */
    private String computeQualStatus(CrewQualification q) {
        if (q.getExpireDate() == null) return "VALID";
        LocalDate today = LocalDate.now();
        LocalDate e = q.getExpireDate();
        if (e.isBefore(today)) return "EXPIRED";
        if (!e.isAfter(today.plusDays(30))) return "EXPIRING_SOON";
        return "VALID";
    }

    @Override
    public List<Map<String, Object>> getCrewDynamics(String status, String crewId) {
        // 先刷新停飞/资质状态，保证"停飞"机组在动态页及时呈现
        crewComplianceService.refreshComplianceStatus();
        // 惰性推进机组状态机：休息/停飞/培训到期后立即呈现最新状态，无需等定时任务
        crewStatusService.advanceStatuses();

        LambdaQueryWrapper<Crew> wrapper = new LambdaQueryWrapper<Crew>()
                .eq(status != null, Crew::getStatus, status)
                .eq(crewId != null, Crew::getCrewId, crewId)
                .eq(Crew::getDeleted, 0);

        List<Crew> crews = crewMapper.selectList(wrapper);
        if (crews.isEmpty()) return Collections.emptyList();

        LocalDate today = LocalDate.now();
        Set<Long> allCrewIds = crews.stream().map(Crew::getId).collect(Collectors.toSet());

        // 批量查所有机组的排班记录（今天及之后）
        List<CrewSchedule> allSchedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<CrewSchedule>()
                        .in(CrewSchedule::getCrewId, allCrewIds)
                        .ge(CrewSchedule::getScheduleDate, today)
                        .eq(CrewSchedule::getDeleted, 0)
                        .orderByAsc(CrewSchedule::getScheduleDate));
        Map<Long, List<CrewSchedule>> scheduleMap = allSchedules.stream()
                .collect(Collectors.groupingBy(CrewSchedule::getCrewId));

        // 批量查航班信息
        Set<Long> flightIds = allSchedules.stream().map(CrewSchedule::getFlightId).collect(Collectors.toSet());
        Map<Long, Flight> flightMap = new HashMap<>();
        if (!flightIds.isEmpty()) {
            flightMapper.selectBatchIds(flightIds).forEach(f -> flightMap.put(f.getId(), f));
        }

        // 批量查本月飞行时间
        LocalDate monthStart = today.withDayOfMonth(1);
        List<CrewFlightTime> monthTimes = crewFlightTimeMapper.selectList(
                new LambdaQueryWrapper<CrewFlightTime>()
                        .in(CrewFlightTime::getCrewId, allCrewIds)
                        .ge(CrewFlightTime::getFlightDate, monthStart));
        Map<Long, Integer> hoursMap = new HashMap<>();
        for (CrewFlightTime ct : monthTimes) {
            hoursMap.merge(ct.getCrewId(), ct.getFlightHours(), Integer::sum);
        }

        return crews.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("crewId", c.getCrewId());
            m.put("name", c.getName());
            m.put("department", c.getDepartment());

            List<CrewSchedule> schedules = scheduleMap.getOrDefault(c.getId(), Collections.emptyList());

            // 根据实际排班动态计算状态（不读 crew.status 字段）
            boolean hasToday = schedules.stream().anyMatch(s -> s.getScheduleDate().equals(today));
            boolean hasFuture = schedules.stream().anyMatch(s -> s.getScheduleDate().isAfter(today));
            String actualStatus;
            if (hasToday) {
                actualStatus = "FLYING";
            } else if (hasFuture) {
                actualStatus = "STANDBY";
            } else if (schedules.isEmpty()) {
                actualStatus = "STANDBY";  // 无排班=待命
            } else {
                actualStatus = "STANDBY";
            }
            // 如果 crew 表中是 GROUNDED/REST/TRAINING/LEAVE 等非自动状态，保留手动状态
            String dbStatus = c.getStatus();
            if (dbStatus != null && List.of("GROUNDED", "REST", "TRAINING", "LEAVE", "LOA").contains(dbStatus)) {
                actualStatus = dbStatus;
            }
            m.put("status", actualStatus);

            // 状态机附加信息：剩余时长与来源（仅 REST/GROUNDED/TRAINING 有截止时间）
            m.put("statusReason", c.getStatusReason());
            if (c.getStatusUntil() != null) {
                long remainMinutes = Duration.between(LocalDateTime.now(), c.getStatusUntil()).toMinutes();
                m.put("statusUntil", c.getStatusUntil().toString());
                m.put("statusRemainMinutes", Math.max(remainMinutes, 0));
            } else {
                m.put("statusUntil", null);
                m.put("statusRemainMinutes", null);
            }

            // ── 当前航班 & 位置（根据排班 + 航班实际到达状态动态切换）──
            String currentFlightNo = null;
            String currentLocation = null;

            // 找到今天排班的航班
            CrewSchedule todaySched = schedules.stream()
                    .filter(s -> s.getScheduleDate().equals(today))
                    .findFirst().orElse(null);

            if (todaySched != null) {
                Flight f = flightMap.get(todaySched.getFlightId());
                // 检查这个航班是否已到达
                boolean hasArrived = f != null && ("ARRIVED".equals(f.getStatus()) || "COMPLETED".equals(f.getStatus()));
                if (hasArrived) {
                    // 已到达 → 当前位置变为到达机场，当前航班过渡到下次任务
                    currentLocation = f.getArrivalAirport();
                    currentFlightNo = null; // 不显示（已落地）
                } else {
                    // 还没到 → 正常显示当前航班
                    currentFlightNo = f.getFlightNo();
                    currentLocation = f.getDepartureAirport();
                }
            }

            // 下次任务：今天之后第一条
            CrewSchedule nextSched = schedules.stream()
                    .filter(s -> s.getScheduleDate().isAfter(today))
                    .findFirst().orElse(null);

            // 如果今天航班已到达且有下次任务，当前航班切换到下次任务
            if (todaySched != null && nextSched != null) {
                Flight tf = flightMap.get(todaySched.getFlightId());
                boolean todayArrived = tf != null && ("ARRIVED".equals(tf.getStatus()) || "COMPLETED".equals(tf.getStatus()));
                if (todayArrived) {
                    Flight nf = flightMap.get(nextSched.getFlightId());
                    currentFlightNo = nf != null ? nf.getFlightNo() : "";
                    if (currentLocation == null) {
                        currentLocation = nf != null ? nf.getDepartureAirport() : "";
                    }
                }
            }

            // 今天没有排班 → 取最近一次过去的航班，位置=其到达机场
            if (currentFlightNo == null && currentLocation == null) {
                CrewSchedule lastPast = schedules.stream()
                        .filter(s -> s.getScheduleDate().isBefore(today))
                        .reduce((first, second) -> second)
                        .orElse(null);
                if (lastPast != null) {
                    Flight lf = flightMap.get(lastPast.getFlightId());
                    currentLocation = lf != null ? lf.getArrivalAirport() : "";
                }
                // 当前航班：如果有下次任务则展示下次，否则不展示
                if (nextSched != null) {
                    Flight nf = flightMap.get(nextSched.getFlightId());
                    currentFlightNo = nf != null ? nf.getFlightNo() : "";
                    if (currentLocation == null) {
                        currentLocation = nf != null ? nf.getDepartureAirport() : "";
                    }
                }
            }

            m.put("flightNo", currentFlightNo);
            m.put("location", currentLocation);

            if (nextSched != null) {
                Flight nf = flightMap.get(nextSched.getFlightId());
                m.put("nextDuty", (nf != null ? nf.getFlightNo() : "") + " " +
                        nextSched.getScheduleDate() + " " + nextSched.getRole());
            } else {
                m.put("nextDuty", schedules.isEmpty() ? "无排班" : "今日已排完");
            }

            int totalMin = hoursMap.getOrDefault(c.getId(), 0);
            m.put("flightHours", totalMin / 60);

            // 同步回 crew 表，让数据库状态跟上实际状态
            if (!actualStatus.equals(dbStatus)) {
                crewMapper.update(null, new LambdaUpdateWrapper<Crew>()
                        .eq(Crew::getId, c.getId())
                        .set(Crew::getStatus, actualStatus));
            }

            return m;
        }).collect(Collectors.toList());
    }
}
