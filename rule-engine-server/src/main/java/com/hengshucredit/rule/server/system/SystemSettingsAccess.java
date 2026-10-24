package com.hengshucredit.rule.server.system;

import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.security.ConsoleAccessDeniedException;
import com.hengshucredit.rule.server.security.ConsolePermissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 系统配置需要真实控制台身份，不能被关闭控制台登录或项目凭证绕过。 */
@Component
public class SystemSettingsAccess {
    private final ConsolePermissionService permissions;
    private final RuleEngineConsoleLoginProperties properties;
    public SystemSettingsAccess(ConsolePermissionService permissions, RuleEngineConsoleLoginProperties properties) {
        this.permissions = permissions; this.properties = properties;
    }
    public void require(HttpServletRequest request, String permission) {
        if (!properties.isEnabled()) throw new ConsoleAccessDeniedException("系统配置需要启用控制台登录");
        var session = request.getSession(false);
        Object id = session == null ? null : session.getAttribute(properties.getSessionUserIdAttribute());
        if (!(id instanceof Number number) || !permissions.hasPermission(number.longValue(), permission)) throw new ConsoleAccessDeniedException(permission);
    }
    public void requireWrite() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) require(attrs.getRequest(), "system:edit");
    }
}
