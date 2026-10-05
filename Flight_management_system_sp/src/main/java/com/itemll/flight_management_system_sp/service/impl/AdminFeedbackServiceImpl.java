package com.itemll.flight_management_system_sp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.entity.Feedback;
import com.itemll.flight_management_system_sp.entity.User;
import com.itemll.flight_management_system_sp.mapper.FeedbackMapper;
import com.itemll.flight_management_system_sp.mapper.UserMapper;
import com.itemll.flight_management_system_sp.service.AdminFeedbackService;
import com.itemll.flight_management_system_sp.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端投诉建议服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFeedbackServiceImpl implements AdminFeedbackService {

    private final FeedbackMapper feedbackMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    @Override
    public List<Map<String, Object>> listFeedback(String status) {
        List<Feedback> list = feedbackMapper.selectList(
                new LambdaQueryWrapper<Feedback>()
                        .eq(status != null && !status.isEmpty(), Feedback::getStatus, status)
                        .orderByDesc(Feedback::getCreateTime));

        if (list.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> userIds = list.stream()
                .map(Feedback::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Feedback f : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId().toString());
            m.put("type", f.getType());
            m.put("userId", f.getUserId() != null ? f.getUserId().toString() : null);
            User u = f.getUserId() != null ? userMap.get(f.getUserId()) : null;
            m.put("userPhone", u != null ? u.getPhone() : null);
            m.put("userName", u != null ? u.getName() : null);
            m.put("orderId", f.getOrderId() != null ? f.getOrderId().toString() : null);
            m.put("content", f.getContent());
            m.put("contactPhone", f.getContactPhone());
            m.put("status", f.getStatus());
            m.put("reply", f.getReply());
            m.put("createTime", f.getCreateTime() != null ? f.getCreateTime().toString() : null);
            result.add(m);
        }
        return result;
    }

    @Override
    public void replyFeedback(Long feedbackId, String reply) {
        if (reply == null || reply.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "回复内容不能为空");
        }
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "反馈不存在");
        }

        feedback.setReply(reply);
        feedback.setStatus("REPLIED");
        feedbackMapper.updateById(feedback);

        if (feedback.getUserId() != null) {
            notificationService.notifyPassenger(
                    feedback.getUserId(),
                    "反馈已回复",
                    "您的反馈已收到回复：" + reply,
                    "feedback",
                    feedback.getId());
        }
        log.info("反馈已回复: feedbackId={}, userId={}", feedbackId, feedback.getUserId());
    }
}
