package com.hengshucredit.rule.server.audit;

import com.hengshucredit.rule.server.auth.TrustedClientAddressResolver;
import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class ConsoleOperationAuditFilter extends OncePerRequestFilter {
    private final RuleEngineConsoleLoginProperties properties;
    private final TrustedClientAddressResolver addressResolver;
    private final ConsoleOperationAudit audit;
    private final ConsoleOperationLogService logs;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/api/auth/console/login") || path.equals("/api/auth/console/logout")) return false;
        return !path.startsWith("/api/rule/") || path.startsWith("/api/rule/sync/")
                || path.startsWith("/api/rule/openapi/") || path.startsWith("/api/rule/runtime/")
                || path.equals("/api/rule/auth/token") || path.equals("/api/rule/log/report")
                || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var state = new ConsoleOperationAudit.State();
        request.setAttribute(ConsoleOperationAudit.STATE, state);
        var entry = state.log;
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object id = session.getAttribute(properties.getSessionUserIdAttribute());
            if (id instanceof Number number) entry.setUserId(number.longValue());
            Object username = session.getAttribute(properties.getSessionUsernameAttribute());
            if (username != null) entry.setUsername(username.toString());
        }
        if (entry.getUsername() == null && !properties.isEnabled()) entry.setUsername(ConsoleOperatorResolver.SYSTEM_CONSOLE);
        entry.setRequestMethod(request.getMethod());
        entry.setRequestPath(ConsoleOperationAudit.limit(request.getRequestURI(), 512));
        entry.setPagePath(pagePath(request.getHeader("X-Console-Page")));
        entry.setModule(module(request.getRequestURI()));
        entry.setAction(action(request.getMethod(), request.getRequestURI()));
        entry.setClientIp(addressResolver.resolve(request));
        entry.setCreateTime(LocalDateTime.now());
        boolean thrown = false;
        try { chain.doFilter(request, response); }
        catch (ServletException | IOException | RuntimeException error) { thrown = true; throw error; }
        finally {
            if (entry.getUsername() == null && request.getSession(false) != null) {
                HttpSession authenticated = request.getSession(false);
                Object id = authenticated.getAttribute(properties.getSessionUserIdAttribute());
                Object username = authenticated.getAttribute(properties.getSessionUsernameAttribute());
                if (id instanceof Number number && username != null) { entry.setUserId(number.longValue()); entry.setUsername(username.toString()); }
            }
            if (entry.getUsername() != null) {
                int code = thrown ? 500 : entry.getResultCode() == null ? response.getStatus() : entry.getResultCode();
                entry.setResultCode(code);
                entry.setOutcome(code == 401 || code == 403 ? "DENIED" : code >= 200 && code < 300 && !state.executionFailed ? "SUCCESS" : "FAILED");
                entry.setDurationMs((System.nanoTime() - state.started) / 1_000_000);
                audit.finish(request);
                try { logs.append(entry); }
                catch (RuntimeException error) { log.error("操作日志写入失败: actor={}, method={}, path={}", entry.getUsername(), entry.getRequestMethod(), entry.getRequestPath(), error); }
            }
        }
    }

    public static String pagePath(String path) {
        return ConsoleOperationPage.sanitize(path);
    }

    static String module(String path) {
        if (path.startsWith("/api/auth/console/")) return "account";
        String namespace = path.substring("/api/rule/".length()).split("/", 2)[0];
        return switch (namespace) {
            case "definition", "expression", "artifact" -> "rule";
            case "variable", "dataobject", "field-validation" -> "field";
            case "runtime-log" -> "log";
            case "governance" -> "approval";
            case "console" -> "account";
            default -> namespace;
        };
    }

    static String action(String method, String path) {
        if (path.equals("/api/auth/console/login")) return "LOGIN";
        if (path.equals("/api/auth/console/logout")) return "LOGOUT";
        if (path.startsWith("/api/rule/transfer/preview") || path.equals("/api/rule/expression/schema") || path.equals("/api/rule/function/methods")) return "READ";
        if (path.equals("/api/rule/variable/preview")) return "DEBUG";
        if ("GET".equals(method) || "HEAD".equals(method)) return path.contains("download") || path.endsWith("/export") ? "EXPORT" : "READ";
        String lower = path.toLowerCase(Locale.ROOT);
        if ("DELETE".equals(method) || lower.matches("/api/rule/governance/rules/[^/]+/delete")) return "DELETE";
        if (lower.matches(".*/publish(/.*)?$")) return "PUBLISH";
        if (lower.matches(".*/(offline|unpublish)(/.*)?$")) return "OFFLINE";
        if (lower.matches(".*/(rollback|restore)(/.*)?$")) return "ROLLBACK";
        if (lower.matches(".*/compile(/.*)?$")) return "COMPILE";
        for (String action : new String[]{"approve", "reject", "submit", "cancel", "import", "export"}) {
            if (lower.matches(".*/" + action + "(?:/.*)?$")) return action.toUpperCase(Locale.ROOT);
        }
        if (lower.matches(".*/(execute|execute-validated|test|invoke|invoke-preview|auth-test|replay|query|trials)(/.*)?$")) return "DEBUG";
        if ("POST".equals(method) && (lower.matches(".*/(upload|copy|temporary-draft)(/.*)?$")
                || lower.matches("/api/rule/(project|definition|variable|dataobject|function|datasource|database|experiment|list/library|billing/config)")
                || lower.matches(".*/api-scenarios$") || lower.endsWith("/revisions/draft"))) return "CREATE";
        return "UPDATE";
    }
}
