package com.itemll.flight_management_system_sp.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itemll.flight_management_system_sp.entity.*;
import com.itemll.flight_management_system_sp.mapper.*;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.config.PermissionBootstrap;
import com.itemll.flight_management_system_sp.service.AdminSystemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端系统服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSystemServiceImpl implements AdminSystemService {

    private final AdminUserMapper adminUserMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final SysConfigMapper configMapper;
    private final SysLogMapper logMapper;

    @Override
    public PageResult<Map<String, Object>> getUserList(int page, int pageSize) {
        Page<AdminUser> pageObj = new Page<>(page, pageSize);
        Page<AdminUser> result = adminUserMapper.selectPage(pageObj,
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getDeleted, 0));

        List<Map<String, Object>> list = result.getRecords().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(u.getId()));
            m.put("username", u.getUsername());
            m.put("name", u.getName());
            m.put("phone", u.getPhone());
            m.put("email", u.getEmail());
            m.put("status", u.getStatus());
            m.put("createTime", u.getCreateTime());
            List<Role> userRoles = rolesOfUser(u.getId());
            m.put("roles", userRoles.stream().map(Role::getCode).collect(Collectors.toList()));
            m.put("roleNames", userRoles.stream().map(Role::getName).collect(Collectors.toList()));
            return m;
        }).collect(Collectors.toList());

        return PageResult.of(list, page, pageSize, result.getTotal());
    }

    @Override
    @Transactional
    public void createUser(Map<String, Object> user) {
        AdminUser admin = new AdminUser();
        admin.setUsername((String) user.get("username"));
        admin.setName((String) user.get("name"));
        admin.setPhone((String) user.get("phone"));
        admin.setEmail((String) user.get("email"));
        admin.setPassword(BCrypt.hashpw((String) user.get("password")));
        admin.setStatus(1);
        adminUserMapper.insert(admin);
        // 落用户-角色关联
        saveUserRoles(admin.getId(), asStringList(user.get("roles")));
    }

    @Override
    @Transactional
    public void updateUser(Long userId, Map<String, Object> user) {
        AdminUser admin = adminUserMapper.selectById(userId);
        if (admin == null) return;
        if (user.containsKey("name")) admin.setName((String) user.get("name"));
        if (user.containsKey("phone")) admin.setPhone((String) user.get("phone"));
        if (user.containsKey("email")) admin.setEmail((String) user.get("email"));
        adminUserMapper.updateById(admin);
        if (user.containsKey("roles")) {
            // 重设用户-角色关联
            saveUserRoles(userId, asStringList(user.get("roles")));
        }
    }

    @Override
    public void disableUser(Long userId) {
        AdminUser admin = adminUserMapper.selectById(userId);
        if (admin != null) {
            admin.setStatus(0);
            adminUserMapper.updateById(admin);
        }
    }

    @Override
    public void enableUser(Long userId) {
        AdminUser admin = adminUserMapper.selectById(userId);
        if (admin != null) {
            admin.setStatus(1);
            adminUserMapper.updateById(admin);
        }
    }

    @Override
    public void resetPassword(Long userId) {
        AdminUser admin = adminUserMapper.selectById(userId);
        if (admin != null) {
            admin.setPassword(BCrypt.hashpw("Reset@123"));
            adminUserMapper.updateById(admin);
        }
    }

    @Override
    public List<Map<String, Object>> getRoleList() {
        List<Role> roles = roleMapper.selectList(
                new LambdaQueryWrapper<Role>().eq(Role::getDeleted, 0));
        return roles.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(r.getId()));
            m.put("roleId", String.valueOf(r.getId()));
            m.put("name", r.getName());
            m.put("code", r.getCode());
            m.put("description", r.getDescription());
            m.put("permissions", permissionCodesOfRole(r.getId()));
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void createRole(Map<String, Object> role) {
        String code = (String) role.get("code");
        if (findRoleByCode(code) != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色代码「" + code + "」已存在");
        }
        Role r = new Role();
        r.setName((String) role.get("name"));
        r.setCode(code);
        r.setDescription((String) role.get("description"));
        r.setStatus(1);
        roleMapper.insert(r);
        saveRolePermissions(r.getId(), asStringList(role.get("permissions")));
    }

    @Override
    @Transactional
    public void updateRole(Long roleId, Map<String, Object> role) {
        Role r = roleMapper.selectById(roleId);
        if (r == null) throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        if (role.containsKey("name")) r.setName((String) role.get("name"));
        if (role.containsKey("description")) r.setDescription((String) role.get("description"));
        roleMapper.updateById(r);
        if (role.containsKey("permissions")) {
            // 重设角色-权限关联
            saveRolePermissions(roleId, asStringList(role.get("permissions")));
        }
    }

    @Override
    @Transactional
    public void deleteRole(Long roleId) {
        Role r = roleMapper.selectById(roleId);
        if (r == null) throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        // 系统内置角色（SUPER_ADMIN 等）不可删除，防止管理员被锁死
        if (PermissionBootstrap.builtinRoleCodes().contains(r.getCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "「" + r.getCode() + "」为系统内置角色，不可删除");
        }
        // 先清理关联，避免脏数据
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, roleId));
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId));
        roleMapper.deleteById(roleId);
    }

    @Override
    public List<Map<String, Object>> getPermissionList() {
        List<Permission> permissions = permissionMapper.selectList(
                new LambdaQueryWrapper<Permission>()
                        .eq(Permission::getDeleted, 0)
                        .orderByAsc(Permission::getId));
        return permissions.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(p.getId()));
            m.put("name", p.getName());
            m.put("code", p.getCode());
            return m;
        }).collect(Collectors.toList());
    }

    // ==================== RBAC 关联辅助 ====================

    /** 入参 JSON 数组（角色码/权限码）安全转 List<String> */
    private List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (Object o : list) {
            if (o != null) result.add(o.toString());
        }
        return result;
    }

    /** 查询账号名下的完整角色列表 */
    private List<Role> rolesOfUser(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).collect(Collectors.toList());
        if (roleIds.isEmpty()) return Collections.emptyList();
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().in(Role::getId, roleIds));
    }

    /** 重设 账号-角色：先清空，再按角色码绑定（未知角色码直接报错，避免静默丢失） */
    private void saveUserRoles(Long userId, List<String> roleCodes) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        for (String code : roleCodes) {
            Role role = findRoleByCode(code);
            if (role == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "角色「" + code + "」不存在，请先在角色权限中创建");
            }
            UserRole ur = new UserRole();
            ur.setUserId(userId);
            ur.setRoleId(role.getId());
            userRoleMapper.insert(ur);
        }
    }

    /** 查询角色名下的权限码列表 */
    private List<String> permissionCodesOfRole(Long roleId) {
        List<Long> permIds = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId))
                .stream().map(RolePermission::getPermissionId).collect(Collectors.toList());
        if (permIds.isEmpty()) return Collections.emptyList();
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>().in(Permission::getId, permIds))
                .stream().map(Permission::getCode).collect(Collectors.toList());
    }

    /** 重设 角色-权限：先清空，再按权限码绑定 */
    private void saveRolePermissions(Long roleId, List<String> permCodes) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId));
        for (String code : permCodes) {
            Permission permission = findPermissionByCode(code);
            if (permission == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "权限码「" + code + "」不存在，请刷新权限列表");
            }
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(permission.getId());
            rolePermissionMapper.insert(rp);
        }
    }

    private Role findRoleByCode(String code) {
        if (code == null || code.isBlank()) return null;
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().eq(Role::getCode, code))
                .stream().findFirst().orElse(null);
    }

    private Permission findPermissionByCode(String code) {
        if (code == null || code.isBlank()) return null;
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>().eq(Permission::getCode, code))
                .stream().findFirst().orElse(null);
    }

    @Override
    public Map<String, Object> getProfile(Long userId) {
        AdminUser admin = adminUserMapper.selectById(userId);
        if (admin == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        Map<String, Object> m = new HashMap<>();
        m.put("userId", admin.getId());
        m.put("username", admin.getUsername());
        m.put("name", admin.getName());
        m.put("phone", admin.getPhone());
        m.put("email", admin.getEmail());
        m.put("avatar", "");
        return m;
    }

    @Override
    @Transactional
    public void updateProfile(Long userId, Map<String, Object> data) {
        AdminUser admin = adminUserMapper.selectById(userId);
        if (admin == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        if (data.containsKey("name")) admin.setName((String) data.get("name"));
        if (data.containsKey("phone")) admin.setPhone((String) data.get("phone"));
        if (data.containsKey("email")) admin.setEmail((String) data.get("email"));
        adminUserMapper.updateById(admin);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        AdminUser admin = adminUserMapper.selectById(userId);
        if (admin == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        if (!BCrypt.checkpw(oldPassword, admin.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "原密码错误");
        }
        admin.setPassword(BCrypt.hashpw(newPassword));
        adminUserMapper.updateById(admin);
    }

    @Override
    public Map<String, Object> getConfig() {
        List<SysConfig> configs = configMapper.selectList(null);
        Map<String, Object> result = new HashMap<>();
        for (SysConfig c : configs) {
            result.put(c.getConfigKey(), c.getConfigValue());
        }
        return result;
    }

    @Override
    public void updateConfig(Map<String, Object> config) {
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            SysConfig existing = configMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, entry.getKey()));
            if (existing != null) {
                existing.setConfigValue(entry.getValue().toString());
                configMapper.updateById(existing);
            } else {
                SysConfig sc = new SysConfig();
                sc.setConfigKey(entry.getKey());
                sc.setConfigValue(entry.getValue().toString());
                configMapper.insert(sc);
            }
        }
    }

    @Override
    public PageResult<Map<String, Object>> getLogs(String operator, String module, String action,
                                                     String startDate, String endDate, int page, int pageSize) {
        Page<SysLog> pageObj = new Page<>(page, pageSize);
        Page<SysLog> result = logMapper.selectPage(pageObj,
                new LambdaQueryWrapper<SysLog>()
                        .like(operator != null, SysLog::getOperatorName, operator)
                        .eq(module != null, SysLog::getModule, module)
                        .eq(action != null, SysLog::getAction, action)
                        .orderByDesc(SysLog::getCreateTime));

        List<Map<String, Object>> list = result.getRecords().stream().map(l -> {
            Map<String, Object> m = new HashMap<>();
            m.put("logId", l.getId().toString());
            m.put("operator", l.getOperatorName());
            m.put("module", l.getModule());
            m.put("action", l.getAction());
            m.put("target", l.getTarget());
            m.put("detail", l.getDetail());
            m.put("ip", l.getIp());
            m.put("createdAt", l.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        return PageResult.of(list, page, pageSize, result.getTotal());
    }
}
