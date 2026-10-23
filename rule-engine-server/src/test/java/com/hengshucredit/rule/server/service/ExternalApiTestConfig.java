package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import java.util.List;
import java.util.Map;

final class ExternalApiTestConfig {
    private ExternalApiTestConfig() { }
    static final String EMPTY = "{\"version\":2,\"requestFields\":[]}";
    static RuleExternalApiConfig api() {
        RuleExternalApiConfig api = new RuleExternalApiConfig();
        api.setExecutionConfig(EMPTY);
        return api;
    }
    static Map<String, Object> literal(Object value) {
        return Map.of("kind", "LITERAL", "valueType", value instanceof Number ? "NUMBER" : value instanceof Boolean ? "BOOLEAN" : "STRING", "value", value);
    }
    static Map<String, Object> reference(long id, String code) {
        return Map.of("kind", "REFERENCE", "refType", "VARIABLE", "refId", id, "code", code);
    }
    static Map<String, Object> path(String value) { return Map.of("kind", "PATH", "value", value, "protocolPath", true); }
    static Map<String, Object> field(String id, String location, String name, Object operand) {
        return Map.of("id", id, "location", location, "path", name, "value", operand);
    }
    static String request(List<Map<String, Object>> fields) {
        return CanonicalJson.write(Map.of("version", 2, "requestFields", fields));
    }
    static void response(RuleExternalApiConfig api, Map<String, Object> fields) {
        branches(api, List.of(Map.of("id", "result", "mode", "FIELDS", "outputFields", fields.entrySet().stream()
                .map(entry -> Map.of("id", entry.getKey(), "path", entry.getKey(), "value", entry.getValue())).toList())));
    }
    static void branches(RuleExternalApiConfig api, List<Map<String, Object>> branches) {
        var config = com.alibaba.fastjson.JSON.parseObject(api.getExecutionConfig());
        config.put("responseBranches", branches);
        api.setExecutionConfig(CanonicalJson.write(config));
    }
    static Map<String, Object> nvl(Object value, Object fallback) {
        return Map.of("kind", "FUNCTION", "functionCode", "nvl", "args", List.of(value, fallback));
    }
    static Map<String, Object> responseBranch(String id, Object condition, String field, Object value) {
        return Map.of("id", id, "mode", "FIELDS", "condition", condition, "outputFields", List.of(Map.of("path", field, "value", value)));
    }
    static String polling(String submit, String query, String statusPath, String success, int attempts) {
        return CanonicalJson.write(Map.of("version", 2, "requestFields", List.of(), "steps", List.of(
                Map.of("id", "submit", "type", "HTTP", "endpointUrl", submit, "requestMethod", "POST", "requestFields", List.of()),
                Map.of("id", "poll", "type", "HTTP", "endpointUrl", query, "requestMethod", "GET", "requestFields", List.of(),
                        "poll", Map.of("intervalMs", 1, "maxAttempts", attempts, "until", Map.of("path", statusPath, "operator", "==", "value", success))))));
    }
    static String pollingResult(String submit, String query, String statusPath, String success, int attempts, int interval, String resultPath) {
        var config = com.alibaba.fastjson.JSON.parseObject(polling(submit, query, statusPath, success, attempts));
        var step = config.getJSONArray("steps").getJSONObject(1);
        step.put("retryCount", 1);
        step.getJSONObject("poll").put("intervalMs", interval);
        if (resultPath != null) config.put("responseBranches", List.of(Map.of("id", "result", "mode", "VALUE", "value", path(resultPath))));
        return CanonicalJson.write(config);
    }
}
