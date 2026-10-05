package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 自定义报表：报表定义 + 最近一次生成结果。
 * <p>结果与定义同表存（{@code result_json}），打开页面即可看到上次的生成内容，
 * 无需每次进入都重算 —— 聚合查询走的是订单主表，数据量大时不宜频繁触发。
 */
@Data
@TableName("custom_report")
public class CustomReport {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 报表名称 */
    private String name;

    /** 统计维度：DATE / ROUTE / AIRLINE / CABIN */
    private String dimension;

    private LocalDate startDate;
    private LocalDate endDate;

    /** 创建人 */
    private String creator;

    /** 最近一次生成时间，NULL 表示从未生成 */
    private LocalDateTime lastGeneratedAt;

    /** 最近一次生成的行数 */
    private Integer rowCount;

    /** 最近一次生成结果（JSON 数组） */
    private String resultJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
