package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class ConsoleOperatorResolver {
    public static final String SYSTEM_CONSOLE = "SYSTEM_CONSOLE";

    @Resource
    private RuleEngineConsoleLoginProperties consoleLoginProperties;

    public String resolve() {
        if (consoleLoginProperties == null || !consoleLoginProperties.isEnabled()) {
            return SYSTEM_CONSOLE;
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return SYSTEM_CONSOLE;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpSession session = request.getSession(false);
        if (session == null) return SYSTEM_CONSOLE;
        Object value = session.getAttribute(consoleLoginProperties.getSessionUsernameAttribute());
        if (value == null || value.toString().isBlank()) return SYSTEM_CONSOLE;
        return value.toString();
    }
}
