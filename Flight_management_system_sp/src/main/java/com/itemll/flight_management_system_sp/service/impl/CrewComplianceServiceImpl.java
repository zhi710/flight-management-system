package com.itemll.flight_management_system_sp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.itemll.flight_management_system_sp.entity.Crew;
import com.itemll.flight_management_system_sp.entity.CrewFlightTime;
import com.itemll.flight_management_system_sp.entity.CrewQualification;
import com.itemll.flight_management_system_sp.entity.CrewSchedule;
import com.itemll.flight_management_system_sp.mapper.CrewFlightTimeMapper;
import com.itemll.flight_management_system_sp.mapper.CrewMapper;
import com.itemll.flight_management_system_sp.mapper.CrewQualificationMapper;
import com.itemll.flight_management_system_sp.mapper.CrewScheduleMapper;
import com.itemll.flight_management_system_sp.security.AdminIrregularWebSocketHandler;
import com.itemll.flight_management_system_sp.service.CrewComplianceService;
import com.itemll.flight_management_system_sp.service.CrewStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrewComplianceServiceImpl implements CrewComplianceService {

    private final CrewMapper crewMapper;
    private final CrewFlightTimeMapper crewFlightTimeMapper;
    private final CrewQualificationMapper crewQualificationMapper;
    private final CrewScheduleMapper crewScheduleMapper;
    private final AdminIrregularWebSocketHandler adminIrregularWebSocket;

    @Override
    public List<Map<String, Object>> getCrewFlightSummary(Long crewId, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);

        List<CrewFlightTime> records = crewFlightTimeMapper.selectList(
                new LambdaQueryWrapper<CrewFlightTime>()
                        .eq(CrewFlightTime::getCrewId, crewId)
                        .ge(CrewFlightTime::getFlightDate, startDate)
                        .orderByDesc(CrewFlightTime::getFlightDate));

        int totalFlightHours = records.stream().mapToInt(CrewFlightTime::getFlightHours).sum();
        int totalDutyHours = records.stream().mapToInt(CrewFlightTime::getDutyHours).sum();

        Crew crew = crewMapper.selectById(crewId);

        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("crewId", crewId);
        summary.put("crewName", crew != null ? crew.getName() : "");
        summary.put("department", crew != null ? crew.getDepartment() : "");
        summary.put("period", startDate + " ~ " + LocalDate.now());
        summary.put("totalFlightMinutes", totalFlightHours);
        summary.put("totalDutyMinutes", totalDutyHours);
        summary.put("totalFlightHours", String.format("%.1f", totalFlightHours / 60.0));
        summary.put("totalDutyHours", String.format("%.1f", totalDutyHours / 60.0));
        summary.put("flightsCount", records.size());

        // 合规检查（CAAC规定：月飞行不超过100小时，日值勤不超过14小时）
        summary.put("monthlyLimitOk", totalFlightHours <= 100 * 60);
        summary.put("dailyLimitOk", records.stream().noneMatch(r -> r.getDutyHours() > 14 * 60));

        result.add(summary);
        return result;
    }

    /**
     * 合规状态自动刷新：① 按 expire_date 校准资质状态；② 飞行部机组资质全部失效 → 自动停飞，
     * 补齐后自动复飞。排班 / 动态 / 合规看板入口均会调用，保证校验与展示基于最新状态。
     */
    @Override
    public void refreshComplianceStatus() {
        LocalDate today = LocalDate.now();

        // ① 资质状态按有效期自动校准（修复"已过期却仍为 EXPIRING_SOON / VALID"的脏状态）
        List<CrewQualification> allQuals = crewQualificationMapper.selectList(
                new LambdaQueryWrapper<CrewQualification>().eq(CrewQualification::getDeleted, 0));
        for (CrewQualification q : allQuals) {
            String desired = computeQualStatus(q, today);
            if (!desired.equals(q.getStatus())) {
                crewQualificationMapper.update(null, new LambdaUpdateWrapper<CrewQualification>()
                        .eq(CrewQualification::getId, q.getId())
                        .set(CrewQualification::getStatus, desired));
                log.info("资质状态校准: 机组{} 资质『{}』 {} → {}", q.getCrewId(), q.getName(), q.getStatus(), desired);
            }
        }

        // ② 停飞/复飞联动（针对飞行部机组：名下资质全部失效即视为不可执行飞行任务）
        List<Crew> flightCrew = crewMapper.selectList(new LambdaQueryWrapper<Crew>()
                .eq(Crew::getDeleted, 0)
                .eq(Crew::getDepartment, "飞行部")
                .in(Crew::getStatus, "STANDBY", "FLYING", "GROUNDED"));
        for (Crew crew : flightCrew) {
            List<CrewQualification> cqs = crewQualificationMapper.selectList(
                    new LambdaQueryWrapper<CrewQualification>()
                            .eq(CrewQualification::getCrewId, crew.getId())
                            .eq(CrewQualification::getDeleted, 0));
            boolean hasEffective = cqs.stream().anyMatch(q -> qualEffective(q, today));

            if ("GROUNDED".equals(crew.getStatus())) {
                // 因「超月飞行时限」停飞的机组不在此复飞，须由状态机走完 停飞 → 培训 → 待命
                if (CrewStatusService.REASON_OVER_LIMIT.equals(crew.getStatusReason())) {
                    continue;
                }
                if (cqs.isEmpty() || hasEffective) { // 资质已补齐 → 复飞
                    crewMapper.update(null, new LambdaUpdateWrapper<Crew>()
                            .eq(Crew::getId, crew.getId())
                            .set(Crew::getStatus, "STANDBY")
                            .set(Crew::getStatusUntil, null)
                            .set(Crew::getStatusReason, null));
                    log.info("机组 {} 资质已补齐，停飞解除 → STANDBY", crew.getName());
                }
            } else if (!cqs.isEmpty() && !hasEffective) {
                crewMapper.update(null, new LambdaUpdateWrapper<Crew>()
                        .eq(Crew::getId, crew.getId())
                        .set(Crew::getStatus, "GROUNDED")
                        .set(Crew::getStatusUntil, null)
                        .set(Crew::getStatusReason, CrewStatusService.REASON_QUAL_INVALID));
                // 自动护班：撤销该机组尚未执行的排班，避免停飞机组仍被安排飞行
                int revoked = crewScheduleMapper.delete(new LambdaQueryWrapper<CrewSchedule>()
                        .eq(CrewSchedule::getCrewId, crew.getId())
                        .ge(CrewSchedule::getScheduleDate, today)
                        .eq(CrewSchedule::getDeleted, 0));
                log.warn("机组 {} 名下资质已全部失效，自动停飞 → GROUNDED{}",
                        crew.getName(), revoked > 0 ? "，已撤销其待执行排班 " + revoked + " 条" : "");
            }
        }
    }

    @Override
    public List<Map<String, Object>> getComplianceWarnings() {
        refreshComplianceStatus();
        List<Map<String, Object>> warnings = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // 1. 飞行时间超限检查（本日历月累计超过 100 小时，与排班校验同一口径）
        LocalDate monthStart = today.withDayOfMonth(1);
        List<Crew> allCrew = crewMapper.selectList(new LambdaQueryWrapper<Crew>().eq(Crew::getDeleted, 0));
        for (Crew crew : allCrew) {
            Integer totalMinutes = crewFlightTimeMapper.selectList(
                            new LambdaQueryWrapper<CrewFlightTime>()
                                    .eq(CrewFlightTime::getCrewId, crew.getId())
                                    .ge(CrewFlightTime::getFlightDate, monthStart))
                    .stream().mapToInt(CrewFlightTime::getFlightHours).sum();

            if (totalMinutes > 100 * 60) { // 超过100小时
                warnings.add(Map.of(
                        "type", "FLIGHT_TIME_EXCEEDED",
                        "crewId", crew.getId().toString(),
                        "crewName", crew.getName(),
                        "totalHours", String.format("%.1f", totalMinutes / 60.0),
                        "limit", "100",
                        "description", String.format("本日历月飞行时间已超100小时上限（当前 %.1f 小时）",
                                totalMinutes / 60.0)
                ));
            }
        }

        // 2. 资质预警：30 天内到期（QUALIFICATION_EXPIRING）+ 已过期（QUALIFICATION_EXPIRED）
        Map<Long, String> crewNames = crewMapper.selectList(
                        new LambdaQueryWrapper<Crew>().eq(Crew::getDeleted, 0))
                .stream().collect(Collectors.toMap(Crew::getId, Crew::getName, (x, y) -> x));
        List<CrewQualification> quals = crewQualificationMapper.selectList(
                new LambdaQueryWrapper<CrewQualification>()
                        .eq(CrewQualification::getDeleted, 0)
                        .orderByAsc(CrewQualification::getExpireDate));
        for (CrewQualification q : quals) {
            if (q.getExpireDate() == null) continue;
            long daysRemaining = ChronoUnit.DAYS.between(today, q.getExpireDate());
            if (daysRemaining <= 30) {
                boolean expired = daysRemaining < 0;
                String crewName = crewNames.getOrDefault(q.getCrewId(), "");
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("type", expired ? "QUALIFICATION_EXPIRED" : "QUALIFICATION_EXPIRING");
                w.put("crewId", q.getCrewId().toString());
                w.put("crewName", crewName);
                w.put("qualName", q.getName());
                w.put("number", q.getNumber());
                w.put("expireDate", q.getExpireDate().toString());
                w.put("daysRemaining", daysRemaining);
                w.put("description", expired
                        ? crewName + "的「" + q.getName() + "」已于 " + Math.abs(daysRemaining) + " 天前过期，资质已失效，请尽快续证/复训"
                        : crewName + "的「" + q.getName() + "」将在 " + daysRemaining + " 天后到期，请提前安排续证/复训");
                warnings.add(w);
            }
        }

        return warnings;
    }

    @Override
    public Map<String, Object> getDashboard() {
        List<Map<String, Object>> warnings = getComplianceWarnings(); // 内部已 refresh
        long flightTimeWarnings = warnings.stream()
                .filter(w -> "FLIGHT_TIME_EXCEEDED".equals(w.get("type"))).count();
        long expiringQualWarnings = warnings.stream()
                .filter(w -> "QUALIFICATION_EXPIRING".equals(w.get("type"))).count();
        long expiredQualWarnings = warnings.stream()
                .filter(w -> "QUALIFICATION_EXPIRED".equals(w.get("type"))).count();
        long groundedCrew = crewMapper.selectCount(new LambdaQueryWrapper<Crew>()
                .eq(Crew::getDeleted, 0)
                .eq(Crew::getStatus, "GROUNDED"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalWarnings", warnings.size());
        result.put("flightTimeWarnings", flightTimeWarnings);
        result.put("qualificationWarnings", expiringQualWarnings + expiredQualWarnings);
        result.put("expiringQualWarnings", expiringQualWarnings);
        result.put("expiredQualWarnings", expiredQualWarnings);
        result.put("groundedCrew", groundedCrew);
        result.put("warnings", warnings);

        return result;
    }

    // ==================== 私有方法 ====================

    /** 按有效期计算资质应处状态：已过期 / 30 天内到期 / 有效 */
    private String computeQualStatus(CrewQualification q, LocalDate today) {
        if (q.getExpireDate() == null) return "VALID";
        LocalDate e = q.getExpireDate();
        if (e.isBefore(today)) return "EXPIRED";
        if (!e.isAfter(today.plusDays(30))) return "EXPIRING_SOON";
        return "VALID";
    }

    /** 资质是否仍有效（无到期日视为长期有效） */
    private boolean qualEffective(CrewQualification q, LocalDate today) {
        return q.getExpireDate() == null || !q.getExpireDate().isBefore(today);
    }

    @Override
    public void recordFlightTime(Long crewId, Long flightId, String role, int flightMinutes, int dutyMinutes) {
        CrewFlightTime record = new CrewFlightTime();
        record.setCrewId(crewId);
        record.setFlightDate(LocalDate.now());
        record.setFlightHours(flightMinutes);
        record.setDutyHours(dutyMinutes);
        record.setRestRequired(dutyMinutes > 12 * 60 ? 1 : 0);
        record.setFlightId(flightId);
        record.setRole(role);
        crewFlightTimeMapper.insert(record);

        // 检查是否超限
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        Integer totalMinutes = crewFlightTimeMapper.selectList(
                        new LambdaQueryWrapper<CrewFlightTime>()
                                .eq(CrewFlightTime::getCrewId, crewId)
                                .ge(CrewFlightTime::getFlightDate, thirtyDaysAgo))
                .stream().mapToInt(CrewFlightTime::getFlightHours).sum();

        if (totalMinutes > 90 * 60) { // 超过90小时预警
            Crew crew = crewMapper.selectById(crewId);
            Map<String, Object> alert = Map.of(
                    "type", "CREW_COMPLIANCE_ALERT",
                    "crewId", crewId.toString(),
                    "crewName", crew != null ? crew.getName() : "",
                    "totalHours", String.format("%.1f", totalMinutes / 60.0),
                    "description", "该机组近30天飞行时间已达" + String.format("%.1f", totalMinutes / 60.0) + "小时，接近100小时上限"
            );
            adminIrregularWebSocket.pushToAdmins("CREW_COMPLIANCE_ALERT",
                    "{\"data\":" + cn.hutool.json.JSONUtil.toJsonStr(alert) + "}");
        }
    }
}
