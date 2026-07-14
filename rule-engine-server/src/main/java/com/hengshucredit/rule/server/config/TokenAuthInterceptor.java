package com.hengshucredit.rule.server.config;

import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.service.ProjectAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Token认证拦截器
 * 用于验证客户端请求的Token有效性
 */
@Slf4j
@Component
public class TokenAuthInterceptor implements HandlerInterceptor {
    
    @Autowired
    private ProjectAuthService projectAuthService;
    
    // 需要验证Token的路径（同步API）
    private static final String[] PROTECTED_PATH_PREFIXES = {
        "/api/sync",
        "/api/rule/sync",
        "/api/rule/log/report",
        "/api/rule/auth/token"
    };
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        
        // 检查是否是需要保护的路径
        boolean needAuth = isProtectedPath(uri);
        
        // 不需要认证的路径直接放行
        if (!needAuth) {
            return true;
        }

        boolean tokenRequest = "/api/rule/auth/token".equals(uri);
        if (tokenRequest && !projectAuthService.isTokenRequestAllowed(request)) {
            projectAuthService.recordAccess(request, null, false, "RATE_LIMITED");
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"Too many token attempts\"}");
            return false;
        }
        
        ProjectAuthContext context = projectAuthService.authenticate(request);
        if (context == null) {
            if (tokenRequest) {
                projectAuthService.recordTokenFailure(request);
            }
            projectAuthService.recordAccess(request, null, false, "INVALID_CREDENTIAL");
            log.warn("Invalid project credential for request: {}", uri);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Invalid project credential\"}");
            return false;
        }
        context.attach(request);
        if ("GRACE".equals(context.getAuthPhase())) {
            response.setHeader("X-Rule-Token-State", "grace");
        }
        if (tokenRequest) {
            projectAuthService.clearTokenFailures(request);
        }
        projectAuthService.recordAccess(request, context, true, null);
        return true;
    }

    static boolean isProtectedPath(String uri) {
        if (uri == null) {
            return false;
        }
        for (String path : PROTECTED_PATH_PREFIXES) {
            if (uri.equals(path) || uri.startsWith(path + "/")) {
                return true;
            }
        }
        return false;
    }
    
}
