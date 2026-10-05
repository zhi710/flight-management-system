package com.itemll.flight_management_system_sp.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.entity.Permission;
import com.itemll.flight_management_system_sp.entity.Role;
import com.itemll.flight_management_system_sp.entity.RolePermission;
import com.itemll.flight_management_system_sp.mapper.PermissionMapper;
import com.itemll.flight_management_system_sp.mapper.RoleMapper;
import com.itemll.flight_management_system_sp.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限字典自举。
 * <p>以代码内维护的「17 个权限码 + 4 个内置角色默认权限」为唯一事实源：</p>
 * <ul>
 *   <li>首次/需迁移：清理旧 seed 遗留的非标准权限码，补齐标准权限码，重建 4 个内置角色的权限集。</li>
 *   <li>之后每次启动：幂等补齐缺失的标准权限码，并确保 SUPER_ADMIN 始终拥有全部权限（防管理员被锁死）。</li>
 * </ul>
 * <p>在角色权限页面新建的角色不在内置名单，完全由用户维护，重启不会被覆盖。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionBootstrap implements CommandLineRunner {

    /** 标准权限码 → 名称（模块级，1:1 对应后台侧边栏菜单） */
    private static final Map<String, String> PERMISSIONS = new LinkedHashMap<>();
    /** 内置角色 code → name（缺失时才插入，不覆盖用户改名） */
    private static final Map<String, String> BUILTIN_ROLES = new LinkedHashMap<>();

    /** 内置角色 code 只读视图，供业务侧判断系统角色（如禁止删除，防止管理员被锁死） */
    public static Set<String> builtinRoleCodes() {
        return java.util.Collections.unmodifiableSet(BUILTIN_ROLES.keySet());
    }
    /** 内置角色 code → 默认权限码集 */
    private static final Map<String, Set<String>> BUILTIN_ROLE_PERMS = new LinkedHashMap<>();

    static {
        PERMISSIONS.put("dashboard:view", "监控大屏");
        PERMISSIONS.put("flight:read", "航班查看");
        PERMISSIONS.put("flight:write", "航班管理");
        PERMISSIONS.put("passenger:read", "旅客查看");
        PERMISSIONS.put("passenger:write", "旅客业务办理");
        PERMISSIONS.put("crew:read", "机组查看");
        PERMISSIONS.put("crew:write", "机组排班");
        PERMISSIONS.put("ticket:read", "客票查看");
        PERMISSIONS.put("ticket:write", "客票/票价管理");
        PERMISSIONS.put("monitor:read", "监控查看");
        PERMISSIONS.put("monitor:write", "运营处置");
        PERMISSIONS.put("report:read", "报表查看");
        PERMISSIONS.put("report:export", "报表导出");
        PERMISSIONS.put("system:user", "用户管理");
        PERMISSIONS.put("system:role", "角色权限");
        PERMISSIONS.put("system:config", "系统配置");
        PERMISSIONS.put("system:data", "基础数据");
        PERMISSIONS.put("feedback:read", "反馈查看");
        PERMISSIONS.put("feedback:write", "反馈回复");

        BUILTIN_ROLES.put("SUPER_ADMIN", "超级管理员");
        BUILTIN_ROLES.put("DISPATCHER", "航班调度员");
        BUILTIN_ROLES.put("CHECKIN_STAFF", "值机员");
        BUILTIN_ROLES.put("CUSTOMER_SERVICE", "客服");

        BUILTIN_ROLE_PERMS.put("SUPER_ADMIN", PERMISSIONS.keySet());
        BUILTIN_ROLE_PERMS.put("DISPATCHER", Set.of(
                "dashboard:view", "flight:read", "flight:write",
                "crew:read", "crew:write", "monitor:read", "monitor:write"));
        BUILTIN_ROLE_PERMS.put("CHECKIN_STAFF", Set.of(
                "dashboard:view", "passenger:read", "passenger:write"));
        BUILTIN_ROLE_PERMS.put("CUSTOMER_SERVICE", Set.of(
                "dashboard:view", "passenger:read", "ticket:read",
                "feedback:read", "feedback:write"));
    }

    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    @Override
    public void run(String... args) {
        try {
            sync();
        } catch (Exception e) {
            log.error("权限字典自举失败（不影响启动，权限以数据库现状为准）: {}", e.getMessage(), e);
        }
    }

    private void sync() {
        // 补齐缺失的标准权限码
        Set<String> existingCodes = permissionMapper.selectList(null).stream()
                .map(Permission::getCode).collect(Collectors.toSet());
        for (Map.Entry<String, String> e : PERMISSIONS.entrySet()) {
            if (!existingCodes.contains(e.getKey())) {
                Permission p = new Permission();
                p.setName(e.getValue());
                p.setCode(e.getKey());
                permissionMapper.insert(p);
            }
        }

        // 是否存在「旧 seed 遗留的非标准权限」或「内置角色缺失」→ 需要一次性迁移重建
        List<Permission> allPerms = permissionMapper.selectList(null);
        Map<String, Permission> byCode = allPerms.stream()
                .collect(Collectors.toMap(Permission::getCode, p -> p, (a, b) -> a, LinkedHashMap::new));
        List<Permission> stale = allPerms.stream()
                .filter(p -> !PERMISSIONS.containsKey(p.getCode()))
                .collect(Collectors.toList());
        boolean migrationNeeded = !stale.isEmpty() || BUILTIN_ROLES.keySet().stream()
                .anyMatch(code -> findRoleByCode(code) == null);

        if (migrationNeeded) {
            log.info("RBAC 权限字典迁移：清理旧权限码 {} 个、重建内置角色默认权限", stale.size());
            if (!stale.isEmpty()) {
                List<Long> staleIds = stale.stream().map(Permission::getId).collect(Collectors.toList());
                rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                        .in(RolePermission::getPermissionId, staleIds));
                permissionMapper.delete(new LambdaQueryWrapper<Permission>()
                        .in(Permission::getId, staleIds));
            }
            // 重建内置角色（缺失才插入）
            for (Map.Entry<String, String> roleEntry : BUILTIN_ROLES.entrySet()) {
                Role role = findRoleByCode(roleEntry.getKey());
                if (role == null) {
                    role = new Role();
                    role.setName(roleEntry.getValue());
                    role.setCode(roleEntry.getKey());
                    role.setDescription("系统内置角色");
                    role.setStatus(1);
                    roleMapper.insert(role);
                }
                rebuildRolePermissions(role.getId(), BUILTIN_ROLE_PERMS.get(roleEntry.getKey()), byCode);
            }
            log.info("RBAC 权限字典迁移完成");
        } else {
            // 稳定态：确保 SUPER_ADMIN 始终拥有全部标准权限（只增不删，防止管理员被锁死）
            Role superRole = findRoleByCode("SUPER_ADMIN");
            if (superRole != null) {
                Set<String> owned = permissionCodesOfRole(superRole.getId());
                for (String code : PERMISSIONS.keySet()) {
                    if (!owned.contains(code)) {
                        Permission p = byCode.get(code);
                        if (p != null) {
                            RolePermission rp = new RolePermission();
                            rp.setRoleId(superRole.getId());
                            rp.setPermissionId(p.getId());
                            rolePermissionMapper.insert(rp);
                        }
                    }
                }
            }
        }
    }

    /** 清空角色现有权限并按其默认集重建 */
    private void rebuildRolePermissions(Long roleId, Set<String> codes, Map<String, Permission> byCode) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));
        for (String code : codes) {
            Permission p = byCode.get(code);
            if (p == null) {
                log.warn("内置角色缺失权限码 {}，跳过（理论上不应发生）", code);
                continue;
            }
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(p.getId());
            rolePermissionMapper.insert(rp);
        }
    }

    private Role findRoleByCode(String code) {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().eq(Role::getCode, code))
                .stream().findFirst().orElse(null);
    }

    private Set<String> permissionCodesOfRole(Long roleId) {
        Set<String> result = new LinkedHashSet<>();
        rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, roleId))
                .forEach(rp -> {
                    Permission p = permissionMapper.selectById(rp.getPermissionId());
                    if (p != null) result.add(p.getCode());
                });
        return result;
    }
}
