package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.common.result.PageResult;

import java.util.Map;

/**
 * 管理端前台用户（旅客会员）管理服务
 */
public interface AdminMemberService {

    PageResult<Map<String, Object>> getMemberList(String phone, String name, String memberLevel,
                                                  Integer status, int page, int pageSize);

    void updateMember(Long id, Map<String, Object> data);

    void disableMember(Long id);

    void enableMember(Long id);

    void resetMemberPassword(Long id);
}
