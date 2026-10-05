package com.itemll.flight_management_system_sp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itemll.flight_management_system_sp.entity.FrequentTraveler;
import org.apache.ibatis.annotations.Select;

public interface FrequentTravelerMapper extends BaseMapper<FrequentTraveler> {

    /**
     * 取常用旅客表里已签发常旅客号的最大序号（{@code FF} 号段）。
     *
     * <p>与 {@link UserMapper#selectMaxMemberNoSequence()} 一起构成**统一号池**：
     * 号码既可能签发给注册会员（{@code sys_user.member_no}），也可能先签发给尚未注册的
     * 旅客档案（{@code frequent_traveler.frequent_flyer_no}），两者必须按同一个序列递增，
     * 否则会出现「同一号段两套计数器」而重号。
     *
     * <p>刻意不加 {@code deleted = 0}：逻辑删除的档案其号也必须占位，否则号会被回收重用。
     */
    @Select("SELECT MAX(CAST(SUBSTRING(frequent_flyer_no, 3) AS UNSIGNED)) FROM frequent_traveler "
            + "WHERE frequent_flyer_no REGEXP '^FF[0-9]+$'")
    Long selectMaxFlyerNoSequence();
}
