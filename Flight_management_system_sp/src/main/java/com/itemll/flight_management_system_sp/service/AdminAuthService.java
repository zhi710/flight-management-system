package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.dto.AdminLoginDTO;
import java.util.List;
import java.util.Map;

/**
 * 管理员认证服务接口
 */
public interface AdminAuthService {
    Map<String, Object> login(AdminLoginDTO dto);
    void logout(Long adminId);

    /**
     * 汇总某管理员账号拥有的全部权限码（账号 → 角色 → 权限），供登录与接口门禁复用。
     */
    List<String> listPermissionCodes(Long adminId);
}
