package com.itemll.flight_management_system_sp.service;

import java.util.List;
import java.util.Map;

public interface CarouselService {
    /** 获取启用状态的轮播图列表（按排序字段升序） */
    List<Map<String, Object>> getActiveCarousels();
}
