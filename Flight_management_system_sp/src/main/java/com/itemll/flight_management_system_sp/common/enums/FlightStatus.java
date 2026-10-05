package com.itemll.flight_management_system_sp.common.enums;

import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;

import java.util.Map;
import java.util.Set;

/**
 * 航班运行状态枚举 + 状态机转换表（唯一事实来源）
 * <p>只描述「航班/飞机的运行阶段」。值机是否开放属于旅客服务维度，
 * 已拆分为 {@code flight.checkin_status} 独立字段，不在此状态机内。</p>
 *
 * <pre>
 * 正常主流程：SCHEDULED → BOARDING → DEPARTED → (FLYING) → ARRIVED → COMPLETED
 * 异常分支：  任意进行中状态 → DELAYED / CANCELLED；DEPARTED/FLYING → DIVERTED / RETURNED
 * </pre>
 */
public enum FlightStatus {

    // ── 正常主流程 ──
    SCHEDULED("已计划"),
    BOARDING("登机中"),
    DEPARTED("已起飞"),
    FLYING("飞行中"),
    ARRIVED("已到达"),
    COMPLETED("已完成"),

    // ── 异常分支 ──
    DELAYED("延误"),
    CANCELLED("已取消"),
    DIVERTED("备降"),
    RETURNED("返航");

    private final String label;

    FlightStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 每个状态的合法出边（允许流转到的目标状态集合） */
    private static final Map<FlightStatus, Set<FlightStatus>> TRANSITIONS = Map.ofEntries(
            Map.entry(SCHEDULED, Set.of(BOARDING, DEPARTED, DELAYED, CANCELLED)),
            Map.entry(BOARDING,  Set.of(DEPARTED, DELAYED, CANCELLED)),
            Map.entry(DEPARTED,  Set.of(FLYING, ARRIVED, DIVERTED, RETURNED, DELAYED, CANCELLED)),
            Map.entry(FLYING,    Set.of(ARRIVED, DIVERTED, RETURNED, DELAYED, CANCELLED)),
            Map.entry(ARRIVED,   Set.of(COMPLETED)),
            Map.entry(COMPLETED, Set.of()),
            Map.entry(CANCELLED, Set.of()),
            Map.entry(DIVERTED,  Set.of()),
            Map.entry(RETURNED,  Set.of()),
            Map.entry(DELAYED,   Set.of(BOARDING, DEPARTED, CANCELLED))
    );

    /** 是否为终止态（不再流转） */
    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    /** 是否允许从当前状态流转到目标状态（同状态重复设置视为幂等 no-op） */
    public boolean canTransitionTo(FlightStatus target) {
        if (target == this) {
            return true;
        }
        return target != null && TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** 校验流转合法性，非法则抛业务异常 */
    public void requireTransitionTo(FlightStatus target) {
        if (!canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "非法状态流转: " + this.label + " → " + (target != null ? target.label : "null"));
        }
    }

    /** 从数据库存储的字符串解析（严格），未知/空状态抛异常 */
    public static FlightStatus fromCode(String code) {
        if (code == null || code.isEmpty()) {
            throw new BusinessException("航班状态为空");
        }
        for (FlightStatus s : values()) {
            if (s.name().equalsIgnoreCase(code)) {
                return s;
            }
        }
        throw new BusinessException("未知航班状态: " + code);
    }

    /** 解析并返回中文标签（宽松），未知状态返回原值，用于展示 */
    public static String labelOf(String code) {
        if (code == null) {
            return "";
        }
        for (FlightStatus s : values()) {
            if (s.name().equalsIgnoreCase(code)) {
                return s.label;
            }
        }
        return code;
    }
}
