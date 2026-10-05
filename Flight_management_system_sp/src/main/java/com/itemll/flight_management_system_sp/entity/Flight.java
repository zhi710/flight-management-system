package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 航班 */
@Data
@TableName("flight")
public class Flight {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String flightNo;
    private LocalDate flightDate;
    private String departureAirport;
    private String arrivalAirport;
    private String departureTerminal;
    private String arrivalTerminal;
    private String departureGate;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private Integer duration;
    private Long aircraftId;
    private Long airlineId;
    private String flightType;
    private String status;
    /** 值机服务状态：NOT_OPEN / OPEN / CLOSED（与运行状态 status 解耦） */
    private String checkinStatus;
    /** 值机开放：起飞前多少小时（默认 24h） */
    private Integer checkinOpenHours;
    /** 值机关闭：起飞前多少分钟（默认 30min） */
    private Integer checkinCloseMinutes;
    private Integer stops;
    private Integer punctuality;
    private Integer mealProvided;
    private Integer wifi;
    private Integer power;
    private String remark;
    private String iataDelayCode;
    private String compensationRule;
    private LocalDateTime actualDepartureTime;
    private LocalDateTime actualArrivalTime;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ==================== 值机服务状态（与运行状态 status 解耦） ====================

    public static final String CHECKIN_NOT_OPEN = "NOT_OPEN";
    public static final String CHECKIN_OPEN = "OPEN";
    public static final String CHECKIN_CLOSED = "CLOSED";

    /**
     * 值机是否开放（旅客能否办理值机）。
     * <p>值机服务状态与航班运行状态是两个维度，组合判断：</p>
     * <ul>
     *   <li>运行状态不是「已计划/延误」→ 关闭</li>
     *   <li>管理员显式关闭(CLOSED) → 关闭</li>
     *   <li>管理员显式开放(OPEN) → 开放</li>
     *   <li>默认(NOT_OPEN) → 按时间窗口 [T - checkinOpenHours, T - checkinCloseMinutes) 自动开放</li>
     * </ul>
     */
    public boolean isCheckinOpen(LocalDateTime now) {
        if (!"SCHEDULED".equals(status) && !"DELAYED".equals(status)) {
            return false;
        }
        if (CHECKIN_CLOSED.equals(checkinStatus)) {
            return false;
        }
        if (CHECKIN_OPEN.equals(checkinStatus)) {
            return true;
        }
        if (departureTime == null) {
            return false;
        }
        int openHours = checkinOpenHours != null ? checkinOpenHours : 24;
        int closeMinutes = checkinCloseMinutes != null ? checkinCloseMinutes : 30;
        return !now.isBefore(departureTime.minusHours(openHours))
                && now.isBefore(departureTime.minusMinutes(closeMinutes));
    }
}
