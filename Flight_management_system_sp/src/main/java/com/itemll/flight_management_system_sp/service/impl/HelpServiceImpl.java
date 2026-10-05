package com.itemll.flight_management_system_sp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.dto.FeedbackDTO;
import com.itemll.flight_management_system_sp.entity.Feedback;
import com.itemll.flight_management_system_sp.mapper.FeedbackMapper;
import com.itemll.flight_management_system_sp.service.HelpService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 帮助中心服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HelpServiceImpl implements HelpService {

    private final FeedbackMapper feedbackMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<Map<String, Object>> getFaq(String category, String keyword) {
        // 返回预设 FAQ（生产环境应从数据库或 CMS 获取）
        List<Map<String, Object>> faqs = new ArrayList<>();
        faqs.add(Map.of("id", 1, "question", "如何预订机票？", "answer", "在首页搜索航班后选择合适的航班和舱位，填写旅客信息后提交订单并支付即可。", "category", "BOOKING"));
        faqs.add(Map.of("id", 2, "question", "如何办理值机？", "answer", "在航班起飞前24小时内，进入「我的订单」点击值机按钮，选择座位后完成值机。", "category", "CHECKIN"));
        faqs.add(Map.of("id", 3, "question", "如何申请退票？", "answer", "在订单详情页点击退票按钮，填写退票原因后提交申请。退票手续费根据舱位和时间不同而异。", "category", "REFUND"));
        faqs.add(Map.of("id", 4, "question", "免费行李额度是多少？", "answer", "经济舱免费托运行李20公斤，公务舱30公斤，头等舱40公斤。", "category", "BAGGAGE"));

        if (category != null) {
            faqs.removeIf(f -> !category.equals(f.get("category")));
        }
        if (keyword != null) {
            faqs.removeIf(f -> !f.get("question").toString().contains(keyword) && !f.get("answer").toString().contains(keyword));
        }
        return faqs;
    }

    @Override
    public Map<String, Object> getRefundPolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("title", "退改签政策");
        policy.put("rules", List.of(
                Map.of("cabin", "ECONOMY", "refund", "起飞前2小时手续费10%，之后不可退", "change", "起飞前2小时手续费5%"),
                Map.of("cabin", "BUSINESS", "refund", "起飞前2小时手续费5%，之后10%", "change", "起飞前免费，之后5%"),
                Map.of("cabin", "FIRST", "refund", "起飞前免费，之后5%", "change", "免费改签")
        ));
        return policy;
    }

    @Override
    public Map<String, Object> getBaggageRules() {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("title", "行李规定");
        rules.put("carryOn", "每人可携带1件手提行李，不超过7公斤");
        rules.put("checked", List.of(
                Map.of("cabin", "ECONOMY", "free", "20kg", "excessFee", "每公斤按经济舱全价票的1.5%"),
                Map.of("cabin", "BUSINESS", "free", "30kg", "excessFee", "每公斤按公务舱全价票的1.5%"),
                Map.of("cabin", "FIRST", "free", "40kg", "excessFee", "每公斤按头等舱全价票的1.5%")
        ));
        return rules;
    }

    @Override
    public void submitFeedback(Long userId, FeedbackDTO dto) {
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setType(dto.getType());
        feedback.setContent(dto.getContent());
        feedback.setContactPhone(dto.getContactPhone());
        if (dto.getOrderId() != null) {
            feedback.setOrderId(Long.parseLong(dto.getOrderId()));
        }
        if (dto.getAttachments() != null) {
            try {
                feedback.setAttachments(objectMapper.writeValueAsString(dto.getAttachments()));
            } catch (Exception e) {
                log.warn("序列化附件失败", e);
            }
        }
        feedback.setStatus("PENDING");
        feedbackMapper.insert(feedback);
        log.info("投诉建议已提交: userId={}, type={}", userId, dto.getType());
    }

    @Override
    public List<Map<String, Object>> listFeedback(Long userId) {
        List<Feedback> list = feedbackMapper.selectList(
                new LambdaQueryWrapper<Feedback>()
                        .eq(Feedback::getUserId, userId)
                        .orderByDesc(Feedback::getCreateTime));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Feedback f : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId().toString());
            m.put("type", f.getType());
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
}
