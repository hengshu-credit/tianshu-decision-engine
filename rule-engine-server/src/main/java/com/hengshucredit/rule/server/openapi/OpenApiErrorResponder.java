package com.hengshucredit.rule.server.openapi;

import com.hengshucredit.rule.core.trace.TraceIdGenerator;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** 在进入 Controller 前失败时仍按开放接口统一模板响应。 */
@Component
public class OpenApiErrorResponder {
    @Resource
    private OpenApiContractService contractService;
    @Resource
    private OpenResponseRenderer responseRenderer;

    public void write(HttpServletRequest request, HttpServletResponse response,
                      OpenApiStatus status) throws IOException {
        OpenApiContract contract = contractService.defaultContract();
        String traceId = TraceIdGenerator.generate("AP", "G", TraceIdGenerator.GLOBAL_SCOPE_CODE);
        try {
            ProjectAuthContext authContext = ProjectAuthContext.from(request);
            if (authContext == null) throw new IllegalStateException("No authenticated project context");
            OpenApiContractService.ResolvedContract resolved = contractService.resolve(
                    authContext.getAuthId(), request.getHeader("X-Auth-Code"), ruleCode(request.getRequestURI()));
            contract = resolved.getContract();
            String scopeCode = resolved.getProject().getTraceScopeCode();
            if (scopeCode == null || scopeCode.trim().isEmpty()) {
                scopeCode = TraceIdGenerator.projectScopeCode(resolved.getProject().getId());
            }
            traceId = TraceIdGenerator.generate("AP", "P", scopeCode);
        } catch (RuntimeException ignored) {
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("error.message", status.getMessage());
        OpenResponseRenderer.RenderedResponse rendered = responseRenderer.render(contract, status, traceId, values);
        response.setStatus(rendered.getHttpStatus());
        for (Map.Entry<String, String> header : rendered.getHeaders().entrySet()) {
            response.setHeader(header.getKey(), header.getValue());
        }
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(com.alibaba.fastjson.JSON.toJSONString(rendered.getBody()));
    }

    private String ruleCode(String uri) {
        String prefix = "/api/rule/open/execute/";
        return uri != null && uri.startsWith(prefix) ? uri.substring(prefix.length()) : "";
    }
}
