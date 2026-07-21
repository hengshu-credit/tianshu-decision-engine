package com.hengshucredit.rule.server.config;

import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.auth.ProjectAccessPolicy;
import com.hengshucredit.rule.server.auth.ProjectExecutionGuard;
import com.hengshucredit.rule.server.auth.TrustedClientAddressResolver;
import com.hengshucredit.rule.server.openapi.OpenApiErrorResponder;
import com.hengshucredit.rule.server.openapi.OpenApiStatus;
import com.hengshucredit.rule.server.service.ProjectAuthService;
import com.hengshucredit.rule.server.service.RequestDeadlineContext;
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

    @Autowired
    private TrustedClientAddressResolver clientAddressResolver;

    @Autowired
    private ProjectExecutionGuard executionGuard;

    @Autowired(required = false)
    private OpenApiErrorResponder openApiErrorResponder;

    private static final String PERMIT_ATTRIBUTE = TokenAuthInterceptor.class.getName() + ".permit";
    
    // 需要验证Token的路径（同步API）
    private static final String[] PROTECTED_PATH_PREFIXES = {
        "/api/sync",
        "/api/rule/sync",
        "/api/rule/open",
        "/api/rule/log/report",
        "/api/rule/auth/token"
    };
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        
        // 检查是否是需要保护的路径
        boolean needAuth = isProtectedPath(uri);
        
        // 不需要认证的路径直接放行
        if (!needAuth) {
            return true;
        }
        long requestStartedAt = RequestDeadlineContext.currentNanoTime();

        boolean tokenRequest = "/api/rule/auth/token".equals(uri);
        if (tokenRequest && !projectAuthService.isTokenRequestAllowed(request)) {
            projectAuthService.recordAccess(request, null, false, "RATE_LIMITED");
            writeError(request, response, new OpenApiStatus(false, "RATE_LIMITED",
                    "Too many token attempts", 429));
            return false;
        }
        
        ProjectAuthContext context = projectAuthService.authenticate(request);
        if (context == null) {
            if (tokenRequest) {
                projectAuthService.recordTokenFailure(request);
            }
            projectAuthService.recordAccess(request, null, false, "INVALID_CREDENTIAL");
            log.warn("Invalid project credential for request: {}", uri);
            writeError(request, response, new OpenApiStatus(false, "INVALID_CREDENTIAL",
                    "Invalid project credential", HttpServletResponse.SC_UNAUTHORIZED));
            return false;
        }
        context.attach(request);
        ProjectAccessPolicy policy = context.getAccessPolicy();
        String clientIp = clientAddressResolver.resolve(request);
        if (!policy.getIpWhitelist().isEmpty()
                && !clientAddressResolver.matchesIp(clientIp, policy.getIpWhitelist())) {
            projectAuthService.recordAccess(request, context, false, "IP_NOT_ALLOWED");
            writeError(request, response, new OpenApiStatus(false, "IP_NOT_ALLOWED",
                    "Client IP is not allowed", 403));
            return false;
        }
        if (!clientAddressResolver.matchesHost(request.getHeader("Host"), policy.getHostWhitelist())) {
            projectAuthService.recordAccess(request, context, false, "HOST_NOT_ALLOWED");
            writeError(request, response, new OpenApiStatus(false, "HOST_NOT_ALLOWED",
                    "Request Host is not allowed", 403));
            return false;
        }
        if (isExecutionPath(uri)) {
            try {
                ProjectExecutionGuard.Permit permit = executionGuard.acquire(context.getAuthId(), policy);
                request.setAttribute(PERMIT_ATTRIBUTE, permit);
                RequestDeadlineContext.startAt(requestStartedAt, policy.getRequestTimeoutMs());
            } catch (ProjectExecutionGuard.Rejected e) {
                projectAuthService.recordAccess(request, context, false, e.getReason().name());
                writeError(request, response, new OpenApiStatus(false, "EXECUTION_LIMITED",
                        "Execution rate or concurrency limit exceeded", 429));
                return false;
            }
        }
        if ("GRACE".equals(context.getAuthPhase())) {
            response.setHeader("X-Rule-Token-State", "grace");
        }
        if (tokenRequest) {
            projectAuthService.clearTokenFailures(request);
        }
        projectAuthService.recordAccess(request, context, true, null);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Object permit = request.getAttribute(PERMIT_ATTRIBUTE);
        if (permit instanceof ProjectExecutionGuard.Permit) {
            ((ProjectExecutionGuard.Permit) permit).close();
            request.removeAttribute(PERMIT_ATTRIBUTE);
        }
        RequestDeadlineContext.clear();
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            OpenApiStatus status) throws java.io.IOException {
        if (request.getRequestURI() != null && request.getRequestURI().startsWith("/api/rule/open/")
                && openApiErrorResponder != null) {
            openApiErrorResponder.write(request, response, status);
            return;
        }
        response.setStatus(status.getHttpStatus());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status.getHttpStatus() + ",\"message\":\""
                + escape(status.getMessage()) + "\"}");
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static boolean isExecutionPath(String uri) {
        return uri != null && (uri.startsWith("/api/rule/open/execute/")
                || uri.startsWith("/api/rule/sync/execute/"));
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
