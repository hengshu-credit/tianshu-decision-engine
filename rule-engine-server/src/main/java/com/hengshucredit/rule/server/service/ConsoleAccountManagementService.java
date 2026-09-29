package com.hengshucredit.rule.server.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.ConsoleRoleSaveRequest;
import com.hengshucredit.rule.model.dto.ConsoleUserPermissionRequest;
import com.hengshucredit.rule.model.dto.ConsoleUserSaveRequest;
import com.hengshucredit.rule.model.entity.ConsolePermission;
import com.hengshucredit.rule.model.entity.ConsoleRole;
import com.hengshucredit.rule.model.entity.ConsoleRolePermission;
import com.hengshucredit.rule.model.entity.ConsoleSecurityAuditLog;
import com.hengshucredit.rule.model.entity.ConsoleUser;
import com.hengshucredit.rule.model.entity.ConsoleUserPermissionOverride;
import com.hengshucredit.rule.model.entity.ConsoleUserRole;
import com.hengshucredit.rule.server.mapper.ConsolePermissionMapper;
import com.hengshucredit.rule.server.mapper.ConsoleRoleMapper;
import com.hengshucredit.rule.server.mapper.ConsoleRolePermissionMapper;
import com.hengshucredit.rule.server.mapper.ConsoleSecurityAuditLogMapper;
import com.hengshucredit.rule.server.mapper.ConsoleUserMapper;
import com.hengshucredit.rule.server.mapper.ConsoleUserPermissionOverrideMapper;
import com.hengshucredit.rule.server.mapper.ConsoleUserRoleMapper;
import com.hengshucredit.rule.server.security.ConsolePermissionService;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ConsoleAccountManagementService {

    private static final String ACCOUNT_MANAGE = "account:manage";
    private static final String ROLE_MANAGE = "role:manage";

    @Resource
    private ConsoleUserMapper userMapper;
    @Resource
    private ConsoleRoleMapper roleMapper;
    @Resource
    private ConsolePermissionMapper permissionMapper;
    @Resource
    private ConsoleUserRoleMapper userRoleMapper;
    @Resource
    private ConsoleRolePermissionMapper rolePermissionMapper;
    @Resource
    private ConsoleUserPermissionOverrideMapper overrideMapper;
    @Resource
    private ConsoleSecurityAuditLogMapper auditLogMapper;
    @Resource
    private ConsolePermissionService permissionService;

    public List<ConsoleUserDetail> listUsers() {
        List<ConsoleUser> users = userMapper.selectList(
                new LambdaQueryWrapper<ConsoleUser>()
                        .orderByAsc(ConsoleUser::getUsername));
        List<ConsoleUserDetail> result = new ArrayList<>(users.size());
        for (ConsoleUser user : users) {
            result.add(toUserDetail(user));
        }
        return result;
    }

    public ConsoleUserDetail getUser(Long userId) {
        return toUserDetail(requireUser(userId));
    }

    @Transactional
    public ConsoleUserDetail saveUser(ConsoleUserSaveRequest request,
                                      String operator) {
        if (request == null || request.getUsername() == null
                || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        boolean existingUser = request.getId() != null;
        ConsoleUser user;
        if (request.getId() == null) {
            if (request.getPassword() == null
                    || request.getPassword().length() < 8) {
                throw new IllegalArgumentException("新账户密码不能少于8位");
            }
            user = new ConsoleUser();
            user.setUsername(request.getUsername().trim());
            user.setDisplayName(displayName(request));
            user.setPasswordHash(BCrypt.hashpw(request.getPassword()));
            user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
            user.setPermissionVersion(1L);
            user.setCreateBy(operator);
            user.setUpdateBy(operator);
            userMapper.insert(user);
        } else {
            user = requireUser(request.getId());
            String username = request.getUsername().trim();
            if (!username.equals(user.getUsername())) {
                throw new IllegalArgumentException(
                        "用户名创建后不可修改，请编辑显示名称");
            }
            user.setUsername(username);
            user.setDisplayName(displayName(request));
            if (request.getStatus() != null) {
                user.setStatus(request.getStatus());
            }
            if (request.getPassword() != null
                    && !request.getPassword().isBlank()) {
                if (request.getPassword().length() < 8) {
                    throw new IllegalArgumentException("密码不能少于8位");
                }
                user.setPasswordHash(BCrypt.hashpw(request.getPassword()));
            }
            user.setUpdateBy(operator);
            userMapper.updateById(user);
        }
        if (request.getRoleIds() != null
                || request.getPermissionOverrides() != null) {
            replaceUserPermissions(user.getId(), request.getRoleIds(),
                    request.getPermissionOverrides(), operator);
        }
        if (existingUser) {
            bumpPermissionVersion(user, operator);
        }
        ensureAtLeastOneAdministrator();
        appendAudit(user.getId(), user.getUsername(),
                request.getId() == null ? "ACCOUNT_CREATE" : "ACCOUNT_UPDATE",
                null);
        return toUserDetail(user);
    }

    @Transactional
    public ConsoleUserDetail saveUserPermissions(
            Long userId,
            ConsoleUserPermissionRequest request,
            String operator) {
        ConsoleUser user = requireUser(userId);
        replaceUserPermissions(userId,
                request == null ? null : request.getRoleIds(),
                request == null ? null : request.getPermissionOverrides(),
                operator);
        bumpPermissionVersion(user, operator);
        ensureAtLeastOneAdministrator();
        appendAudit(userId, user.getUsername(),
                "ACCOUNT_PERMISSION_UPDATE", null);
        return toUserDetail(user);
    }

    @Transactional
    public void changeUserStatus(Long userId, Integer status, String operator) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("账户状态必须为0或1");
        }
        ConsoleUser user = requireUser(userId);
        if (status == 0 && isEnabledAdministrator(userId)
                && countOtherEnabledAdministrators(userId) == 0) {
            throw new LastAdministratorException();
        }
        persistUserStatus(user, status, operator);
        appendAudit(userId, user.getUsername(),
                status == 1 ? "ACCOUNT_ENABLE" : "ACCOUNT_DISABLE", null);
    }

    @Transactional
    public void resetPassword(Long userId, String rawPassword, String operator) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("密码不能少于8位");
        }
        ConsoleUser user = requireUser(userId);
        persistPasswordHash(user, BCrypt.hashpw(rawPassword), operator);
        appendAudit(userId, user.getUsername(), "ACCOUNT_PASSWORD_RESET", null);
    }

    public List<ConsoleRoleDetail> listRoles() {
        List<ConsoleRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<ConsoleRole>()
                        .orderByAsc(ConsoleRole::getRoleCode));
        List<ConsoleRoleDetail> result = new ArrayList<>(roles.size());
        for (ConsoleRole role : roles) {
            result.add(toRoleDetail(role));
        }
        return result;
    }

    @Transactional
    public ConsoleRoleDetail saveRole(ConsoleRoleSaveRequest request,
                                      String operator) {
        if (request == null || request.getRoleCode() == null
                || request.getRoleCode().isBlank()
                || request.getRoleName() == null
                || request.getRoleName().isBlank()) {
            throw new IllegalArgumentException("角色编码和名称不能为空");
        }
        ConsoleRole role;
        if (request.getId() == null) {
            role = new ConsoleRole();
            role.setRoleCode(request.getRoleCode().trim());
            role.setRoleName(request.getRoleName().trim());
            role.setDescription(request.getDescription());
            role.setStatus(request.getStatus() == null ? 1 : request.getStatus());
            role.setSystemRole(0);
            role.setCreateBy(operator);
            role.setUpdateBy(operator);
            roleMapper.insert(role);
        } else {
            role = requireRole(request.getId());
            role.setRoleCode(request.getRoleCode().trim());
            role.setRoleName(request.getRoleName().trim());
            role.setDescription(request.getDescription());
            if (request.getStatus() != null) {
                role.setStatus(request.getStatus());
            }
            role.setUpdateBy(operator);
            roleMapper.updateById(role);
        }
        if (request.getPermissionCodes() != null) {
            replaceRolePermissions(role.getId(),
                    request.getPermissionCodes(), operator);
            bumpRoleMemberPermissionVersions(role.getId(), operator);
        }
        ensureAtLeastOneAdministrator();
        appendAudit(null, operator,
                request.getId() == null ? "ROLE_CREATE" : "ROLE_UPDATE",
                role.getRoleCode());
        return toRoleDetail(role);
    }

    @Transactional
    public void changeRoleStatus(Long roleId, Integer status, String operator) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("角色状态必须为0或1");
        }
        ConsoleRole role = requireRole(roleId);
        role.setStatus(status);
        role.setUpdateBy(operator);
        roleMapper.updateById(role);
        bumpRoleMemberPermissionVersions(roleId, operator);
        ensureAtLeastOneAdministrator();
        appendAudit(null, operator,
                status == 1 ? "ROLE_ENABLE" : "ROLE_DISABLE",
                role.getRoleCode());
    }

    public List<ConsolePermission> listPermissions() {
        return permissionMapper.selectList(
                new LambdaQueryWrapper<ConsolePermission>()
                        .eq(ConsolePermission::getStatus, 1)
                        .orderByAsc(ConsolePermission::getSortOrder));
    }

    protected ConsoleUser requireUser(Long userId) {
        ConsoleUser user = userMapper.selectById(userId);
        if (user == null) throw new IllegalArgumentException("账户不存在");
        return user;
    }

    protected boolean isEnabledAdministrator(Long userId) {
        ConsoleUser user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            return false;
        }
        Set<String> permissions = permissionService.effectivePermissions(userId);
        return permissions.contains(ACCOUNT_MANAGE)
                && permissions.contains(ROLE_MANAGE);
    }

    protected long countOtherEnabledAdministrators(Long userId) {
        long count = 0L;
        for (ConsoleUser user : userMapper.selectList(
                new LambdaQueryWrapper<ConsoleUser>()
                        .eq(ConsoleUser::getStatus, 1)
                        .ne(ConsoleUser::getId, userId))) {
            if (isEnabledAdministrator(user.getId())) {
                count++;
            }
        }
        return count;
    }

    protected void persistUserStatus(ConsoleUser user, Integer status,
                                     String operator) {
        user.setStatus(status);
        user.setPermissionVersion(nextPermissionVersion(user));
        user.setUpdateBy(operator);
        userMapper.updateById(user);
    }

    protected void persistPasswordHash(ConsoleUser user, String passwordHash,
                                       String operator) {
        user.setPasswordHash(passwordHash);
        user.setPermissionVersion(nextPermissionVersion(user));
        user.setUpdateBy(operator);
        userMapper.updateById(user);
    }

    protected void appendAudit(Long userId, String username, String action,
                               String details) {
        if (auditLogMapper == null) return;
        ConsoleSecurityAuditLog log = new ConsoleSecurityAuditLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setAction(action);
        log.setTargetType(action.startsWith("ROLE") ? "CONSOLE_ROLE" : "CONSOLE_USER");
        log.setDetailsJson(details);
        auditLogMapper.insert(log);
    }

    private void replaceUserPermissions(Long userId,
                                        List<Long> roleIds,
                                        Map<String, String> overrides,
                                        String operator) {
        if (roleIds != null) {
            userRoleMapper.delete(new LambdaQueryWrapper<ConsoleUserRole>()
                    .eq(ConsoleUserRole::getUserId, userId));
            for (Long roleId : new LinkedHashSet<>(roleIds)) {
                ConsoleRole role = requireRole(roleId);
                if (!Integer.valueOf(1).equals(role.getStatus())) {
                    throw new IllegalArgumentException("不能分配已停用角色");
                }
                ConsoleUserRole membership = new ConsoleUserRole();
                membership.setUserId(userId);
                membership.setRoleId(roleId);
                membership.setCreateBy(operator);
                userRoleMapper.insert(membership);
            }
        }
        if (overrides != null) {
            overrideMapper.delete(
                    new LambdaQueryWrapper<ConsoleUserPermissionOverride>()
                            .eq(ConsoleUserPermissionOverride::getUserId, userId));
            Map<String, ConsolePermission> permissions = permissionsByCode();
            for (Map.Entry<String, String> entry : overrides.entrySet()) {
                String effect = normalizeEffect(entry.getValue());
                ConsolePermission permission = permissions.get(entry.getKey());
                if (permission == null) {
                    throw new IllegalArgumentException(
                            "权限不存在: " + entry.getKey());
                }
                ConsoleUserPermissionOverride row =
                        new ConsoleUserPermissionOverride();
                row.setUserId(userId);
                row.setPermissionId(permission.getId());
                row.setEffect(effect);
                row.setCreateBy(operator);
                row.setUpdateBy(operator);
                overrideMapper.insert(row);
            }
        }
    }

    private void replaceRolePermissions(Long roleId,
                                        List<String> permissionCodes,
                                        String operator) {
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<ConsoleRolePermission>()
                        .eq(ConsoleRolePermission::getRoleId, roleId));
        Map<String, ConsolePermission> permissions = permissionsByCode();
        for (String code : new LinkedHashSet<>(permissionCodes)) {
            ConsolePermission permission = permissions.get(code);
            if (permission == null) {
                throw new IllegalArgumentException("权限不存在: " + code);
            }
            ConsoleRolePermission grant = new ConsoleRolePermission();
            grant.setRoleId(roleId);
            grant.setPermissionId(permission.getId());
            grant.setCreateBy(operator);
            rolePermissionMapper.insert(grant);
        }
    }

    private ConsoleUserDetail toUserDetail(ConsoleUser user) {
        List<ConsoleUserRole> memberships = userRoleMapper.selectList(
                new LambdaQueryWrapper<ConsoleUserRole>()
                        .eq(ConsoleUserRole::getUserId, user.getId()));
        List<Long> roleIds = memberships.stream()
                .map(ConsoleUserRole::getRoleId).distinct().toList();
        List<ConsoleRole> roles = roleIds.isEmpty()
                ? Collections.emptyList() : roleMapper.selectByIds(roleIds);
        Set<String> inherited = permissionCodesForRoles(
                enabledRoleIds(roles));
        Map<String, String> overrides = overrideCodes(user.getId());
        ConsoleUserDetail detail = new ConsoleUserDetail();
        detail.setId(user.getId());
        detail.setUsername(user.getUsername());
        detail.setDisplayName(user.getDisplayName());
        detail.setStatus(user.getStatus());
        detail.setPermissionVersion(user.getPermissionVersion());
        detail.setLastLoginTime(user.getLastLoginTime());
        detail.setRoleIds(roleIds);
        detail.setRoleCodes(roles.stream().map(ConsoleRole::getRoleCode).toList());
        detail.setInheritedPermissions(inherited);
        detail.setPermissionOverrides(overrides);
        detail.setEffectivePermissions(
                permissionService.merge(inherited, overrides));
        return detail;
    }

    protected List<Long> enabledRoleIds(List<ConsoleRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return roles.stream()
                .filter(role -> Integer.valueOf(1)
                        .equals(role.getStatus()))
                .map(ConsoleRole::getId)
                .distinct()
                .toList();
    }

    private ConsoleRoleDetail toRoleDetail(ConsoleRole role) {
        ConsoleRoleDetail detail = new ConsoleRoleDetail();
        detail.setId(role.getId());
        detail.setRoleCode(role.getRoleCode());
        detail.setRoleName(role.getRoleName());
        detail.setDescription(role.getDescription());
        detail.setStatus(role.getStatus());
        detail.setSystemRole(role.getSystemRole());
        detail.setPermissionCodes(new ArrayList<>(
                permissionCodesForRoles(List.of(role.getId()))));
        Long memberCount = userRoleMapper.selectCount(
                new LambdaQueryWrapper<ConsoleUserRole>()
                        .eq(ConsoleUserRole::getRoleId, role.getId()));
        detail.setMemberCount(memberCount == null ? 0L : memberCount);
        return detail;
    }

    private Set<String> permissionCodesForRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return Collections.emptySet();
        List<ConsoleRolePermission> grants = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<ConsoleRolePermission>()
                        .in(ConsoleRolePermission::getRoleId, roleIds));
        if (grants.isEmpty()) return Collections.emptySet();
        List<Long> permissionIds = grants.stream()
                .map(ConsoleRolePermission::getPermissionId)
                .distinct().toList();
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (ConsolePermission permission
                : permissionMapper.selectByIds(permissionIds)) {
            if (Integer.valueOf(1).equals(permission.getStatus())) {
                codes.add(permission.getPermissionCode());
            }
        }
        return codes;
    }

    private Map<String, String> overrideCodes(Long userId) {
        List<ConsoleUserPermissionOverride> rows = overrideMapper.selectList(
                new LambdaQueryWrapper<ConsoleUserPermissionOverride>()
                        .eq(ConsoleUserPermissionOverride::getUserId, userId));
        if (rows.isEmpty()) return Collections.emptyMap();
        Map<Long, String> codes = new LinkedHashMap<>();
        for (ConsolePermission permission : permissionMapper.selectByIds(
                rows.stream().map(ConsoleUserPermissionOverride::getPermissionId)
                        .distinct().toList())) {
            codes.put(permission.getId(), permission.getPermissionCode());
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (ConsoleUserPermissionOverride row : rows) {
            String code = codes.get(row.getPermissionId());
            if (code != null) result.put(code, row.getEffect());
        }
        return result;
    }

    private Map<String, ConsolePermission> permissionsByCode() {
        Map<String, ConsolePermission> result = new LinkedHashMap<>();
        for (ConsolePermission permission : listPermissions()) {
            result.put(permission.getPermissionCode(), permission);
        }
        return result;
    }

    private void bumpPermissionVersion(ConsoleUser user, String operator) {
        user.setPermissionVersion(nextPermissionVersion(user));
        user.setUpdateBy(operator);
        userMapper.updateById(user);
    }

    private long nextPermissionVersion(ConsoleUser user) {
        return user.getPermissionVersion() == null
                ? 1L : user.getPermissionVersion() + 1L;
    }

    private void bumpRoleMemberPermissionVersions(Long roleId, String operator) {
        List<ConsoleUserRole> memberships = userRoleMapper.selectList(
                new LambdaQueryWrapper<ConsoleUserRole>()
                        .eq(ConsoleUserRole::getRoleId, roleId));
        for (ConsoleUserRole membership : memberships) {
            ConsoleUser user = userMapper.selectById(membership.getUserId());
            if (user != null) bumpPermissionVersion(user, operator);
        }
    }

    private void ensureAtLeastOneAdministrator() {
        for (ConsoleUser user : userMapper.selectList(
                new LambdaQueryWrapper<ConsoleUser>()
                        .eq(ConsoleUser::getStatus, 1))) {
            if (isEnabledAdministrator(user.getId())) return;
        }
        throw new LastAdministratorException();
    }

    private ConsoleRole requireRole(Long roleId) {
        ConsoleRole role = roleMapper.selectById(roleId);
        if (role == null) throw new IllegalArgumentException("角色不存在");
        return role;
    }

    private String normalizeEffect(String effect) {
        if (effect == null) throw new IllegalArgumentException("权限覆盖类型不能为空");
        String normalized = effect.toUpperCase(Locale.ROOT);
        if (!"ALLOW".equals(normalized) && !"DENY".equals(normalized)) {
            throw new IllegalArgumentException("权限覆盖类型必须为ALLOW或DENY");
        }
        return normalized;
    }

    private String displayName(ConsoleUserSaveRequest request) {
        return request.getDisplayName() == null
                || request.getDisplayName().isBlank()
                ? request.getUsername().trim() : request.getDisplayName().trim();
    }

    @Data
    public static class ConsoleUserDetail {
        private Long id;
        private String username;
        private String displayName;
        private Integer status;
        private Long permissionVersion;
        private java.time.LocalDateTime lastLoginTime;
        private List<Long> roleIds;
        private List<String> roleCodes;
        private Set<String> inheritedPermissions;
        private Map<String, String> permissionOverrides;
        private Set<String> effectivePermissions;
    }

    @Data
    public static class ConsoleRoleDetail {
        private Long id;
        private String roleCode;
        private String roleName;
        private String description;
        private Integer status;
        private Integer systemRole;
        private List<String> permissionCodes;
        private Long memberCount;
    }

    public static class LastAdministratorException extends IllegalStateException {
        public LastAdministratorException() {
            super("LAST_ADMIN_PROTECTION");
        }
    }
}
