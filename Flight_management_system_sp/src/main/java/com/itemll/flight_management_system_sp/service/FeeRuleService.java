package com.itemll.flight_management_system_sp.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.entity.SysConfig;
import com.itemll.flight_management_system_sp.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 退票 / 改签手续费规则服务
 *
 * <p><b>为什么需要它：</b>改造前退票手续费是写死的 {@code totalAmount × 10%}、
 * 改签手续费是写死的 {@code fare × 5%}，而 {@code sys_config} 里配好的
 * {@code default_refund_rule}、以及 {@code flight_cabin.refund_rule/change_rule}
 * <b>从未被读取过</b> —— 也就是"规则表是摆设，改配置不生效"。
 * 本类把「按剩余时间分档计费」收敛成唯一实现。</p>
 *
 * <p><b>两套规则的分工（不要搞混）：</b></p>
 * <ul>
 *   <li>{@code sys_config.default_refund_rule} / {@code default_change_rule}：
 *       <b>结构化 JSON，是本类的计算真源</b>，形如
 *       {@code {"before24h":0.05,"before2h":0.1,"afterTakeoff":1}}。</li>
 *   <li>{@code flight_cabin.refund_rule} / {@code change_rule}：
 *       <b>中文自由文本</b>（如"起飞前2h手续费10%，之后不可退"），只用于前端展示给旅客看，
 *       无法可靠解析，因此<b>不参与计算</b>。两套口径必须人工保持一致。</li>
 * </ul>
 *
 * <p><b>分档语义</b>（三个档位，值 = 手续费占订单总额的比例）：</p>
 * <ul>
 *   <li>{@code before24h} —— 距起飞 &ge; 24 小时</li>
 *   <li>{@code before2h} —— 距起飞在 [2h, 24h)</li>
 *   <li>{@code afterTakeoff} —— 距起飞 &lt; 2 小时（含已起飞）</li>
 * </ul>
 * 比例为 {@code 1} 表示<b>全额扣费</b>，等价于"按规则不允许退/改"，
 * 调用方应据此拒绝操作，而不是生成一张手续费 100%、实退 0 元的白单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeeRuleService {

    /** 退票手续费规则配置键（已存在） */
    public static final String KEY_REFUND_RULE = "default_refund_rule";
    /** 改签手续费规则配置键（V15 新增） */
    public static final String KEY_CHANGE_RULE = "default_change_rule";

    /** 手续费比例的满额值：达到它即视为"规则不允许" */
    private static final BigDecimal FULL_RATIO = BigDecimal.ONE;
    /** 分档边界 */
    private static final long HOURS_24_MINUTES = 24 * 60L;
    private static final long HOURS_2_MINUTES = 2 * 60L;

    /** 配置缺失时的兜底规则，避免因为一行配置没了就整条退改链路不可用 */
    private static final BigDecimal DEFAULT_BEFORE_24H = new BigDecimal("0.05");
    private static final BigDecimal DEFAULT_BEFORE_2H = new BigDecimal("0.10");

    private final SysConfigMapper sysConfigMapper;

    /**
     * 按规则计算退票手续费比例（0 ~ 1）。
     *
     * @param departureTime 航班计划起飞时间；为 null 时按最严档（afterTakeoff）处理
     */
    public BigDecimal refundFeeRatio(LocalDateTime departureTime) {
        return ratioOf(KEY_REFUND_RULE, departureTime);
    }

    /** 按规则计算改签手续费比例（0 ~ 1），档位语义同退票 */
    public BigDecimal changeFeeRatio(LocalDateTime departureTime) {
        return ratioOf(KEY_CHANGE_RULE, departureTime);
    }

    /** 规则是否允许退票（手续费比例 &lt; 100%） */
    public boolean refundAllowed(LocalDateTime departureTime) {
        return refundFeeRatio(departureTime).compareTo(FULL_RATIO) < 0;
    }

    /** 规则是否允许改签（手续费比例 &lt; 100%） */
    public boolean changeAllowed(LocalDateTime departureTime) {
        return changeFeeRatio(departureTime).compareTo(FULL_RATIO) < 0;
    }

    /**
     * 按比例算出手续费金额（分位四舍五入）。
     *
     * @param amount 计算基数，传订单总额
     * @param ratio  手续费比例
     */
    public BigDecimal feeOf(BigDecimal amount, BigDecimal ratio) {
        if (amount == null || ratio == null) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 命中哪个档位，用于日志与提示文案。
     *
     * @return {@code before24h} / {@code before2h} / {@code afterTakeoff}
     */
    public String stageOf(LocalDateTime departureTime) {
        return stageKey(minutesUntil(departureTime));
    }

    // ==================== 内部实现 ====================

    private BigDecimal ratioOf(String configKey, LocalDateTime departureTime) {
        long minutes = minutesUntil(departureTime);
        JSONObject rule = loadRule(configKey);
        BigDecimal value = rule.getBigDecimal(stageKey(minutes));
        if (value == null) {
            log.warn("退改规则缺少档位配置，按满额手续费处理: configKey={}, stage={}, rule={}",
                    configKey, stageKey(minutes), rule);
            return FULL_RATIO;
        }
        return value;
    }

    private String stageKey(long minutes) {
        if (minutes >= HOURS_24_MINUTES) {
            return "before24h";
        }
        if (minutes >= HOURS_2_MINUTES) {
            return "before2h";
        }
        return "afterTakeoff";
    }

    /** 距起飞分钟数；起飞时间缺失或已过都归入最严档 */
    private long minutesUntil(LocalDateTime departureTime) {
        if (departureTime == null) {
            return -1;
        }
        return Duration.between(LocalDateTime.now(), departureTime).toMinutes();
    }

    /**
     * 读取并解析结构化规则。解析失败时退回兜底档位而不是抛异常 ——
     * 配置写错不应该让旅客连退票入口都点不开；兜底后打 WARN 提醒运维修正。
     */
    private JSONObject loadRule(String configKey) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, configKey));
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            log.warn("退改规则未配置，使用兜底档位: configKey={}", configKey);
            return fallbackRule();
        }
        try {
            JSONObject parsed = JSON.parseObject(config.getConfigValue());
            return parsed == null ? fallbackRule() : parsed;
        } catch (Exception e) {
            log.warn("退改规则格式非法，使用兜底档位: configKey={}, value={}, err={}",
                    configKey, config.getConfigValue(), e.getMessage());
            return fallbackRule();
        }
    }

    private JSONObject fallbackRule() {
        JSONObject fallback = new JSONObject();
        fallback.put("before24h", DEFAULT_BEFORE_24H);
        fallback.put("before2h", DEFAULT_BEFORE_2H);
        fallback.put("afterTakeoff", FULL_RATIO);
        return fallback;
    }
}
