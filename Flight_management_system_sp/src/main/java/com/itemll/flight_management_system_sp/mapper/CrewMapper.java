package com.itemll.flight_management_system_sp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itemll.flight_management_system_sp.entity.Crew;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface CrewMapper extends BaseMapper<Crew> {

    /**
     * 按工号查询（含已逻辑删除的记录）。
     * <p>crew 表对 crew_id 建了唯一索引，逻辑删除的行仍占用该工号，
     * 所以新增/改工号前必须连已删除的行一起查，否则会撞唯一索引报 SQL 错。</p>
     */
    @Select("SELECT * FROM crew WHERE crew_id = #{crewId} LIMIT 1")
    Crew selectByCrewIdIncludeDeleted(@Param("crewId") String crewId);

    /**
     * 复用一条已逻辑删除的机组记录：重置资料与状态并取消删除标记。
     * <p>复用原 id，使历史排班/飞行时长仍然关联到本人；名下资质保持不动。</p>
     */
    @Update("UPDATE crew SET name = #{name}, gender = #{gender}, phone = #{phone}, email = #{email}, "
            + "department = #{department}, status = 'STANDBY', status_until = NULL, status_reason = NULL, "
            + "deleted = 0, update_time = NOW() WHERE id = #{id}")
    int revive(Crew crew);
}
