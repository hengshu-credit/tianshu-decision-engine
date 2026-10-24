package com.hengshucredit.rule.server.openapi;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 使用固定平台外层结构渲染开放接口，模板仅定制 data 和业务响应头。 */
@Component
public class OpenResponseRenderer {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.hengshucredit.rule.server.system.SystemResponseMapping responseMapping;

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");
    private static final Pattern HEADER_NAME = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");
    private static final List<String> FORBIDDEN_HEADERS = Arrays.asList(
            "content-length", "transfer-encoding", "connection", "set-cookie", "host", "content-type", "x-trace-id", "retry-after", "x-rule-standard-code", "x-rule-code-mapping-version");

    public RenderedResponse render(OpenApiContract contract, OpenApiStatus status, String traceId,
                                   Map<String, Object> values) {
        validate(contract);
        var mapping = responseMapping == null ? com.hengshucredit.rule.model.ExternalCodeMapping.DEFAULT : responseMapping.current();
        var standard = com.hengshucredit.rule.model.PlatformCode.find(status.getCode());
        Map<String, Object> context = new LinkedHashMap<>();
        if (values != null) context.putAll(values);
        context.put("status.success", status.isSuccess());
        context.put("status.code", mapping.code(standard));
        context.put("status.message", mapping.message(standard));
        context.put("status.detail", status.getDetail());
        context.put("status.httpStatus", status.getHttpStatus());
        context.put("traceId", traceId);
        Object dataTemplate = status.isSuccess() ? contract.getSuccessDataTemplate() : contract.getErrorDataTemplate();
        Object data = renderValue(dataTemplate, context);
        context.put("data", data);
        JSONObject body = new JSONObject(true);
        body.put("success", status.isSuccess());
        body.put("code", mapping.code(standard));
        body.put("message", mapping.message(standard));
        body.put("detail", status.getDetail());
        body.put("traceId", traceId);
        body.put("data", data);
        Map<String, String> headers = renderHeaders(contract.getResponseHeaders(), context);
        headers.put("Content-Type", "application/json");
        headers.put("X-Trace-Id", traceId);
        if (mapping.enabled()) {
            headers.put(com.hengshucredit.rule.model.ExternalCodeMapping.STANDARD_CODE_HEADER, standard.code());
            headers.put(com.hengshucredit.rule.model.ExternalCodeMapping.VERSION_HEADER, Long.toString(mapping.version()));
        }
        return new RenderedResponse(body, headers, status.getHttpStatus());
    }

    public void validate(OpenApiContract contract) {
        if (contract == null) throw new IllegalArgumentException("开放接口配置不能为空");
        validateHeaders(contract.getResponseHeaders());
    }

    private Object renderValue(Object template, Map<String, Object> context) {
        if (template instanceof Map) {
            JSONObject result = new JSONObject(true);
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) template).entrySet()) {
                result.put(String.valueOf(entry.getKey()), renderValue(entry.getValue(), context));
            }
            return result;
        }
        if (template instanceof List) {
            JSONArray result = new JSONArray();
            for (Object item : (List<?>) template) result.add(renderValue(item, context));
            return result;
        }
        if (!(template instanceof String)) return template;
        String text = (String) template;
        Matcher exact = PLACEHOLDER.matcher(text);
        if (exact.matches()) return context.get(exact.group(1));
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            Object value = context.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Map<String, String> renderHeaders(Map<String, String> templates, Map<String, Object> context) {
        if (templates == null || templates.isEmpty()) return new LinkedHashMap<>();
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : templates.entrySet()) {
            Object rendered = renderValue(entry.getValue(), context);
            headers.put(entry.getKey(), rendered == null ? "" : String.valueOf(rendered));
        }
        return headers;
    }

    private void validateHeaders(Map<String, String> headers) {
        if (headers == null) return;
        for (String name : headers.keySet()) {
            String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
            if (!HEADER_NAME.matcher(name == null ? "" : name).matches()
                    || FORBIDDEN_HEADERS.contains(normalized)
                    || normalized.startsWith("access-control-")) {
                throw new IllegalArgumentException("不允许配置响应 Header: " + name);
            }
        }
    }

    public static class RenderedResponse {
        private final Object body;
        private final Map<String, String> headers;
        private final int httpStatus;

        private RenderedResponse(Object body, Map<String, String> headers, int httpStatus) {
            this.body = body;
            this.headers = Collections.unmodifiableMap(headers);
            this.httpStatus = httpStatus;
        }

        public Object getBody() {
            return body;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public int getHttpStatus() {
            return httpStatus;
        }
    }
}
