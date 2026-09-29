package com.hengshucredit.rule.server.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.ConsolePermission;
import com.hengshucredit.rule.model.entity.ConsoleRole;
import com.hengshucredit.rule.model.entity.ConsoleRolePermission;
import com.hengshucredit.rule.model.entity.ConsoleUserPermissionOverride;
import com.hengshucredit.rule.model.entity.ConsoleUserRole;
import com.hengshucredit.rule.server.mapper.ConsolePermissionMapper;
import com.hengshucredit.rule.server.mapper.ConsoleRoleMapper;
import com.hengshucredit.rule.server.mapper.ConsoleRolePermissionMapper;
import com.hengshucredit.rule.server.mapper.ConsoleUserPermissionOverrideMapper;
import com.hengshucredit.rule.server.mapper.ConsoleUserRoleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ConsolePermissionService {

    @Resource
    private ConsoleUserRoleMapper userRoleMapper;
    @Resource
    private ConsoleRoleMapper roleMapper;
    @Resource
    private ConsoleRolePermissionMapper rolePermissionMapper;
    @Resource
    private ConsolePermissionMapper permissionMapper;
    @Resource
    private ConsoleUserPermissionOverrideMapper overrideMapper;

    public Set<String> effectivePermissions(Long userId) {
        if (userId == null) return Collections.emptySet();
        Set<String> rolePermissions = rolePermissionCodes(userId);
        List<PermissionOverride> overrides = userOverrides(userId);
        return merge(rolePermissions, overrides);
    }

    public boolean hasPermission(Long userId, String permissionCode) {
        return permissionCode != null
                && effectivePermissions(userId).contains(permissionCode);
    }

    public Set<String> merge(Set<String> rolePermissions, Map<String, String> overrides) {
        List<PermissionOverride> items = new ArrayList<>();
        if (overrides != null) {
            for (Map.Entry<String, String> entry : overrides.entrySet()) {
                items.add(new PermissionOverride(entry.getKey(), entry.getValue()));
            }
        }
        return merge(rolePermissions, items);
    }

    public Set<String> merge(Set<String> rolePermissions,
                             List<PermissionOverride> overrides) {
        LinkedHashSet<String> effective = new LinkedHashSet<>();
        if (rolePermissions != null) {
            effective.addAll(rolePermissions);
        }
        LinkedHashSet<String> denied = new LinkedHashSet<>();
        if (overrides != null) {
            for (PermissionOverride override : overrides) {
                if (override == null || override.permissionCode() == null
                        || override.effect() == null) {
                    continue;
                }
                String effect = override.effect().toUpperCase(Locale.ROOT);
                if ("DENY".equals(effect)) {
                    denied.add(override.permissionCode());
                    effective.remove(override.permissionCode());
                } else if ("ALLOW".equals(effect)
                        && !denied.contains(override.permissionCode())) {
                    effective.add(override.permissionCode());
                }
            }
        }
        effective.removeAll(denied);
        return Collections.unmodifiableSet(effective);
    }

    public List<String> roleCodes(Long userId) {
        List<ConsoleRole> roles = enabledRoles(userId);
        List<String> codes = new ArrayList<>(roles.size());
        for (ConsoleRole role : roles) {
            codes.add(role.getRoleCode());
        }
        return Collections.unmodifiableList(codes);
    }

    private Set<String> rolePermissionCodes(Long userId) {
        List<ConsoleRole> roles = enabledRoles(userId);
        if (roles.isEmpty()) return Collections.emptySet();
        List<Long> roleIds = roles.stream().map(ConsoleRole::getId).toList();
        List<ConsoleRolePermission> grants = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<ConsoleRolePermission>()
                        .in(ConsoleRolePermission::getRoleId, roleIds));
        if (grants.isEmpty()) return Collections.emptySet();
        List<Long> permissionIds = grants.stream()
                .map(ConsoleRolePermission::getPermissionId)
                .distinct()
                .toList();
        List<ConsolePermission> permissions = permissionMapper.selectByIds(permissionIds);
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (ConsolePermission permission : permissions) {
            if (Integer.valueOf(1).equals(permission.getStatus())) {
                codes.add(permission.getPermissionCode());
            }
        }
        return codes;
    }

    private List<ConsoleRole> enabledRoles(Long userId) {
        List<ConsoleUserRole> memberships = userRoleMapper.selectList(
                new LambdaQueryWrapper<ConsoleUserRole>()
                        .eq(ConsoleUserRole::getUserId, userId));
        if (memberships.isEmpty()) return Collections.emptyList();
        List<Long> roleIds = memberships.stream()
                .map(ConsoleUserRole::getRoleId)
                .distinct()
                .toList();
        List<ConsoleRole> roles = roleMapper.selectByIds(roleIds);
        return roles.stream()
                .filter(role -> Integer.valueOf(1).equals(role.getStatus()))
                .toList();
    }

    private List<PermissionOverride> userOverrides(Long userId) {
        List<ConsoleUserPermissionOverride> rows = overrideMapper.selectList(
                new LambdaQueryWrapper<ConsoleUserPermissionOverride>()
                        .eq(ConsoleUserPermissionOverride::getUserId, userId));
        if (rows.isEmpty()) return Collections.emptyList();
        List<Long> permissionIds = rows.stream()
                .map(ConsoleUserPermissionOverride::getPermissionId)
                .distinct()
                .toList();
        Map<Long, String> codes = new HashMap<>();
        for (ConsolePermission permission : permissionMapper.selectByIds(permissionIds)) {
            if (Integer.valueOf(1).equals(permission.getStatus())) {
                codes.put(permission.getId(), permission.getPermissionCode());
            }
        }
        List<PermissionOverride> result = new ArrayList<>();
        for (ConsoleUserPermissionOverride row : rows) {
            String code = codes.get(row.getPermissionId());
            if (code != null) {
                result.add(new PermissionOverride(code, row.getEffect()));
            }
        }
        return result;
    }

    public record PermissionOverride(String permissionCode, String effect) {
    }
}
