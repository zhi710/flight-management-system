package com.itemll.flight_management_system_sp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.itemll.flight_management_system_sp.entity.*;
import com.itemll.flight_management_system_sp.mapper.*;
import com.itemll.flight_management_system_sp.service.CrewStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 机组状态机实现（详见 {@link CrewStatusService}）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrewStatusServiceImpl implements CrewStatusService {

    private final CrewMapper crewMapper;
    private final CrewScheduleMapper scheduleMapper;
    private final FlightMapper flightMapper;
    private final CrewFlightTimeMapper crewFlightTimeMapper;
    private final SysConfigMapper sysConfigMapper;

    /** 月飞行时限（分钟）：与排班校验保持同一口径 */
    private static final int MONTHLY_LIMIT_MINUTES = 100 * 60;

    /** 航班已落地（不再占用机组）的状态 */
    private static final Set<String> LANDED_STATUSES = Set.of("ARRIVED", "COMPLETED", "CANCELLED");

    // ==================== 到达 → 休息 ====================

    @Override
    @Transactional
    public void onFlightArrived(Flight flight) {
        if (flight == null || flight.getId() == null) return;

        List<CrewSchedule> schedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<CrewSchedule>()
                        .eq(CrewSchedule::getFlightId, flight.getId())
                        .eq(CrewSchedule::getDeleted, 0));
        if (schedules.isEmpty()) return;

        LocalDate date = flight.getFlightDate() != null ? flight.getFlightDate() : LocalDate.now();
        int restMinutes = configMinutes("crewRestMinutes", 12 * 60);
        LocalDateTime until = LocalDateTime.now().plusMinutes(restMinutes);

        for (CrewSchedule s : schedules) {
            Crew crew = crewMapper.selectById(s.getCrewId());
            if (crew == null) continue;
            // 仅对「在岗」机组生效：停飞/培训/休假中的机组不因航班落地改状态
            if (!Set.of("STANDBY", "FLYING").contains(crew.getStatus())) continue;
            // 当天还有未落地的航班 → 还没飞完，不进入休息
            if (hasUnfinishedFlightToday(crew.getId(), date)) continue;

            crewMapper.update(null, new LambdaUpdateWrapper<Crew>()
                    .eq(Crew::getId, crew.getId())
                    .set(Crew::getStatus, "REST")
                    .set(Crew::getStatusUntil, until)
                    .set(Crew::getStatusReason, REASON_FLIGHT_REST));
            log.info("机组 {} 所执飞航班 {} 已到达，进入强制休息至 {}",
                    crew.getName(), flight.getFlightNo(), until);
        }
    }

    /** 该机组在指定日期是否还有未落地的航班 */
    private boolean hasUnfinishedFlightToday(Long crewPkId, LocalDate date) {
        List<CrewSchedule> todaySchedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<CrewSchedule>()
                        .eq(CrewSchedule::getCrewId, crewPkId)
                        .eq(CrewSchedule::getScheduleDate, date)
                        .eq(CrewSchedule::getDeleted, 0));
        if (todaySchedules.isEmpty()) return false;

        List<Long> flightIds = todaySchedules.stream()
                .map(CrewSchedule::getFlightId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (flightIds.isEmpty()) return false;

        List<Flight> flights = flightMapper.selectBatchIds(flightIds);
        return flights.stream().anyMatch(f -> !LANDED_STATUSES.contains(f.getStatus()));
    }

    // ==================== 时间推进 ====================

    @Override
    @Transactional
    public int advanceStatuses() {
        LocalDateTime now = LocalDateTime.now();
        int changed = 0;

        // ① 休息到期：本月未超时限 → 待命；已超时限 → 停飞
        for (Crew c : expired("REST", now)) {
            if (monthFlightMinutes(c.getId(), now.toLocalDate()) >= MONTHLY_LIMIT_MINUTES) {
                int groundedMinutes = configMinutes("crewGroundedMinutes", 24 * 60);
                setStatus(c, "GROUNDED", now.plusMinutes(groundedMinutes), REASON_OVER_LIMIT);
                log.info("机组 {} 休息结束，本月飞行已超 100 小时 → 停飞至 {}",
                        c.getName(), now.plusMinutes(groundedMinutes));
            } else {
                setStatus(c, "STANDBY", null, null);
                log.info("机组 {} 休息结束，本月未超时限 → 待命", c.getName());
            }
            changed++;
        }

        // ② 超限停飞到期 → 培训（资质停飞不在此列，须补齐资质后复飞）
        for (Crew c : expiredWithReason("GROUNDED", REASON_OVER_LIMIT, now)) {
            int trainingMinutes = configMinutes("crewTrainingMinutes", 24 * 60);
            setStatus(c, "TRAINING", now.plusMinutes(trainingMinutes), REASON_OVER_LIMIT);
            log.info("机组 {} 停飞结束 → 培训至 {}", c.getName(), now.plusMinutes(trainingMinutes));
            changed++;
        }

        // ③ 培训到期 → 待命
        for (Crew c : expired("TRAINING", now)) {
            setStatus(c, "STANDBY", null, null);
            log.info("机组 {} 培训结束 → 待命", c.getName());
            changed++;
        }

        return changed;
    }

    /** 查询某状态下 status_until 已到期的机组 */
    private List<Crew> expired(String status, LocalDateTime now) {
        return crewMapper.selectList(new LambdaQueryWrapper<Crew>()
                .eq(Crew::getDeleted, 0)
                .eq(Crew::getStatus, status)
                .isNotNull(Crew::getStatusUntil)
                .le(Crew::getStatusUntil, now));
    }

    /** 查询某状态 + 指定来源下 status_until 已到期的机组 */
    private List<Crew> expiredWithReason(String status, String reason, LocalDateTime now) {
        return crewMapper.selectList(new LambdaQueryWrapper<Crew>()
                .eq(Crew::getDeleted, 0)
                .eq(Crew::getStatus, status)
                .eq(Crew::getStatusReason, reason)
                .isNotNull(Crew::getStatusUntil)
                .le(Crew::getStatusUntil, now));
    }

    private void setStatus(Crew crew, String status, LocalDateTime until, String reason) {
        crewMapper.update(null, new LambdaUpdateWrapper<Crew>()
                .eq(Crew::getId, crew.getId())
                .set(Crew::getStatus, status)
                .set(Crew::getStatusUntil, until)
                .set(Crew::getStatusReason, reason));
    }

    /** 机组本日历月累计飞行分钟数（与排班校验同一口径） */
    private int monthFlightMinutes(Long crewPkId, LocalDate today) {
        LocalDate monthStart = today.withDayOfMonth(1);
        return crewFlightTimeMapper.selectList(new LambdaQueryWrapper<CrewFlightTime>()
                        .eq(CrewFlightTime::getCrewId, crewPkId)
                        .ge(CrewFlightTime::getFlightDate, monthStart))
                .stream().mapToInt(CrewFlightTime::getFlightHours).sum();
    }

    /**
     * 读取机组状态时长配置（分钟），缺省或非法时回退默认值。
     * 键名：crewRestMinutes / crewGroundedMinutes / crewTrainingMinutes
     */
    private int configMinutes(String key, int defaultMinutes) {
        SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key));
        if (c == null || StrUtil.isBlank(c.getConfigValue())) return defaultMinutes;
        try {
            int v = Integer.parseInt(c.getConfigValue().trim());
            return v >= 0 ? v : defaultMinutes;
        } catch (NumberFormatException e) {
            return defaultMinutes;
        }
    }
}
