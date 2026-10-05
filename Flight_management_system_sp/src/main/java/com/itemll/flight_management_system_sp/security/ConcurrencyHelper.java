package com.itemll.flight_management_system_sp.security;

import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 并发控制辅助类
 * <p>基于 Redis 分布式锁实现，用于机票预订等高并发场景。</p>
 * <p>锁粒度：flight:{flightId}:{cabinClass}，同一航班同一舱位的下单请求串行化。</p>
 *
 * <h3>防高并发方案说明：</h3>
 * <ol>
 *   <li><b>第一层：Redis 分布式锁</b> — 将同一航班+舱位的并发请求串行化，防止超卖</li>
 *   <li><b>第二层：数据库乐观锁</b> — flight_cabin 表的 version 字段，CAS 更新座位数</li>
 *   <li><b>第三层：SQL 条件扣减</b> — UPDATE ... SET available_seats = available_seats - N
 *       WHERE available_seats >= N，数据库层面保证不会扣成负数</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConcurrencyHelper {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String LOCK_PREFIX = "lock:flight:";
    private static final long LOCK_TIMEOUT_SECONDS = 5; // 锁超时 5 秒

    /**
     * 尝试获取分布式锁
     *
     * @param flightId   航班ID
     * @param cabinClass 舱位等级
     * @return true=获取成功，false=获取失败（说明有并发冲突）
     */
    public boolean tryLock(Long flightId, String cabinClass) {
        String key = LOCK_PREFIX + flightId + ":" + cabinClass;
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放分布式锁
     */
    public void unlock(Long flightId, String cabinClass) {
        String key = LOCK_PREFIX + flightId + ":" + cabinClass;
        stringRedisTemplate.delete(key);
    }

    /**
     * 获取锁并执行业务逻辑（推荐使用此方法）
     *
     * @param flightId   航班ID
     * @param cabinClass 舱位等级
     * @param action     业务逻辑
     * @param <T>        返回类型
     * @return 业务返回值
     */
    public <T> T executeWithLock(Long flightId, String cabinClass, LockAction<T> action) {
        boolean locked = tryLock(flightId, cabinClass);
        if (!locked) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前航班预订人数较多，请稍后重试");
        }
        try {
            return action.execute();
        } finally {
            unlock(flightId, cabinClass);
        }
    }

    /**
     * 锁内执行的业务逻辑函数式接口
     */
    @FunctionalInterface
    public interface LockAction<T> {
        T execute();
    }
}
