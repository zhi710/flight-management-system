package com.itemll.flight_management_system_sp.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.dto.AdminLoginDTO;
import com.itemll.flight_management_system_sp.entity.AdminUser;
import com.itemll.flight_management_system_sp.entity.Permission;
import com.itemll.flight_management_system_sp.entity.Role;
import com.itemll.flight_management_system_sp.entity.RolePermission;
import com.itemll.flight_management_system_sp.entity.UserRole;
import com.itemll.flight_management_system_sp.mapper.AdminUserMapper;
import com.itemll.flight_management_system_sp.mapper.PermissionMapper;
import com.itemll.flight_management_system_sp.mapper.RoleMapper;
import com.itemll.flight_management_system_sp.mapper.RolePermissionMapper;
import com.itemll.flight_management_system_sp.mapper.UserRoleMapper;
import com.itemll.flight_management_system_sp.security.JwtUtil;
import com.itemll.flight_management_system_sp.security.RedisTokenService;
import com.itemll.flight_management_system_sp.service.AdminAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private final AdminUserMapper adminUserMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final JwtUtil jwtUtil;
    private final RedisTokenService redisTokenService;

    @Override
    public Map<String, Object> login(AdminLoginDTO dto) {
        AdminUser admin = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, dto.getUsername()));
        if (admin == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "管理员不存在");
        }
        if (!BCrypt.checkpw(dto.getPassword(), admin.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "密码错误");
        }
        if (admin.getStatus() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        // 查询角色
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, admin.getId()));
        List<String> roleCodes = userRoles.stream()
                .map(ur -> roleMapper.selectById(ur.getRoleId()))
                .filter(r -> r != null)
                .map(Role::getCode)
                .collect(Collectors.toList());

        // 生成 Token
        String token = jwtUtil.generateAdminToken(admin.getId(), admin.getUsername());
        try {
            redisTokenService.saveAdminToken(admin.getId(), token, 1800000);
        } catch (Exception e) {
            log.warn("Redis 不可用，Token 未持久化: {}", e.getMessage());
        }

        log.info("管理员登录成功: username={}", dto.getUsername());

        Map<String, Object> result = new HashMap<>();
        result.put("userId", "A" + admin.getId());
        result.put("username", admin.getUsername());
        result.put("name", admin.getName());
        result.put("roles", roleCodes);
        // 按 账号 → 角色 → 权限 真实汇总，而非写死
        result.put("permissions", listPermissionCodes(admin.getId()));
        result.put("token", token);
        result.put("expiresIn", 1800);
        return result;
    }

    @Override
    public void logout(Long adminId) {
        redisTokenService.removeAdminToken(adminId);
        log.info("管理员登出: adminId={}", adminId);
    }

    @Override
    public List<String> listPermissionCodes(Long adminId) {
        // 账号 → 角色
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, adminId));
        Set<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        if (roleIds.isEmpty()) return List.of();

        // 角色 → 权限ID
        List<RolePermission> rolePermissions = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().in(RolePermission::getRoleId, roleIds));
        Set<Long> permIds = rolePermissions.stream().map(RolePermission::getPermissionId).collect(Collectors.toSet());
        if (permIds.isEmpty()) return List.of();

        // 权限ID → 权限码（去重、保序）
        Set<String> codes = new LinkedHashSet<>();
        permissionMapper.selectList(new LambdaQueryWrapper<Permission>().in(Permission::getId, permIds))
                .forEach(p -> codes.add(p.getCode()));
        return new ArrayList<>(codes);
    }
}
