package com.hengshucredit.rule.model;

import java.util.Map;

/** HTTP 与 SDK 运行入口共用的规则执行请求。业务字段原样保留在 params。 */
public class RuleExecutionRequest {
    private Map<String, Object> params;
    private String clientAppName;
    private Boolean traceEnabled;

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
    public String getClientAppName() { return clientAppName; }
    public void setClientAppName(String clientAppName) { this.clientAppName = clientAppName; }
    public Boolean getTraceEnabled() { return traceEnabled; }
    public void setTraceEnabled(Boolean traceEnabled) { this.traceEnabled = traceEnabled; }

    public static RuleExecutionRequest fromBody(Map<String, Object> body) {
        if (body == null || body.get("params") == null) {
            throw new PlatformException(PlatformCode.REQUIRED_FIELD_MISSING, "params 必须提供，可使用空对象");
        }
        if (!(body.get("params") instanceof Map<?, ?> raw)) {
            throw new PlatformException(PlatformCode.PARAMETER_INVALID, "params 必须为对象");
        }
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new PlatformException(PlatformCode.PARAMETER_INVALID, "params 字段名必须为字符串");
            params.put(key, entry.getValue());
        }
        Object trace = body.get("traceEnabled");
        if (trace != null && !(trace instanceof Boolean)
                && !(trace instanceof String text && ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)))) {
            throw new PlatformException(PlatformCode.PARAMETER_INVALID, "traceEnabled 必须为 true 或 false");
        }
        RuleExecutionRequest request = new RuleExecutionRequest();
        request.setParams(params);
        request.setTraceEnabled(trace == null ? null : Boolean.valueOf(trace.toString()));
        request.setClientAppName(body.get("clientAppName") == null ? null : body.get("clientAppName").toString());
        return request;
    }
}
