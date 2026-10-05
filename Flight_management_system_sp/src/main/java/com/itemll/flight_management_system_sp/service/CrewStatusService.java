package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.entity.Flight;

/**
 * 机组状态机
 *
 * <pre>
 * 状态流转（时长均可在「系统配置」中调整，键名见实现类）：
 *   排班（当天）        → FLYING
 *   航班到达            → REST      （固定休息时长）
 *   REST 到期 + 本月未超 100h → STANDBY
 *   REST 到期 + 本月已超 100h → GROUNDED（固定停飞时长）
 *   GROUNDED(超限) 到期 → TRAINING  （固定培训时长）
 *   TRAINING 到期       → STANDBY
 *
 * 注意：因「资质失效」产生的 GROUNDED 不在此状态机内推进，须等资质补齐后由
 * 合规服务直接复飞，避免超限停飞与资质停飞互相覆盖。
 * </pre>
 */
public interface CrewStatusService {

    /** 状态来源：超月飞行时限停飞 */
    String REASON_OVER_LIMIT = "OVER_LIMIT";
    /** 状态来源：资质失效停飞 */
    String REASON_QUAL_INVALID = "QUAL_INVALID";
    /** 状态来源：航班到达后强制休息 */
    String REASON_FLIGHT_REST = "FLIGHT_REST";

    /**
     * 航班到达后调用：若该机组当天已无未落地的航班，则进入强制休息。
     */
    void onFlightArrived(Flight flight);

    /**
     * 按时间推进所有已到期的状态（REST / GROUNDED(超限) / TRAINING），返回被推进的机组数。
     */
    int advanceStatuses();
}
