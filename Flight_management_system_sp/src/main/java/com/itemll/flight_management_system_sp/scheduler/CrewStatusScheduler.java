package com.itemll.flight_management_system_sp.scheduler;

import com.itemll.flight_management_system_sp.service.CrewStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 机组状态机定时推进
 * <p>每分钟扫描一次，把已到期的 休息 / 停飞(超限) / 培训 推进到下一状态。</p>
 * <p>「航班到达 → 休息」由 {@link FlightStatusScheduler} 在航班转 ARRIVED 时即时触发；
 * 机组动态、排班等入口也会惰性推进一次，保证打开页面即为最新状态。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrewStatusScheduler {

    private final CrewStatusService crewStatusService;

    /** 每分钟执行一次 */
    @Scheduled(fixedDelay = 60_000)
    public void advanceCrewStatuses() {
        try {
            int changed = crewStatusService.advanceStatuses();
            if (changed > 0) {
                log.info("机组状态定时推进完成，共 {} 人次状态变更", changed);
            }
        } catch (Exception e) {
            log.error("机组状态定时推进失败", e);
        }
    }
}
