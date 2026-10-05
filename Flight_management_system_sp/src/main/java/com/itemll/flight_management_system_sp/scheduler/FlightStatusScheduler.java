package com.itemll.flight_management_system_sp.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.enums.FlightStatus;
import com.itemll.flight_management_system_sp.entity.CrewFlightTime;
import com.itemll.flight_management_system_sp.entity.CrewSchedule;
import com.itemll.flight_management_system_sp.entity.Flight;
import com.itemll.flight_management_system_sp.mapper.CrewFlightTimeMapper;
import com.itemll.flight_management_system_sp.mapper.CrewScheduleMapper;
import com.itemll.flight_management_system_sp.security.FlightStatusWebSocketHandler;
import com.itemll.flight_management_system_sp.service.CrewStatusService;
import com.itemll.flight_management_system_sp.service.FlightStateMachine;
import com.itemll.flight_management_system_sp.service.GroundHandlingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 航班状态自动推进定时任务
 * <p>每 30 秒扫描一次，根据出发/到达时间自动推进航班运行状态。
 * 状态流转合法性统一由 {@link FlightStatus} 状态机校验，这里只负责「按计划时间兜底」。</p>
 *
 * <pre>
 * 状态流转图：
 *   SCHEDULED ──(起飞前30min)──→ BOARDING
 *   BOARDING  ──(起飞时间到)──→ DEPARTED
 *   SCHEDULED ──(起飞时间到)──→ DEPARTED   （跳过登机，直飞场景）
 *   DEPARTED  ──(到达时间到)──→ ARRIVED
 *   ARRIVED   ──(到达后2h)──→ COMPLETED
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightStatusScheduler {

    private final FlightStateMachine flightStateMachine;
    private final CrewScheduleMapper crewScheduleMapper;
    private final CrewFlightTimeMapper crewFlightTimeMapper;
    private final FlightStatusWebSocketHandler flightStatusWebSocket;
    private final GroundHandlingService groundHandlingService;
    private final CrewStatusService crewStatusService;

    /** 每 30 秒执行一次 */
    @Scheduled(fixedDelay = 30_000)
    public void advanceFlightStatus() {
        LocalDateTime now = LocalDateTime.now();
        log.debug("开始扫描航班状态推进...");

        // 1. SCHEDULED → BOARDING（起飞前 30 分钟）
        advanceAndNotify(FlightStatus.SCHEDULED, FlightStatus.BOARDING,
                f -> f.getDepartureTime() != null && !f.getDepartureTime().isAfter(now.plusMinutes(30)),
                "起飞前30分钟自动开放登机");

        // 2. BOARDING → DEPARTED（起飞时间已到）
        advanceAndNotify(FlightStatus.BOARDING, FlightStatus.DEPARTED,
                f -> f.getDepartureTime() != null && !f.getDepartureTime().isAfter(now),
                "起飞时间已到，自动标记已起飞");

        // 3. SCHEDULED → DEPARTED（跳过登机，直飞或漏推进的场景）
        advanceAndNotify(FlightStatus.SCHEDULED, FlightStatus.DEPARTED,
                f -> f.getDepartureTime() != null && !f.getDepartureTime().isAfter(now),
                "起飞时间已过未登机，自动标记已起飞");

        // 4. DEPARTED → ARRIVED（到达时间已到）
        advanceAndNotify(FlightStatus.DEPARTED, FlightStatus.ARRIVED,
                f -> f.getArrivalTime() != null && !f.getArrivalTime().isAfter(now),
                "到达时间已到，自动标记已到达");

        // 5. ARRIVED → COMPLETED（到达后 2 小时）
        advanceAndNotify(FlightStatus.ARRIVED, FlightStatus.COMPLETED,
                f -> f.getArrivalTime() != null && !f.getArrivalTime().isAfter(now.minusHours(2)),
                "航班到达后2小时，自动标记已完成");
    }

    private void advanceAndNotify(FlightStatus from, FlightStatus to,
                                  Predicate<Flight> filter, String reason) {
        List<Flight> transitioned = flightStateMachine.advance(from, to, filter, reason);
        if (transitioned.isEmpty()) {
            return;
        }

        for (Flight f : transitioned) {
            // 推送航班动态 WebSocket
            String json = String.format(
                    "{\"flightNo\":\"%s\",\"status\":\"%s\",\"oldStatus\":\"%s\",\"reason\":\"%s\"}",
                    f.getFlightNo(), to.name(), from.name(), reason);
            flightStatusWebSocket.pushFlightUpdate(f.getFlightNo(), json);

            // 进入登机时：自动完成相关地面保障节点
            if (to == FlightStatus.BOARDING) {
                groundHandlingService.autoCompleteByFlightStatus(f.getId(), FlightStatus.BOARDING.name());
            }

            // 航班到达时：自动记录所有排班机组的飞行时间
            if (to == FlightStatus.ARRIVED) {
                recordCrewFlightTime(f);
                // 机组状态机：当天航班飞完后进入强制休息（REST）
                crewStatusService.onFlightArrived(f);
            }
        }

        log.info("航班状态推进: {} → {}, 共 {} 个航班（{} 等）",
                from.name(), to.name(), transitioned.size(),
                transitioned.stream().map(Flight::getFlightNo).limit(5).collect(Collectors.joining(", ")));
    }

    /**
     * 航班到达时自动记录该航班所有排班机组的飞行时间
     */
    private void recordCrewFlightTime(Flight flight) {
        List<CrewSchedule> schedules = crewScheduleMapper.selectList(
                new LambdaQueryWrapper<CrewSchedule>()
                        .eq(CrewSchedule::getFlightId, flight.getId())
                        .eq(CrewSchedule::getDeleted, 0));
        if (schedules.isEmpty()) return;

        int flightMinutes = flight.getDuration() != null ? flight.getDuration() : 120;
        int dutyMinutes = flightMinutes + 60; // 执勤时间 = 飞行时间 + 前后各30分钟准备/收尾

        for (CrewSchedule s : schedules) {
            // 幂等：同一机组同一航班同一天不重复记录
            Long exists = crewFlightTimeMapper.selectCount(
                    new LambdaQueryWrapper<CrewFlightTime>()
                            .eq(CrewFlightTime::getCrewId, s.getCrewId())
                            .eq(CrewFlightTime::getFlightId, flight.getId())
                            .eq(CrewFlightTime::getFlightDate, flight.getFlightDate()));
            if (exists > 0) continue;

            CrewFlightTime ct = new CrewFlightTime();
            ct.setCrewId(s.getCrewId());
            ct.setFlightDate(flight.getFlightDate() != null ? flight.getFlightDate() : LocalDate.now());
            ct.setFlightHours(flightMinutes);
            ct.setDutyHours(dutyMinutes);
            ct.setFlightId(flight.getId());
            ct.setRole(s.getRole());
            ct.setRestRequired(dutyMinutes > 12 * 60 ? 1 : 0);
            crewFlightTimeMapper.insert(ct);
        }

        log.info("航班 {} 到达，已为 {} 名机组记录飞行时间（飞行{}min/执勤{}min）",
                flight.getFlightNo(), schedules.size(), flightMinutes, dutyMinutes);
    }
}
