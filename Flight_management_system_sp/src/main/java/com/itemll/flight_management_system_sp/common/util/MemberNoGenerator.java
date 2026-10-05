package com.itemll.flight_management_system_sp.common.util;

import com.itemll.flight_management_system_sp.mapper.FrequentTravelerMapper;
import com.itemll.flight_management_system_sp.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 常旅客号（会员号）生成器 —— 全平台**唯一号池**。
 *
 * <p>规则：{@code FF} + 8 位零填充顺序号，例如 {@code FF00000007}。
 *
 * <p><b>为什么号池要跨两张表</b>：号是发给「人」的，不是发给「账号」的。
 * 一个旅客可能先被他人作为常用旅客建档、拿到号（此时还没有账号），之后才注册。
 * 因此号码同时可能落在 {@code sys_user.member_no} 与
 * {@code frequent_traveler.frequent_flyer_no}，序号必须取两者的**全局最大值**，
 * 否则两套计数器会各自从 1 开始而重号。
 *
 * <p>取最大值而非计数器表的好处：人工插入的号也会被自动计入，序号不会与数据漂移。
 *
 * <p><b>并发说明</b>：本方法用 JVM 内锁串行化「读最大值 → 生成下一个」。单实例部署下足够；
 * 多实例部署时最终防线是 {@code sys_user.uk_member_no} 与
 * {@code frequent_traveler.uk_frequent_flyer_no} 两个唯一索引 —— 撞号会在写入时直接失败，
 * 而不是静默产生两个同号旅客。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberNoGenerator {

    /** 常旅客号前缀 */
    public static final String PREFIX = "FF";

    /** 序号位数（零填充） */
    private static final int SEQUENCE_WIDTH = 8;

    private final UserMapper userMapper;
    private final FrequentTravelerMapper travelerMapper;

    /** 生成下一个常旅客号 */
    public String next() {
        synchronized (this) {
            return format(nextSequence());
        }
    }

    /** 当前已发放的最大序号（0 表示尚未发放过任何号） */
    public long nextSequence() {
        Long byMember = userMapper.selectMaxMemberNoSequence();
        Long byTraveler = travelerMapper.selectMaxFlyerNoSequence();
        long max = Math.max(byMember == null ? 0L : byMember,
                byTraveler == null ? 0L : byTraveler);
        return max + 1L;
    }

    /** 按序号格式化：1 → {@code FF00000001} */
    public static String format(long sequence) {
        return PREFIX + String.format("%0" + SEQUENCE_WIDTH + "d", sequence);
    }
}
