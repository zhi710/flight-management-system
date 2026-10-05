package com.itemll.flight_management_system_sp.scheduler;

import com.itemll.flight_management_system_sp.service.CrewComplianceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 机组合规状态每日定时刷新
 * <p>凌晨 02:17 自动校准一次资质状态（过期 → EXPIRED、临期 → EXPIRING_SOON），
 * 并对资质全部失效的飞行部机组执行自动停飞 / 复飞联动。</p>
 * <p>除本任务外，排班、机组动态、合规看板等入口也会惰性刷新，保证展示即最新。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrewComplianceScheduler {

    private final CrewComplianceService crewComplianceService;

    /** 每天 02:17 执行一次 */
    @Scheduled(cron = "0 17 2 * * ?")
    public void dailyRefresh() {
        try {
            crewComplianceService.refreshComplianceStatus();
            log.info("机组合规状态每日定时刷新完成");
        } catch (Exception e) {
            log.error("机组合规状态每日定时刷新失败", e);
        }
    }
}
