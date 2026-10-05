package com.itemll.flight_management_system_sp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.entity.*;
import com.itemll.flight_management_system_sp.mapper.*;
import com.itemll.flight_management_system_sp.service.NotificationService;
import com.itemll.flight_management_system_sp.service.SsrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SsrServiceImpl implements SsrService {

    private final SsrCodeMapper ssrCodeMapper;
    private final SpecialServiceRequestMapper ssrRequestMapper;
    private final OrderMapper orderMapper;
    private final OrderPassengerMapper orderPassengerMapper;
    private final FlightMapper flightMapper;
    private final NotificationService notificationService;

    @Override
    public List<Map<String, Object>> getAvailableSsrCodes(String category) {
        LambdaQueryWrapper<SsrCode> wrapper = new LambdaQueryWrapper<SsrCode>()
                .eq(SsrCode::getStatus, 1)
                .orderByAsc(SsrCode::getSortOrder);
        if (StrUtil.isNotBlank(category)) {
            wrapper.eq(SsrCode::getCategory, category);
        }

        return ssrCodeMapper.selectList(wrapper).stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", s.getCode());
            m.put("category", s.getCategory());
            m.put("nameCn", s.getNameCn());
            m.put("nameEn", s.getNameEn());
            m.put("description", s.getDescription());
            m.put("extraFee", s.getExtraFee());
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public PageResult<Map<String, Object>> getSsrList(Long flightId, String status, int page, int pageSize) {
        LambdaQueryWrapper<SpecialServiceRequest> wrapper = new LambdaQueryWrapper<SpecialServiceRequest>()
                .orderByDesc(SpecialServiceRequest::getCreateTime);

        if (flightId != null) {
            List<Order> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getFlightId, flightId)
                            .eq(Order::getDeleted, 0));
            if (orders.isEmpty()) {
                return PageResult.of(List.of(), page, pageSize, 0);
            }
            List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
            wrapper.in(SpecialServiceRequest::getOrderId, orderIds);
        }
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(SpecialServiceRequest::getStatus, status);
        }

        Page<SpecialServiceRequest> pageObj = new Page<>(page, pageSize);
        Page<SpecialServiceRequest> result = ssrRequestMapper.selectPage(pageObj, wrapper);

        List<Map<String, Object>> list = result.getRecords().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("requestId", s.getId().toString());
            m.put("orderId", s.getOrderId().toString());
            m.put("passengerName", s.getPassengerName());
            m.put("ssrCode", s.getSsrCode());

            SsrCode code = ssrCodeMapper.selectOne(
                    new LambdaQueryWrapper<SsrCode>().eq(SsrCode::getCode, s.getSsrCode()));
            m.put("ssrName", code != null ? code.getNameCn() : s.getSsrCode());

            m.put("status", s.getStatus());
            m.put("remark", s.getRemark());
            m.put("createdAt", s.getCreateTime());

            Order order = orderMapper.selectById(s.getOrderId());
            if (order != null) {
                Flight flight = flightMapper.selectById(order.getFlightId());
                if (flight != null) {
                    m.put("flightNo", flight.getFlightNo());
                }
            }
            return m;
        }).collect(Collectors.toList());

        return PageResult.of(list, page, pageSize, result.getTotal());
    }

    @Override
    public void submitSsrRequest(Long orderId, Long orderPassengerId, String ssrCode, String remark) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("订单不存在");

        OrderPassenger passenger = orderPassengerMapper.selectById(orderPassengerId);
        if (passenger == null) throw new BusinessException("旅客信息不存在");

        SpecialServiceRequest request = new SpecialServiceRequest();
        request.setOrderId(orderId);
        request.setOrderPassengerId(orderPassengerId);
        request.setPassengerName(passenger.getPassengerName());
        request.setSsrCode(ssrCode);
        request.setStatus("PENDING");
        request.setRemark(remark);
        ssrRequestMapper.insert(request);

        // 更新订单 SSR 标记
        order.setSsrFlag(1);
        orderMapper.updateById(order);
    }

    @Override
    public void processSsrRequest(Long requestId, String action, Long adminId) {
        SpecialServiceRequest request = ssrRequestMapper.selectById(requestId);
        if (request == null) throw new BusinessException("SSR请求不存在");

        boolean isApproved = "APPROVED".equals(action);
        request.setStatus(isApproved ? "APPROVED" : "REJECTED");
        request.setOperatorId(adminId);
        ssrRequestMapper.updateById(request);

        Order order = orderMapper.selectById(request.getOrderId());
        SsrCode code = ssrCodeMapper.selectOne(
                new LambdaQueryWrapper<SsrCode>().eq(SsrCode::getCode, request.getSsrCode()));

        // 如果通过且是付费服务，追加费用到订单
        BigDecimal extraFee = (code != null) ? code.getExtraFee() : BigDecimal.ZERO;
        if (isApproved && extraFee.compareTo(BigDecimal.ZERO) > 0 && order != null) {
            // 追加到订单，但不改 order 的总状态
            order.setServiceFee(order.getServiceFee().add(extraFee));
            order.setTotalAmount(order.getTotalAmount().add(extraFee));
            orderMapper.updateById(order);
            log.info("SSR审核通过，费用¥{}已追加到订单 {}: {}", extraFee, order.getOrderId(), code.getNameCn());
        }

        // 推送通知给旅客
        if (order != null && order.getUserId() != null) {
            String title = isApproved ? "特殊服务申请已通过" : "特殊服务申请被拒绝";
            String content = isApproved
                    ? "您申请的「" + (code != null ? code.getNameCn() : request.getSsrCode()) + "」已通过审核"
                      + (extraFee.compareTo(BigDecimal.ZERO) > 0 ? "，费用¥" + extraFee + "已追加到订单" : "")
                    : "您申请的「" + (code != null ? code.getNameCn() : request.getSsrCode()) + "」未通过审核，费用将不会收取";

            notificationService.pushToPassenger(order.getUserId(), "SSR_UPDATE", Map.of(
                    "title", title,
                    "content", content,
                    "ssrCode", request.getSsrCode(),
                    "ssrName", code != null ? code.getNameCn() : request.getSsrCode(),
                    "orderId", order.getOrderId(),
                    "status", request.getStatus(),
                    "extraFee", extraFee,
                    "passengerName", request.getPassengerName()
            ));
        }
    }

    @Override
    public List<Map<String, Object>> getSsrByOrder(Long orderId) {
        return ssrRequestMapper.selectList(
                new LambdaQueryWrapper<SpecialServiceRequest>()
                        .eq(SpecialServiceRequest::getOrderId, orderId)
                        .orderByDesc(SpecialServiceRequest::getCreateTime))
                .stream().map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("requestId", s.getId().toString());
                    m.put("ssrCode", s.getSsrCode());
                    SsrCode code = ssrCodeMapper.selectOne(
                            new LambdaQueryWrapper<SsrCode>().eq(SsrCode::getCode, s.getSsrCode()));
                    m.put("ssrName", code != null ? code.getNameCn() : s.getSsrCode());
                    m.put("extraFee", code != null ? code.getExtraFee() : BigDecimal.ZERO);
                    m.put("passengerName", s.getPassengerName());
                    m.put("status", s.getStatus());
                    m.put("remark", s.getRemark());
                    return m;
                }).collect(Collectors.toList());
    }
}
