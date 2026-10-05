package com.itemll.flight_management_system_sp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.entity.BoardingPass;
import com.itemll.flight_management_system_sp.entity.CheckIn;
import com.itemll.flight_management_system_sp.entity.FlightCabin;
import com.itemll.flight_management_system_sp.entity.OrderPassenger;
import com.itemll.flight_management_system_sp.entity.Seat;
import com.itemll.flight_management_system_sp.mapper.BoardingPassMapper;
import com.itemll.flight_management_system_sp.mapper.CheckInMapper;
import com.itemll.flight_management_system_sp.mapper.FlightCabinMapper;
import com.itemll.flight_management_system_sp.mapper.OrderPassengerMapper;
import com.itemll.flight_management_system_sp.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 座位与库存回补服务
 *
 * <p><b>为什么独立成类：</b>「回补舱位库存」和「释放具体座位」原本散落在
 * {@code OrderExpiryService}（只管库存）、{@code CheckInServiceImpl}（只管座位）、
 * 以及退改审核里（两者都没有）三处。退票和改签都需要<b>同时</b>做这两件事，
 * 复制第三份必然会有一处漏掉，于是收敛到这里。</p>
 *
 * <p><b>两层数据结构的分工（改这里的代码前必须理解）：</b></p>
 * <ol>
 *   <li>{@code flight_cabin.available_seats} —— <b>库存</b>，下单时按乘机人数扣减、
 *       取消/退票时按人数回补。它只回答"这个舱位还能不能卖"。</li>
 *   <li>{@code order_passenger.seat_row/seat_column} + {@code seat.status/passenger_id} ——
 *       <b>具体座位占用</b>，值机选座时写入。其中 {@code order_passenger} 是<b>真源</b>，
 *       {@code seat} 表只是物化视图（见 MEMORY 中"座位与值机数据一致性"一节）。</li>
 * </ol>
 * 因此释放座位必须<b>两边一起改</b>：只改 {@code seat} 不改 {@code order_passenger}，
 * 下次座位图重建（{@code applyOccupiedFromPassengers}）会按真源把座位重新占回去，
 * 表现为"退了票座位却还占着"。
 *
 * <p><b>幂等性：</b>所有方法都用条件更新（{@code status='OCCUPIED'} 才改），
 * 重复调用不会把已经空出来的座位或已经回补过的库存再动一次。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatInventoryService {

    /** 座位状态：可预订 */
    private static final String SEAT_AVAILABLE = "AVAILABLE";
    /** 座位状态：已占用 */
    private static final String SEAT_OCCUPIED = "OCCUPIED";
    /** 旅客值机状态：未值机 */
    private static final String CHECKIN_NOT_CHECKED_IN = "NOT_CHECKED_IN";

    private final FlightCabinMapper flightCabinMapper;
    private final OrderPassengerMapper orderPassengerMapper;
    private final SeatMapper seatMapper;
    private final CheckInMapper checkInMapper;
    private final BoardingPassMapper boardingPassMapper;

    /**
     * 作废订单的值机与座位占用（退票审核通过 / 改签转出时调用）。
     *
     * <p>三件事必须一起做，缺任何一件都会留下互相矛盾的数据：</p>
     * <ol>
     *   <li>释放 {@code seat} 表占用并清空 {@code order_passenger} 的座位（真源）；</li>
     *   <li>把 {@code check_in} 记录置为 {@code CANCELLED} ——
     *       否则订单已经退票/改签，值机记录还显示"已值机"；</li>
     *   <li>删除对应的登机牌 —— 否则旅客手机里还留着一张写着旧航班旧座位的登机牌。</li>
     * </ol>
     *
     * @param orderId  订单主键
     * @param flightId 该订单<b>当前</b>所属航班；改签场景必须在改动订单航班之前调用
     */
    public void voidCheckInAndReleaseSeats(Long orderId, Long flightId) {
        releaseOccupiedSeats(orderId, flightId);

        List<CheckIn> checkIns = checkInMapper.selectList(
                new LambdaQueryWrapper<CheckIn>().eq(CheckIn::getOrderId, orderId));
        for (CheckIn checkIn : checkIns) {
            if ("CANCELLED".equals(checkIn.getStatus())) {
                continue;
            }
            checkIn.setStatus("CANCELLED");
            checkInMapper.updateById(checkIn);
            int removed = boardingPassMapper.delete(
                    new LambdaQueryWrapper<BoardingPass>().eq(BoardingPass::getCheckinId, checkIn.getId()));
            log.info("值机已作废并删除登机牌: orderId={}, checkinId={}, boardingPass={}",
                    orderId, checkIn.getCheckinId(), removed);
        }
    }

    /** 订单乘机人数，即下单时扣减的座位数 */
    public int countPassengers(Long orderId) {
        Long count = orderPassengerMapper.selectCount(
                new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, orderId));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 回补舱位库存（订单取消 / 退票审核通过）。
     * <p>按人数回退，用数据库原子自增而不是"先查后 set"，避免与支付回调并发时丢失更新。
     * 回退上限由 SQL 里的 {@code LEAST(..., total_seats)} 兜住，库存只会偏保守。</p>
     *
     * @return 实际影响行数；0 表示舱位不存在或已被删除
     */
    public int restoreCabinSeats(Long flightId, String cabinClass, int count) {
        if (count <= 0) {
            return 0;
        }
        FlightCabin cabin = findCabin(flightId, cabinClass);
        if (cabin == null) {
            log.warn("回补库存失败，舱位不存在: flightId={}, cabinClass={}", flightId, cabinClass);
            return 0;
        }
        int rows = flightCabinMapper.restoreSeats(cabin.getId(), count);
        log.info("舱位库存已回补: flightId={}, cabinClass={}, seats={}", flightId, cabinClass, count);
        return rows;
    }

    /**
     * 扣减舱位库存（改签转入新航班时用）。
     * <p>沿用与下单一致的乐观锁 + SQL 条件扣减，库存不足直接抛业务异常。</p>
     *
     * @throws BusinessException 新航班该舱位余票不足
     */
    public void deductCabinSeats(Long flightId, String cabinClass, int count) {
        if (count <= 0) {
            return;
        }
        FlightCabin cabin = findCabin(flightId, cabinClass);
        if (cabin == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "目标航班不存在该舱位：" + cabinClass);
        }
        if (cabin.getAvailableSeats() == null || cabin.getAvailableSeats() < count) {
            throw new BusinessException(ErrorCode.FLIGHT_FULL, "目标航班该舱位余票不足，请更换航班或舱位");
        }
        int rows = flightCabinMapper.decrementSeats(cabin.getId(), count, cabin.getVersion());
        if (rows == 0) {
            throw new BusinessException(ErrorCode.FLIGHT_FULL, "目标航班座位刚被抢占，请重新选择");
        }
        log.info("舱位库存已扣减: flightId={}, cabinClass={}, seats={}", flightId, cabinClass, count);
    }

    /**
     * 释放某订单旅客已经占用的具体座位（退票 / 改签转出）。
     *
     * <p>同时清理两层数据：{@code seat} 表（物化视图）与 {@code order_passenger}
     * （真源）。只有已值机的旅客才可能占座，未值机的会被跳过。</p>
     *
     * @param orderId  订单主键
     * @param flightId 该订单<b>当前</b>所属航班；改签场景必须在改动订单航班之前调用
     * @return 实际释放的座位数
     */
    public int releaseOccupiedSeats(Long orderId, Long flightId) {
        List<OrderPassenger> passengers = orderPassengerMapper.selectList(
                new LambdaQueryWrapper<OrderPassenger>().eq(OrderPassenger::getOrderId, orderId));
        if (passengers.isEmpty()) {
            return 0;
        }

        List<Long> passengerIds = passengers.stream()
                .map(OrderPassenger::getId)
                .filter(Objects::nonNull)
                .toList();

        // ① 按 passenger_id 释放：这是最可靠的匹配方式（seat.passenger_id 指向 order_passenger.id）
        int released = seatMapper.update(null, new LambdaUpdateWrapper<Seat>()
                .in(Seat::getPassengerId, passengerIds)
                .eq(Seat::getStatus, SEAT_OCCUPIED)
                .set(Seat::getStatus, SEAT_AVAILABLE)
                .set(Seat::getPassengerId, null));

        // ② 兜底：把该航班上按「行号+列号」能对上、但 passenger_id 已经断了关联的孤儿占用也清掉，
        //    否则这些座位会一直是 OCCUPIED，前台显示"没人选却已占用"。
        if (flightId != null) {
            for (OrderPassenger p : passengers) {
                if (p.getSeatRow() == null || p.getSeatColumn() == null) {
                    continue;
                }
                released += seatMapper.update(null, new LambdaUpdateWrapper<Seat>()
                        .eq(Seat::getFlightId, flightId)
                        .eq(Seat::getRowNum, p.getSeatRow())
                        .eq(Seat::getColCode, p.getSeatColumn())
                        .eq(Seat::getStatus, SEAT_OCCUPIED)
                        .set(Seat::getStatus, SEAT_AVAILABLE)
                        .set(Seat::getPassengerId, null));
            }
        }

        // ③ 清空 order_passenger 上的座位与值机状态（真源）
        int cleared = orderPassengerMapper.update(null, new LambdaUpdateWrapper<OrderPassenger>()
                .eq(OrderPassenger::getOrderId, orderId)
                .set(OrderPassenger::getSeatRow, null)
                .set(OrderPassenger::getSeatColumn, null)
                .set(OrderPassenger::getCheckinStatus, CHECKIN_NOT_CHECKED_IN));

        log.info("订单座位已释放: orderId={}, flightId={}, 释放座位数={}, 重置旅客数={}",
                orderId, flightId, released, cleared);
        return released;
    }

    private FlightCabin findCabin(Long flightId, String cabinClass) {
        if (flightId == null || cabinClass == null) {
            return null;
        }
        return flightCabinMapper.selectOne(new LambdaQueryWrapper<FlightCabin>()
                .eq(FlightCabin::getFlightId, flightId)
                .eq(FlightCabin::getCabinClass, cabinClass));
    }
}
