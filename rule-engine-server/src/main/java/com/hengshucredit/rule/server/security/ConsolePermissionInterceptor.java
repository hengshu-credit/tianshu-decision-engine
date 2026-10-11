package com.hengshucredit.rule.server.security;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
public class ConsolePermissionInterceptor implements HandlerInterceptor {

    private final RuleEngineConsoleLoginProperties properties;
    private final ConsolePermissionService permissionService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePermission requirement = handlerMethod.getMethodAnnotation(
                RequirePermission.class);
        if (requirement == null) {
            requirement = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(), RequirePermission.class);
        }
        if (requirement == null) {
            if (isDashboardPath(request)) {
                return true;
            }
            String implicitPermission =
                    resolveImplicitPermission(request);
            if (implicitPermission == null) {
                if (isProtectedManagementPath(request)) {
                    return deny(response, "PERMISSION_MAPPING_MISSING");
                }
                return true;
            }
            return authorize(request, response, implicitPermission);
        }
        Long userId = resolveUserId(request.getSession(false));
        if (userId != null) {
            // 空权限只用于已登录用户的前端访问留痕，事件对象另行校验权限。
            if (requirement.value().isEmpty() || permissionService.hasPermission(userId, requirement.value())) return true;
            for (String code : requirement.anyOf()) if (permissionService.hasPermission(userId, code)) return true;
        }
        return deny(response, "PERMISSION_DENIED");
    }

    private boolean authorize(HttpServletRequest request,
                              HttpServletResponse response,
                              String permissionCode) throws Exception {
        Long userId = resolveUserId(request.getSession(false));
        if (userId != null
                && permissionService.hasPermission(
                userId, permissionCode)) {
            return true;
        }
        return deny(response, "PERMISSION_DENIED");
    }

    private boolean deny(HttpServletResponse response,
                         String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(
                R.fail(HttpServletResponse.SC_FORBIDDEN, message)));
        return false;
    }

    private boolean isProtectedManagementPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/rule/");
    }

    private boolean isDashboardPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && (path.equals("/api/rule/dashboard")
                || path.startsWith("/api/rule/dashboard/"));
    }

    private String resolveImplicitPermission(
            HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/rule/")) {
            return null;
        }
        String module = modulePermission(path);
        if (module == null) {
            return null;
        }
        if ("project".equals(module) && path.endsWith("/full")) {
            return "project:edit";
        }
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method)) {
            return module + ":view";
        }
        String lowerPath = path.toLowerCase();
        if (lowerPath.matches(
                ".*/(approve|reject)(/.*)?$")) {
            return "approval:approve";
        }
        if (lowerPath.matches(
                ".*/(submit|publish|unpublish|offline|rollback)(/.*)?$")) {
            return module + ":submit";
        }
        return module + ":edit";
    }

    private String modulePermission(String path) {
        String namespace = path.substring("/api/rule/".length()).split("/", 2)[0];
        return switch (namespace) {
            case "project", "model", "datasource", "database", "function",
                 "experiment", "list", "billing", "lineage" -> namespace;
            case "definition", "expression", "artifact" -> "rule";
            case "variable", "dataobject", "field-validation" -> "field";
            case "log", "runtime-log" -> "log";
            default -> null;
        };
    }

    private Long resolveUserId(HttpSession session) {
        if (session == null) return null;
        Object value = session.getAttribute(properties.getSessionUserIdAttribute());
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) return null;
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
