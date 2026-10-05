package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.common.result.PageResult;
import java.util.List;
import java.util.Map;

/**
 * 管理端系统服务接口
 */
public interface AdminSystemService {
    PageResult<Map<String, Object>> getUserList(int page, int pageSize);
    void createUser(Map<String, Object> user);
    void updateUser(Long userId, Map<String, Object> user);
    void disableUser(Long userId);
    void enableUser(Long userId);
    void resetPassword(Long userId);
    List<Map<String, Object>> getRoleList();
    void createRole(Map<String, Object> role);
    void updateRole(Long roleId, Map<String, Object> role);
    void deleteRole(Long roleId);
    List<Map<String, Object>> getPermissionList();
    Map<String, Object> getProfile(Long userId);
    void updateProfile(Long userId, Map<String, Object> data);
    void changePassword(Long userId, String oldPassword, String newPassword);
    Map<String, Object> getConfig();
    void updateConfig(Map<String, Object> config);
    PageResult<Map<String, Object>> getLogs(String operator, String module, String action, String startDate, String endDate, int page, int pageSize);
}
