package com.hengshucredit.rule.runtime;

import com.hengshucredit.rule.client.RuleEngineClient;
import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.ExternalCodeMapping;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** 本地鉴权/参数错误与实际执行结果使用同一映射快照。 */
@RestControllerAdvice
public class RuntimeResponseMapping implements ResponseBodyAdvice<Object> {
    private final ObjectProvider<RuleEngineClient> sdk;
    public RuntimeResponseMapping(ObjectProvider<RuleEngineClient> sdk) { this.sdk = sdk; }
    private ExternalCodeMapping current() {
        RuleEngineClient client = sdk.getIfAvailable();
        return client == null ? ExternalCodeMapping.DEFAULT : client.getCodeMapping();
    }
    @Override public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public Object beforeBodyWrite(Object body, MethodParameter method, MediaType type,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> envelope) {
            ExternalCodeMapping mapping = current();
            envelope.mapExternal(mapping);
            if (mapping.enabled()) {
                response.getHeaders().set(ExternalCodeMapping.STANDARD_CODE_HEADER, envelope.status().code());
                response.getHeaders().set(ExternalCodeMapping.VERSION_HEADER, Long.toString(mapping.version()));
            }
        }
        return body;
    }
}
