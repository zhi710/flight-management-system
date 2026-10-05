package com.itemll.flight_management_system_sp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itemll.flight_management_system_sp.entity.FlightCabin;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface FlightCabinMapper extends BaseMapper<FlightCabin> {

    /**
     * 乐观锁 + 条件扣减座位数
     * <p>三重保障：</p>
     * <ol>
     *   <li>available_seats >= #{count} — 库存足够才扣减</li>
     *   <li>AND version = #{oldVersion} — 乐观锁 CAS</li>
     *   <li>数据库层面原子操作，不会出现负数</li>
     * </ol>
     *
     * @param id         舱位ID
     * @param count      扣减数量
     * @param oldVersion 读取时的版本号
     * @return 影响行数（0 = 扣减失败，1 = 成功）
     */
    @Update("UPDATE flight_cabin SET available_seats = available_seats - #{count}, " +
            "version = version + 1, update_time = NOW() " +
            "WHERE id = #{id} AND available_seats >= #{count} AND version = #{oldVersion}")
    int decrementSeats(@Param("id") Long id,
                       @Param("count") int count,
                       @Param("oldVersion") int oldVersion);

    /**
     * 原子回退座位数（订单取消 / 支付超时释放库存）
     * <p>数据库层面自增，不做「先查再 set」的读改写：取消订单与支付回调可能并发，
     * 读改写会丢失更新，导致回退的座位数少于实际占用。</p>
     * <p>用 {@code LEAST(..., total_seats)} 兜住上限：即使出现重复回退等异常情况，
     * 可用座位也不会超过该舱位的总座位数，库存只会偏保守而不会虚增。</p>
     *
     * @param id    舱位ID
     * @param count 回退数量
     * @return 影响行数
     */
    @Update("UPDATE flight_cabin SET available_seats = LEAST(available_seats + #{count}, total_seats), " +
            "version = version + 1, update_time = NOW() " +
            "WHERE id = #{id} AND deleted = 0")
    int restoreSeats(@Param("id") Long id, @Param("count") int count);
}
