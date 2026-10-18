package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;

/** Applies the optional runtime-log content policy before synchronous or async persistence. */
@Component
public class RuntimeCallLogContentPolicy {
    @Resource
    private ExternalCallProperties properties;

    public void apply(RuleRuntimeCallLog log) {
        if (log == null) return;
        if (!properties.isRuntimeLogContentEnabled()) {
            clearContent(log);
            return;
        }
        int limit = properties.getRuntimeLogMaxFieldBytes();
        if (limit <= 0) return;
        log.setRequestHeaders(limit(log.getRequestHeaders(), limit));
        log.setRequestParams(limit(log.getRequestParams(), limit));
        log.setRequestBody(limit(log.getRequestBody(), limit));
        log.setRawRequestBody(limit(log.getRawRequestBody(), limit));
        log.setRawRequestMetadata(limit(log.getRawRequestMetadata(), limit));
        log.setOriginalRequestBody(limit(log.getOriginalRequestBody(), limit));
        log.setTraceSteps(limit(log.getTraceSteps(), limit));
        log.setResponseBody(limit(log.getResponseBody(), limit));
        log.setRawResponseBody(limit(log.getRawResponseBody(), limit));
        log.setRawResponseMetadata(limit(log.getRawResponseMetadata(), limit));
        log.setOriginalResponseBody(limit(log.getOriginalResponseBody(), limit));
    }

    private void clearContent(RuleRuntimeCallLog log) {
        log.setRequestHeaders(null);
        log.setRequestParams(null);
        log.setRequestBody(null);
        log.setRawRequestBody(null);
        log.setRawRequestMetadata(null);
        log.setOriginalRequestBody(null);
        log.setTraceSteps(null);
        log.setResponseBody(null);
        log.setRawResponseBody(null);
        log.setRawResponseMetadata(null);
        log.setOriginalResponseBody(null);
    }

    private String limit(String value, int maxBytes) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) return value;
        return null;
    }
}
