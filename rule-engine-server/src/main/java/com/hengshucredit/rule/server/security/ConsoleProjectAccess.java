package com.hengshucredit.rule.server.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.ConsoleUserProject;
import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.mapper.ConsoleUserProjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Set;
import java.util.stream.Collectors;

/** 控制台项目范围只保存在当前请求中；不会改变 SDK 的项目凭证鉴权。 */
@Service
public class ConsoleProjectAccess {
    private static final String ATTRIBUTE = ConsoleProjectAccess.class.getName();
    private static final ThreadLocal<Scope> ASYNC_SCOPE = new ThreadLocal<>();
    @Resource private RuleEngineConsoleLoginProperties properties;
    @Resource private ConsoleUserProjectMapper projects;
    @Resource private ConsolePermissionService permissions;

    public Scope current() {
        Scope async = ASYNC_SCOPE.get();
        if (async != null) return async;
        if (!properties.isEnabled() || !(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return null;
        var request = attrs.getRequest();
        String path = request.getRequestURI();
        if (!path.startsWith("/api/rule/") || path.startsWith("/api/rule/sync/")
                || path.startsWith("/api/rule/openapi/") || path.startsWith("/api/rule/runtime/")
                || path.equals("/api/rule/auth/token") || path.equals("/api/rule/log/report")) return null;
        if (request.getAttribute(ATTRIBUTE) instanceof Scope scope) return scope;
        var session = request.getSession(false);
        Object identity = session == null ? null : session.getAttribute(properties.getSessionUserIdAttribute());
        Long id = identity instanceof Number number ? number.longValue() : null;
        Scope scope = id == null ? new Scope(null, Set.of(), Set.of()) : permissions.scope(id);
        request.setAttribute(ATTRIBUTE, scope);
        return scope;
    }

    public void requireProject(Long projectId) {
        Scope scope = current();
        if (scope != null && !scope.allowsProject(projectId)) {
            throw new ConsoleAccessDeniedException("无权访问该项目");
        }
    }

    public void requireScope(String resourceScope, Long projectId) {
        if (!"GLOBAL".equals(resourceScope)) requireProject(projectId);
    }

    public void requirePermission(String permission, Long projectId) {
        Scope scope = current();
        if (scope != null && !scope.can(permission, projectId)) throw new ConsoleAccessDeniedException("当前项目/全局范围无此操作权限: " + permission);
    }

    public void requireGlobalEditable() {
        Scope scope = current();
        if (scope != null && !scope.globalEditable()) throw new ConsoleAccessDeniedException("当前账户只允许查看全局内容");
    }

    public void requireUnrestrictedPermissions(String... required) {
        Scope scope = current();
        if (scope == null) return;
        for (String permission : required) {
            if (!scope.globalCan(permission) || !scope.allProjectPermission(permission)) {
                throw new ConsoleAccessDeniedException("此操作影响当前节点全部项目，需要全局及所有项目的权限: " + permission);
            }
        }
    }

    public java.util.Map<String, Object> globalStatusSummary(java.util.Map<String, Object> details, String permission) {
        Scope scope = current();
        if (scope == null || scope.globalCan(permission) && scope.allProjectPermission(permission)) return details;
        java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
        for (String key : java.util.List.of("state", "targetCount", "preparedCount", "successCount", "failureCount", "cpuFallbackCount")) {
            if (details.containsKey(key)) summary.put(key, details.get(key));
        }
        summary.put("detailsRestricted", true);
        summary.put("failures", java.util.List.of());
        return summary;
    }

    public Runnable wrap(Runnable action) {
        Scope captured = current();
        if (captured == null) return action;
        return () -> {
            Scope previous = ASYNC_SCOPE.get();
            ASYNC_SCOPE.set(permissions == null || captured.userId() == null ? captured : permissions.scope(captured.userId()));
            try { action.run(); }
            finally { if (previous == null) ASYNC_SCOPE.remove(); else ASYNC_SCOPE.set(previous); }
        };
    }

    public <T> java.util.concurrent.Callable<T> wrap(java.util.concurrent.Callable<T> action) {
        Scope captured = current();
        if (captured == null) return action;
        return () -> {
            Scope previous = ASYNC_SCOPE.get();
            ASYNC_SCOPE.set(permissions == null || captured.userId() == null ? captured : permissions.scope(captured.userId()));
            try { return action.call(); }
            finally { if (previous == null) ASYNC_SCOPE.remove(); else ASYNC_SCOPE.set(previous); }
        };
    }

    public void grantCreatedProject(Long projectId) {
        Scope scope = current();
        if (scope == null || scope.userId() == null) return;
        var grant = new ConsoleUserProject();
        grant.setUserId(scope.userId()); grant.setProjectId(projectId);
        projects.insert(grant);
        var ids = new java.util.LinkedHashSet<>(scope.projectIds()); ids.add(projectId);
        Scope updated = new Scope(scope.userId(), ids, scope.permissions(), scope.allProjects(), scope.globalEditable(), scope.projectOverrides());
        if (ASYNC_SCOPE.get() != null) ASYNC_SCOPE.set(updated);
        else if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) attrs.getRequest().setAttribute(ATTRIBUTE, updated);
    }

    public record Scope(Long userId, Set<Long> projectIds, Set<String> permissions, boolean allProjects,
                        boolean globalEditable, java.util.Map<Long, java.util.Map<String, String>> projectOverrides) {
        public Scope(Long userId, Set<Long> projectIds, Set<String> permissions) { this(userId, projectIds, permissions, false, true, java.util.Map.of()); }
        public Scope {
            projectIds = Set.copyOf(projectIds); permissions = Set.copyOf(permissions);
            var overrides = new java.util.LinkedHashMap<Long, java.util.Map<String, String>>();
            projectOverrides.forEach((key, value) -> overrides.put(key, java.util.Map.copyOf(value)));
            projectOverrides = java.util.Map.copyOf(overrides);
        }
        public boolean allowsProject(Long id) { return id != null && id > 0 && (allProjects || projectIds.contains(id)); }
        public boolean globalCan(String code) {
            if (code != null && code.startsWith("system:")) return permissions.contains(code)
                    && (code.equals("system:view") || code.equals("system:audit") || globalEditable);
            if ("function:native".equals(code)) return allProjects && globalEditable && permissions.contains(code)
                    && permissions.contains("account:manage") && permissions.contains("role:manage");
            return permissions.contains(code) && (globalEditable || !isContentMutation(code));
        }
        public boolean allProjectPermission(String code) {
            return allProjects && permissions.contains(code) && projectOverrides.values().stream().noneMatch(value -> "DENY".equals(value.get(code)));
        }
        public boolean can(String code, Long projectId) {
            if (!isProjectPermission(code)) return globalCan(code);
            if (projectId == null || projectId <= 0) return globalCan(code);
            if (!allowsProject(projectId)) return false;
            String effect = projectOverrides.getOrDefault(projectId, java.util.Map.of()).get(code);
            return "ALLOW".equals(effect) || (!"DENY".equals(effect) && permissions.contains(code));
        }
        public boolean can(String code) {
            if (!isProjectPermission(code)) return globalCan(code);
            if (globalCan(code) || (allProjects && permissions.contains(code))) return true;
            return projectIds.stream().anyMatch(id -> can(code, id)) || (allProjects && projectOverrides.keySet().stream().anyMatch(id -> can(code, id)));
        }
        public Set<String> availablePermissions() {
            var codes = new java.util.LinkedHashSet<>(permissions);
            projectOverrides.values().forEach(value -> codes.addAll(value.keySet()));
            codes.removeIf(code -> !can(code));
            return Set.copyOf(codes);
        }
        public String projectSql() { return projectIds.isEmpty() ? "NULL" : projectIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(",")); }
    }

    public static boolean isProjectPermission(String code) { return code != null && !code.startsWith("account:") && !code.startsWith("role:") && !code.startsWith("system:") && !"function:native".equals(code); }
    public static boolean isContentMutation(String code) {
        return isProjectPermission(code) && (code.endsWith(":edit") || code.endsWith(":submit") || code.equals("approval:approve") || code.equals("transfer:import"));
    }
}
