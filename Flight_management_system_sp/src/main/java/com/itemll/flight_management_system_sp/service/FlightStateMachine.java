package com.itemll.flight_management_system_sp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.itemll.flight_management_system_sp.common.enums.FlightStatus;
import com.itemll.flight_management_system_sp.entity.Flight;
import com.itemll.flight_management_system_sp.entity.FlightStatusLog;
import com.itemll.flight_management_system_sp.mapper.FlightMapper;
import com.itemll.flight_management_system_sp.mapper.FlightStatusLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 航班状态机
 * <p>统一负责航班状态流转的「合法性校验 + 持久化 + 审计日志」。
 * 所有状态变更都应经过这里，避免逻辑散落各处、状态机不完整导致航班卡死。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightStateMachine {

    private final FlightMapper flightMapper;
    private final FlightStatusLogMapper statusLogMapper;

    /**
     * 单个航班状态推进（事件驱动）。
     * <p>校验 from→to 合法性后，将目标状态写入实体并更新（@Version 乐观锁），并记录状态日志。</p>
     *
     * @param flight 已加载的航班实体（调用方可先修改其他字段，状态统一在这里写入）
     * @param target 目标状态
     * @param reason 状态变更原因
     * @return 更新后的航班实体
     */
    public Flight transition(Flight flight, FlightStatus target, String reason) {
        FlightStatus from = FlightStatus.fromCode(flight.getStatus());
        from.requireTransitionTo(target);

        flight.setStatus(target.name());
        flightMapper.updateById(flight);

        // 同状态重复设置（如重复延误）只更新实体，不重复记日志
        if (from != target) {
            recordLog(flight.getId(), from, target, reason);
        }
        return flight;
    }

    /**
     * 批量推进（定时任务兜底）。
     * <p>查询仍处于 from 状态的航班，按条件过滤后逐个做「条件更新」推进到 to，天然幂等。</p>
     *
     * @param from   原状态
     * @param to     目标状态
     * @param filter 额外过滤条件（如时间判断）
     * @param reason 状态变更原因
     * @return 实际被推进的航班列表
     */
    public List<Flight> advance(FlightStatus from, FlightStatus to,
                                Predicate<Flight> filter, String reason) {
        from.requireTransitionTo(to);

        List<Flight> flights = flightMapper.selectList(
                new LambdaQueryWrapper<Flight>()
                        .eq(Flight::getStatus, from.name())
                        .eq(Flight::getDeleted, 0));

        List<Flight> matched = flights.stream().filter(filter).toList();
        if (matched.isEmpty()) {
            return List.of();
        }

        List<Flight> updated = new ArrayList<>();
        for (Flight f : matched) {
            int rows = flightMapper.update(null, new LambdaUpdateWrapper<Flight>()
                    .eq(Flight::getId, f.getId())
                    .eq(Flight::getStatus, from.name())
                    .eq(Flight::getDeleted, 0)
                    .set(Flight::getStatus, to.name()));
            if (rows > 0) {
                recordLog(f.getId(), from, to, reason);
                updated.add(f);
            }
        }
        return updated;
    }

    private void recordLog(Long flightId, FlightStatus from, FlightStatus to, String reason) {
        FlightStatusLog logObj = new FlightStatusLog();
        logObj.setFlightId(flightId);
        logObj.setOldStatus(from.name());
        logObj.setNewStatus(to.name());
        logObj.setReason(reason);
        statusLogMapper.insert(logObj);
    }
}
