package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.ApiResponse;

import com.alibaba.fastjson.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Prevents legacy HTTP endpoints from bypassing lifecycle approval.
 *
 * <p>Governance adapters update projection services in-process after approval,
 * so they are not affected by this HTTP boundary.</p>
 */
public class GovernedProjectionMutationInterceptor implements HandlerInterceptor {

    public static final String ERROR_CODE = "GOVERNANCE_APPROVAL_REQUIRED";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || handlerMethod.getMethodAnnotation(
                GovernedProjectionMutation.class) == null) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(ApiResponse.fail(
                com.hengshucredit.rule.model.PlatformCode.GOVERNANCE_CONFLICT, ERROR_CODE)));
        return false;
    }
}
