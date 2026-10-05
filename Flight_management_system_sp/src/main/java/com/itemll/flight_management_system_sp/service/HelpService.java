package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.dto.FeedbackDTO;
import java.util.List;
import java.util.Map;

/**
 * 帮助中心服务接口
 */
public interface HelpService {

    /** 获取 FAQ 列表 */
    List<Map<String, Object>> getFaq(String category, String keyword);

    /** 获取退改政策 */
    Map<String, Object> getRefundPolicy();

    /** 获取行李规定 */
    Map<String, Object> getBaggageRules();

    /** 提交投诉建议 */
    void submitFeedback(Long userId, FeedbackDTO dto);

    /** 查询我的反馈列表 */
    List<Map<String, Object>> listFeedback(Long userId);
}
