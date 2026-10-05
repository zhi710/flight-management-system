package com.itemll.flight_management_system_sp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itemll.flight_management_system_sp.entity.User;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<User> {

    /**
     * 取当前已发放的常旅客号最大序号（{@code FF} 号段）。
     *
     * <p>刻意不加 {@code deleted = 0}：被逻辑删除的会员其号也必须占位，否则号会被回收重用。
     * 无数据时返回 {@code null}，由调用方按「从 1 开始」处理。
     */
    @Select("SELECT MAX(CAST(SUBSTRING(member_no, 3) AS UNSIGNED)) FROM sys_user "
            + "WHERE member_no REGEXP '^FF[0-9]+$'")
    Long selectMaxMemberNoSequence();
}
