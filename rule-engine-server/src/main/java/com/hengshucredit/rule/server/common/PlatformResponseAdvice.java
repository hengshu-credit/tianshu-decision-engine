package com.hengshucredit.rule.server.common;

import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** 控制器响应的 HTTP 状态只从公共结果码读取，文件和第三方原始报文不做包装。 */
@RestControllerAdvice
public class PlatformResponseAdvice implements ResponseBodyAdvice<Object> {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.hengshucredit.rule.server.system.SystemResponseMapping mapping;
    @Override public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) {
        return true;
    }

    @Override public Object beforeBodyWrite(Object body, MethodParameter method, MediaType mediaType,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> envelope) {
            String path = request.getURI().getPath();
            if (request instanceof org.springframework.http.server.ServletServerHttpRequest servlet
                    && servlet.getServletRequest().getAttribute(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI) instanceof String original) path = original;
            if (mapping != null && com.hengshucredit.rule.server.system.SystemResponseMapping.externalPath(path)) mapping.apply(envelope, response.getHeaders()::set);
            response.setStatusCode(HttpStatusCode.valueOf(envelope.status().httpStatus()));
            if (envelope.getTraceId() != null) response.getHeaders().set("X-Trace-Id", envelope.getTraceId());
            if (envelope.status().httpStatus() == 429) {
                Long delay = envelope.getData() instanceof RuleResult result ? result.getRetryAfterMs() : null;
                response.getHeaders().set("Retry-After", String.valueOf(delay == null ? 1 : Math.max(1, (delay + 999) / 1000)));
            }
        }
        return body;
    }
}
