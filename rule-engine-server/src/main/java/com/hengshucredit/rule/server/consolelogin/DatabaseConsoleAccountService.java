package com.hengshucredit.rule.server.consolelogin;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.ConsolePermission;
import com.hengshucredit.rule.model.entity.ConsoleRole;
import com.hengshucredit.rule.model.entity.ConsoleRolePermission;
import com.hengshucredit.rule.model.entity.ConsoleSecurityAuditLog;
import com.hengshucredit.rule.model.entity.ConsoleUser;
import com.hengshucredit.rule.model.entity.ConsoleUserRole;
import com.hengshucredit.rule.server.mapper.ConsolePermissionMapper;
import com.hengshucredit.rule.server.mapper.ConsoleRoleMapper;
import com.hengshucredit.rule.server.mapper.ConsoleRolePermissionMapper;
import com.hengshucredit.rule.server.mapper.ConsoleSecurityAuditLogMapper;
import com.hengshucredit.rule.server.mapper.ConsoleUserMapper;
import com.hengshucredit.rule.server.mapper.ConsoleUserRoleMapper;
import com.hengshucredit.rule.server.security.ConsolePermissionCodes;
import com.hengshucredit.rule.server.security.ConsolePermissionService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DatabaseConsoleAccountService {

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

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
    private ConsoleSecurityAuditLogMapper auditLogMapper;
    @Resource
    private ConsolePermissionService permissionService;

    @Autowired(required = false)
    @Lazy
    private ConsoleLoginAuthenticator bootstrapAuthenticator;

    @Transactional
    public ConsoleLoginResult authenticate(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null) {
            return ConsoleLoginResult.failure();
        }
        String normalizedUsername = username.trim();
        if (countUsers() == 0) {
            if (!authenticateBootstrapSource(normalizedUsername, rawPassword)) {
                return ConsoleLoginResult.failure();
            }
            ConsoleUser user = createBootstrapUser(
                    normalizedUsername, rawPassword);
            persistBootstrapUser(user);
            updateLastLogin(user);
            appendAudit(user, "BOOTSTRAP_SUPER_ADMIN");
            return successfulResult(user, true);
        }

        ConsoleUser user = findUser(normalizedUsername);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || !matchesPassword(rawPassword, user.getPasswordHash())) {
            return ConsoleLoginResult.failure();
        }
        synchronizePermissionCatalogForExistingUsers(
                normalizedUsername);
        updateLastLogin(user);
        appendAudit(user, "LOGIN_SUCCESS");
        return successfulResult(user, false);
    }

    public ConsoleLoginResult currentUser(Long userId) {
        if (userId == null) return ConsoleLoginResult.failure();
        ConsoleUser user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            return ConsoleLoginResult.failure();
        }
        return successfulResult(user, false);
    }

    protected long countUsers() {
        Long count = userMapper.selectCount(null);
        return count == null ? 0L : count;
    }

    protected ConsoleUser findUser(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<ConsoleUser>()
                .eq(ConsoleUser::getUsername, username)
                .last("LIMIT 1"));
    }

    protected boolean authenticateBootstrapSource(String username,
                                                  String rawPassword) {
        return bootstrapAuthenticator != null
                && bootstrapAuthenticator.authenticate(username, rawPassword);
    }

    protected ConsoleUser createBootstrapUser(String username,
                                              String rawPassword) {
        ConsoleUser user = new ConsoleUser();
        user.setUsername(username);
        user.setDisplayName(username);
        user.setRegistrationStatus("ACTIVE");
        user.setRegistrationSource("ADMIN");
        user.setPasswordHash(BCrypt.hashpw(rawPassword));
        user.setStatus(1);
        user.setPermissionVersion(1L);
        user.setCreateBy(username);
        user.setUpdateBy(username);
        return user;
    }

    protected void persistBootstrapUser(ConsoleUser user) {
        PermissionCatalogSync catalog = synchronizePermissionCatalog();
        ConsoleRole role = ensureSuperAdministratorRole(user.getUsername());
        grantAllPermissions(
                role, catalog.activePermissions(), user.getUsername());
        userMapper.insert(user);

        ConsoleUserRole membership = new ConsoleUserRole();
        membership.setUserId(user.getId());
        membership.setRoleId(role.getId());
        membership.setCreateBy(user.getUsername());
        userRoleMapper.insert(membership);
    }

    protected List<String> resolveRoleCodes(Long userId) {
        return permissionService.roleCodes(userId);
    }

    protected Set<String> resolvePermissions(Long userId) {
        return permissionService.effectivePermissions(userId);
    }

    protected void updateLastLogin(ConsoleUser user) {
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    private ConsoleLoginResult successfulResult(ConsoleUser user,
                                                boolean bootstrapped) {
        return new ConsoleLoginResult(
                true,
                bootstrapped,
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPermissionVersion(),
                resolveRoleCodes(user.getId()),
                resolvePermissions(user.getId()));
    }

    private boolean matchesPassword(String rawPassword, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) return false;
        try {
            return BCrypt.checkpw(rawPassword, passwordHash);
        } catch (Exception ignored) {
            return false;
        }
    }

    private PermissionCatalogSync synchronizePermissionCatalog() {
        Set<String> createdCodes = new LinkedHashSet<>();
        for (ConsolePermissionCodes.PermissionDefinition definition
                : ConsolePermissionCodes.catalog()) {
            ConsolePermission permission = permissionMapper.selectOne(
                    new LambdaQueryWrapper<ConsolePermission>()
                            .eq(ConsolePermission::getPermissionCode,
                                    definition.code())
                            .last("LIMIT 1"));
            if (permission == null) {
                permission = new ConsolePermission();
                permission.setPermissionCode(definition.code());
                permission.setPermissionName(definition.name());
                permission.setPermissionGroup(definition.group());
                permission.setPermissionType(definition.type());
                permission.setMenuPath(definition.menuPath());
                permission.setSortOrder(definition.sortOrder());
                permission.setStatus(1);
                permissionMapper.insert(permission);
                createdCodes.add(definition.code());
            }
        }
        List<ConsolePermission> activePermissions = permissionMapper.selectList(
                new LambdaQueryWrapper<ConsolePermission>()
                        .eq(ConsolePermission::getStatus, 1));
        List<ConsolePermission> createdPermissions = activePermissions.stream()
                .filter(permission -> createdCodes.contains(
                        permission.getPermissionCode()))
                .toList();
        return new PermissionCatalogSync(
                activePermissions, createdPermissions);
    }

    /**
     * Existing installations may gain new menu/action permissions after the
     * first administrator was bootstrapped. Refresh the catalog on successful
     * login. Only permissions added by this catalog refresh are assigned to
     * the system administrator role; deliberately revoked grants stay revoked.
     */
    protected void synchronizePermissionCatalogForExistingUsers(
            String operator) {
        PermissionCatalogSync catalog =
                synchronizePermissionCatalog();
        ConsoleRole role = roleMapper.selectOne(
                new LambdaQueryWrapper<ConsoleRole>()
                        .eq(ConsoleRole::getRoleCode,
                                SUPER_ADMIN_ROLE_CODE)
                        .last("LIMIT 1"));
        if (role != null && !catalog.createdPermissions().isEmpty()) {
            grantAllPermissions(
                    role, catalog.createdPermissions(), operator);
        }
    }

    private record PermissionCatalogSync(
            List<ConsolePermission> activePermissions,
            List<ConsolePermission> createdPermissions) {
    }

    private ConsoleRole ensureSuperAdministratorRole(String operator) {
        ConsoleRole role = roleMapper.selectOne(
                new LambdaQueryWrapper<ConsoleRole>()
                        .eq(ConsoleRole::getRoleCode, SUPER_ADMIN_ROLE_CODE)
                        .last("LIMIT 1"));
        if (role != null) return role;
        role = new ConsoleRole();
        role.setRoleCode(SUPER_ADMIN_ROLE_CODE);
        role.setRoleName("超级管理员");
        role.setDescription("系统引导创建，拥有全部控制台功能权限");
        role.setStatus(1);
        role.setSystemRole(1);
        role.setCreateBy(operator);
        role.setUpdateBy(operator);
        roleMapper.insert(role);
        return role;
    }

    private void grantAllPermissions(ConsoleRole role,
                                     List<ConsolePermission> permissions,
                                     String operator) {
        List<ConsoleRolePermission> existing = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<ConsoleRolePermission>()
                        .eq(ConsoleRolePermission::getRoleId, role.getId()));
        Set<Long> grantedIds = existing.stream()
                .map(ConsoleRolePermission::getPermissionId)
                .collect(java.util.stream.Collectors.toSet());
        for (ConsolePermission permission : permissions) {
            if (grantedIds.contains(permission.getId())) continue;
            ConsoleRolePermission grant = new ConsoleRolePermission();
            grant.setRoleId(role.getId());
            grant.setPermissionId(permission.getId());
            grant.setCreateBy(operator);
            rolePermissionMapper.insert(grant);
        }
    }

    private void appendAudit(ConsoleUser user, String action) {
        if (auditLogMapper == null) return;
        ConsoleSecurityAuditLog log = new ConsoleSecurityAuditLog();
        log.setUserId(user.getId());
        log.setUsername(user.getUsername());
        log.setAction(action);
        log.setTargetType("CONSOLE_USER");
        log.setTargetId(String.valueOf(user.getId()));
        auditLogMapper.insert(log);
    }

    public record ConsoleLoginResult(boolean authenticated,
                                     boolean bootstrapped,
                                     Long userId,
                                     String username,
                                     String displayName,
                                     Long permissionVersion,
                                     List<String> roleCodes,
                                     Set<String> permissions) {
        public ConsoleLoginResult {
            roleCodes = roleCodes == null
                    ? Collections.emptyList() : List.copyOf(roleCodes);
            permissions = permissions == null
                    ? Collections.emptySet() : Set.copyOf(permissions);
        }

        public static ConsoleLoginResult failure() {
            return new ConsoleLoginResult(
                    false, false, null, null, null, null,
                    Collections.emptyList(), Collections.emptySet());
        }
    }
}
