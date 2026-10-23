package com.hengshucredit.rule.server.dashboard;

import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.security.ConsolePermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class DashboardAccessService {

    private final RuleEngineConsoleLoginProperties properties;
    private final ConsolePermissionService permissionService;

    public DashboardAccessService(
            RuleEngineConsoleLoginProperties properties,
            ConsolePermissionService permissionService) {
        this.properties = properties;
        this.permissionService = permissionService;
    }

    public DashboardAccessContext resolve(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return new DashboardAccessContext(true, null, null,
                    Collections.emptySet());
        }
        HttpSession session = request.getSession(false);
        if (session == null) return restricted();
        Long userId = userId(session.getAttribute(
                properties.getSessionUserIdAttribute()));
        Object username = session.getAttribute(
                properties.getSessionUsernameAttribute());
        if (userId == null || username == null
                || username.toString().isBlank()) {
            return restricted();
        }
        return new DashboardAccessContext(false, userId,
                username.toString(),
                permissionService.scope(userId).availablePermissions());
    }

    private DashboardAccessContext restricted() {
        return new DashboardAccessContext(false, null, null,
                Collections.emptySet());
    }

    private Long userId(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
