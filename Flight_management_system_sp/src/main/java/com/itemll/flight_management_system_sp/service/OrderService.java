package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.dto.OrderChangeDTO;
import com.itemll.flight_management_system_sp.dto.OrderCreateDTO;
import com.itemll.flight_management_system_sp.dto.OrderRefundDTO;
import java.util.List;
import java.util.Map;

/**
 * 订单服务接口
 */
public interface OrderService {

    /**
     * 创建订单（含并发控制：Redis 分布式锁 + 乐观锁 + SQL 条件扣减）
     *
     * @param dto    订单创建参数
     * @param userId 当前登录用户ID
     * @return 订单详情
     */
    Map<String, Object> createOrder(OrderCreateDTO dto, Long userId);

    /** 查询订单详情 */
    Map<String, Object> getOrderDetail(String orderId, Long userId);

    /** 订单列表 */
    PageResult<Map<String, Object>> getOrderList(Long userId, String status, String startDate, String endDate, String keyword, int page, int pageSize);

    /** 取消订单 */
    void cancelOrder(String orderId, Long userId, String reason);

    /** 申请改签 */
    Map<String, Object> changeOrder(String orderId, Long userId, OrderChangeDTO dto);

    /** 申请退票 */
    Map<String, Object> refundOrder(String orderId, Long userId, OrderRefundDTO dto);

    /** 查询改签状态 */
    Map<String, Object> getChangeStatus(String orderId, String changeId, Long userId);

    /** 查询退票状态 */
    Map<String, Object> getRefundStatus(String orderId, String refundId, Long userId);

    /** 我的改签记录列表 */
    List<Map<String, Object>> listChanges(Long userId);

    /** 我的退票记录列表 */
    List<Map<String, Object>> listRefunds(Long userId);
}
