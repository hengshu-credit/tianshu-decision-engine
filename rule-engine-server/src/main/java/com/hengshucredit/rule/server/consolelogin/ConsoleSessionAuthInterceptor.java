package com.hengshucredit.rule.server.consolelogin;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.server.common.R;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Objects;

/**
 * 在启用控制台登录时，对配置的 include 路径校验 HttpSession 中是否已登录。
 */
public class ConsoleSessionAuthInterceptor implements HandlerInterceptor {

    private final RuleEngineConsoleLoginProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private DatabaseConsoleAccountService databaseAccountService;

    public ConsoleSessionAuthInterceptor(
            RuleEngineConsoleLoginProperties properties) {
        this(properties, null);
    }

    public ConsoleSessionAuthInterceptor(
            RuleEngineConsoleLoginProperties properties,
            DatabaseConsoleAccountService databaseAccountService) {
        this.properties = properties;
        this.databaseAccountService = databaseAccountService;
    }

    public void setDatabaseAccountService(
            DatabaseConsoleAccountService databaseAccountService) {
        this.databaseAccountService = databaseAccountService;
    }

    /**
     * 判断当前请求是否允许访问：未命中需保护路径则放行；已登录则放行；否则返回 401 JSON。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!properties.isEnabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        if (!matchesAny(uri, properties.getIncludePatterns())) {
            return true;
        }
        if (matchesAny(uri, properties.getExcludePatterns())) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return writeUnauthorized(response, "需要登录");
        }
        Object user = session.getAttribute(properties.getSessionUsernameAttribute());
        if (user == null) {
            return writeUnauthorized(response, "需要登录");
        }
        if (databaseAccountService != null
                && !databaseSessionIsCurrent(session)) {
            clearIdentity(session, properties);
            return writeUnauthorized(response, "会话权限已变更，请重新登录");
        }
        return true;
    }

    private boolean databaseSessionIsCurrent(HttpSession session) {
        Object userIdValue = session.getAttribute(
                properties.getSessionUserIdAttribute());
        if (!(userIdValue instanceof Number number)) return false;
        DatabaseConsoleAccountService.ConsoleLoginResult current =
                databaseAccountService.currentUser(number.longValue());
        if (!current.authenticated()) return false;
        Object sessionVersion = session.getAttribute(
                properties.getSessionPermissionVersionAttribute());
        return Objects.equals(current.permissionVersion(), sessionVersion);
    }

    /** 撤销身份但保留会话容器，避免 Redis 并发请求因物理会话已删除而无法提交响应。 */
    static void clearIdentity(HttpSession session, RuleEngineConsoleLoginProperties properties) {
        session.removeAttribute(properties.getSessionUsernameAttribute());
        session.removeAttribute(properties.getSessionUserIdAttribute());
        session.removeAttribute(properties.getSessionPermissionVersionAttribute());
    }

    /**
     * 将 URI 与一组 Ant 模式做匹配，任一匹配即返回 true。
     */
    private boolean matchesAny(String uri, Iterable<String> patterns) {
        for (String p : patterns) {
            if (pathMatcher.match(p, uri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 写入与前端 axios 拦截器一致的 JSON 结构（code/message）。
     */
    private boolean writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(R.fail(401, message)));
        return false;
    }
}
