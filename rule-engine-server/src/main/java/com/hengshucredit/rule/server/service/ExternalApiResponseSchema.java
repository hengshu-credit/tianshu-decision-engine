package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 响应样例用于结构识别，整体绑定与逐字段绑定共用同一个结构来源。 */
public final class ExternalApiResponseSchema {
    private ExternalApiResponseSchema() { }

    public static Object sampleValue(JSONObject branch) {
        Object sample = branch.get("sample");
        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, Object> response = new LinkedHashMap<>(); response.put("body", sample); response.put("httpStatus", 200);
        context.put("response", response); context.put("body", sample); context.put("httpStatus", 200);
        if ("VALUE".equals(branch.getString("mode"))) return ExternalApiRequestPlan.evaluate(branch.get("value"), context, Map.of());
        Map<String, Object> result = new LinkedHashMap<>();
        if (branch.getJSONArray("outputFields") != null) {
            for (Object raw : branch.getJSONArray("outputFields")) {
                JSONObject field = JSON.parseObject(JSON.toJSONString(raw));
                ExternalApiRequestPlan.write(result, field.getString("path"), ExternalApiRequestPlan.evaluate(field.get("value"), context, Map.of()));
            }
        }
        return result;
    }

    public static List<Map<String, Object>> catalog(JSONObject execution) {
        Map<String, Map<String, Object>> fields = new LinkedHashMap<>();
        if (execution != null && execution.getJSONArray("responseBranches") != null) {
            for (Object raw : execution.getJSONArray("responseBranches")) {
                JSONObject branch = JSON.parseObject(JSON.toJSONString(raw));
                add(fields, "body", sampleValue(branch));
            }
        }
        return new ArrayList<>(fields.values());
    }

    private static void add(Map<String, Map<String, Object>> fields, String path, Object value) {
        String type = value instanceof Map ? "OBJECT" : value instanceof List ? "LIST" : value instanceof Number ? "NUMBER" : value instanceof Boolean ? "BOOLEAN" : value == null ? "NULL" : "STRING";
        fields.putIfAbsent(path, Map.of("value", path, "label", "组装结果 · " + path, "type", type));
        if (value instanceof Map<?, ?> object) object.forEach((key, child) -> {
            String name = String.valueOf(key);
            String suffix = name.matches("[A-Za-z_][A-Za-z0-9_]*") ? "." + name : "[" + JSON.toJSONString(name) + "]";
            add(fields, path + suffix, child);
        });
        if (value instanceof List<?> items && !items.isEmpty()) add(fields, path + "[0]", items.get(0));
    }
}
