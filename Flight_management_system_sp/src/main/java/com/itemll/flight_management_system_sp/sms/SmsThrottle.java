package com.itemll.flight_management_system_sp.sms;

import cn.hutool.core.util.StrUtil;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.config.SmsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 短信限流与校验防爆破
 *
 * <p><b>为什么必须有这个类：</b>改造前 60 秒倒计时只存在于前端（按钮 disabled），
 * 后端可以被无限调用 —— 演示模式下无所谓，但接入真实短信后每一条都是钱，
 * 一个脚本就能把余额刷空；同时 6 位验证码若没有失败次数限制，可以被暴力枚举。</p>
 *
 * <p>四道闸门：</p>
 * <ol>
 *   <li><b>发送冷却</b>（{@code sms:cd:{手机号}}）：同号 60 秒内不允许重复发送。</li>
 *   <li><b>单号日限</b>（{@code sms:send:daily:{手机号}:{日期}}）：防单号码被反复轰炸。</li>
 *   <li><b>单 IP 时限</b>（{@code sms:send:ip:{IP}:{小时}}）：防换号码批量刷。</li>
 *   <li><b>校验失败锁定</b>（{@code sms:verify:fail:{手机号}}）：连续错 N 次锁定 M 分钟。</li>
 * </ol>
 *
 * <p><b>计数口径：</b>日限与 IP 限统计的是「发送尝试」而非「发送成功」，
 * 冷却期内的重复点击同样计入 —— 这是刻意为之：防刷优先，且冷却被拒的请求本身
 * 就是异常行为特征。真正的渠道发送失败会通过 {@link #release(String)} 释放冷却，
 * 避免因为一次网络抖动让用户干等 60 秒。</p>
 *
 * <p><b>已知取舍：</b>计数采用「先 INCR、首次再 EXPIRE」的写法，
 * 若进程恰好在两步之间崩溃，该 key 会失去 TTL 而长期存在；这对本项目可接受
 * （最多是某个号码当天被提前限流），换成 Lua 脚本可彻底原子化。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsThrottle {

    private final StringRedisTemplate redisTemplate;
    private final SmsProperties smsProperties;

    private static final String KEY_COOLDOWN = "sms:cd:";
    private static final String KEY_DAILY = "sms:send:daily:";
    private static final String KEY_IP_HOUR = "sms:send:ip:";
    private static final String KEY_VERIFY_FAIL = "sms:verify:fail:";

    /**
     * 发送前取号：任一闸门超限就抛出 429，由全局异常处理器统一返回给前端。
     *
     * @param phone    手机号
     * @param clientIp 客户端 IP，取不到时跳过 IP 维度（不影响其它闸门）
     */
    public void acquire(String phone, String clientIp) {
        SmsProperties.Throttle limit = smsProperties.getThrottle();

        // 1) 单号日限：先判额度再谈冷却，否则「已超日限」会被误报成「发送过于频繁」，提示不准
        String dailyKey = KEY_DAILY + phone + ":" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        long dailyCount = incrementWithTtl(dailyKey, secondsToMidnight());
        if (dailyCount > limit.getDailyLimitPerPhoneOrDefault()) {
            log.warn("短信发送已达单号日限: phone={}, count={}", mask(phone), dailyCount);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "该手机号今日获取验证码次数已达上限，请明天再试");
        }

        // 2) 单 IP 时限
        if (StrUtil.isNotBlank(clientIp)) {
            String ipKey = KEY_IP_HOUR + clientIp + ":"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
            long ipCount = incrementWithTtl(ipKey, 7200);
            if (ipCount > limit.getHourlyLimitPerIpOrDefault()) {
                log.warn("短信发送已达单 IP 时限: ip={}, count={}", clientIp, ipCount);
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "当前网络获取验证码过于频繁，请稍后再试");
            }
        }

        // 3) 冷却：SETNX 原子占位，并发点击只有一个能通过
        String cooldownKey = KEY_COOLDOWN + phone;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", limit.getCooldownSecondsOrDefault(), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            long remain = Math.max(remainSeconds(cooldownKey), 1L);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "发送过于频繁，请 " + remain + " 秒后再试");
        }
    }

    /**
     * 释放冷却。
     * <p>仅在「本可以发送但渠道发送失败」时调用 —— 渠道侧的问题不该惩罚用户，
     * 否则用户只能盯着倒计时干等。日限与 IP 计数<b>不</b>回滚，避免被反复重试绕过。</p>
     */
    public void release(String phone) {
        try {
            redisTemplate.delete(KEY_COOLDOWN + phone);
        } catch (Exception e) {
            log.warn("释放短信冷却失败: phone={}, {}", mask(phone), e.getMessage());
        }
    }

    /**
     * 校验前检查失败次数。
     *
     * @throws BusinessException 已超过失败次数上限，处于锁定期
     */
    public void checkVerifyAllowed(String phone) {
        String key = KEY_VERIFY_FAIL + phone;
        long fails = parseLong(redisTemplate.opsForValue().get(key));
        int max = smsProperties.getThrottle().getMaxVerifyAttemptsOrDefault();
        if (fails >= max) {
            long remainMinutes = Math.max(remainSeconds(key) / 60, 1L);
            log.warn("验证码校验已锁定: phone={}, 失败={}次", mask(phone), fails);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "验证码错误次数过多，请 " + remainMinutes + " 分钟后再试");
        }
    }

    /** 记录一次校验失败；锁定窗口从第一次失败开始计算，避免后续失败不断续期 */
    public void markVerifyFailed(String phone) {
        String key = KEY_VERIFY_FAIL + phone;
        long fails = incrementWithTtl(key, smsProperties.getThrottle().getVerifyLockMinutesOrDefault() * 60L);
        log.warn("验证码校验失败: phone={}, 累计失败={}次", mask(phone), fails);
    }

    /** 校验通过后清空失败计数 */
    public void clearVerifyFailed(String phone) {
        redisTemplate.delete(KEY_VERIFY_FAIL + phone);
    }

    /** 自增并在首次写入时设置 TTL */
    private long incrementWithTtl(String key, long ttlSeconds) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, Math.max(ttlSeconds, 1L), TimeUnit.SECONDS);
        }
        return value == null ? 0L : value;
    }

    private long remainSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0L : ttl;
    }

    /** 到本地次日零点还剩多少秒，用于让日限计数在自然日边界自动归零 */
    private long secondsToMidnight() {
        return Math.max(Duration.between(LocalDateTime.now(),
                LocalDate.now().plusDays(1).atStartOfDay()).getSeconds(), 1L);
    }

    private long parseLong(String value) {
        try {
            return StrUtil.isBlank(value) ? 0L : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 日志脱敏：138****0001 */
    private String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
