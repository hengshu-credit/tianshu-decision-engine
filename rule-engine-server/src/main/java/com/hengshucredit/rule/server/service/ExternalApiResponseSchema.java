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
        return sampleValue(branch, branch.get("sample"));
    }

    private static Object sampleValue(JSONObject branch, Object sample) {
        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, Object> response = new LinkedHashMap<>(); response.put("body", sample); response.put("httpStatus", 200);
        context.put("response", response); context.put("body", sample); context.put("httpStatus", 200);
        if ("VALUE".equals(branch.getString("mode"))) {
            Object value = ExternalApiRequestPlan.evaluate(branch.get("value"), context, Map.of());
            String outputPath = branch.getString("outputPath");
            if (outputPath == null || outputPath.isBlank()) return value;
            Map<String, Object> mapped = new LinkedHashMap<>();
            ExternalApiRequestPlan.write(mapped, outputPath, value);
            return mapped;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (branch.getJSONArray("outputFields") != null) {
            for (Object raw : branch.getJSONArray("outputFields")) {
                JSONObject field = JSON.parseObject(JSON.toJSONString(raw));
                ExternalApiRequestPlan.write(result, field.getString("path"), ExternalApiRequestPlan.evaluate(field.get("value"), context, Map.of()));
            }
        }
        return result;
    }

    /** 对象变量形状分析与字段选择器使用相同的样例和组装路径。 */
    static List<Map<String, Object>> sampleEnvelopes(JSONObject execution) {
        List<Map<String, Object>> samples = new ArrayList<>();
        var steps = execution.getJSONArray("steps");
        if (execution.containsKey("responseSample") || hasResponseBranches(execution) || steps == null) {
            collectSampleEnvelopes(samples, execution);
        } else {
            for (int i = steps.size() - 1; i >= 0; i--) {
                JSONObject step = steps.getJSONObject(i);
                collectSampleEnvelopes(samples, step);
                if (!ExternalApiExecutionConfig.hasCondition(step.get("when"))) break;
            }
        }
        return samples;
    }

    private static void collectSampleEnvelopes(List<Map<String, Object>> samples, JSONObject owner) {
        if (!hasResponseBranches(owner)) {
            if (owner.containsKey("responseSample")) samples.add(sampleEnvelope(owner.get("responseSample"), owner.get("responseSample")));
            return;
        }
        var branches = owner.getJSONArray("responseBranches");
        for (int i = 0; i < branches.size(); i++) {
            JSONObject branch = branches.getJSONObject(i);
            Object raw = branch.getOrDefault("sample", owner.get("responseSample"));
            samples.add(sampleEnvelope(raw, sampleValue(branch, raw)));
        }
    }

    private static Map<String, Object> sampleEnvelope(Object raw, Object assembled) {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("response", java.util.Collections.singletonMap("body", raw));
        sample.put("body", assembled);
        return sample;
    }

    public static List<Map<String, Object>> catalog(JSONObject execution) {
        Map<String, Map<String, Object>> fields = new LinkedHashMap<>();
        catalogBranches(fields, execution, "");
        if (execution == null) return new ArrayList<>(fields.values());
        var steps = execution.getJSONArray("steps");
        boolean finalFromStep = !execution.containsKey("responseSample") && !hasResponseBranches(execution);
        for (int index = steps == null ? -1 : steps.size() - 1; index >= 0; index--) {
            JSONObject step = steps.getJSONObject(index);
            catalogBranches(fields, step, "steps." + step.getString("id") + ".");
            if (finalFromStep) {
                catalogBranches(fields, step, "");
                finalFromStep = ExternalApiExecutionConfig.hasCondition(step.get("when"));
            }
        }
        return new ArrayList<>(fields.values());
    }

    public static List<Map<String, Object>> catalog(com.hengshucredit.rule.model.entity.RuleExternalApiConfig api) {
        return catalog(ExternalApiRequestPlan.specification(api));
    }

    private static boolean hasResponseBranches(JSONObject execution) {
        return execution.getJSONArray("responseBranches") != null && !execution.getJSONArray("responseBranches").isEmpty();
    }

    private static void catalogBranches(Map<String, Map<String, Object>> fields, JSONObject execution, String prefix) {
        if (execution == null) return;
        if (execution.containsKey("responseSample")) {
            add(fields, prefix + "response.body", execution.get("responseSample"));
            if (!hasResponseBranches(execution)) add(fields, prefix + "body", execution.get("responseSample"));
        }
        if (!hasResponseBranches(execution)) return;
        var branches = execution.getJSONArray("responseBranches");
        for (int i = 0; i < branches.size(); i++) {
            JSONObject branch = branches.getJSONObject(i);
            Object sample = branch.getOrDefault("sample", execution.get("responseSample"));
            if (branch.containsKey("sample")) add(fields, prefix + "response.body", branch.get("sample"));
            if ("VALUE".equals(branch.getString("mode"))) {
                String outputPath = branch.getString("outputPath");
                addCatalogValue(fields, prefix + "body" + (outputPath == null || outputPath.isBlank() ? "" : (outputPath.startsWith("[") ? "" : ".") + outputPath), branch.getJSONObject("value"), sample);
            } else if (branch.getJSONArray("outputFields") != null) for (Object entry : branch.getJSONArray("outputFields")) {
                JSONObject field = JSON.parseObject(JSON.toJSONString(entry));
                String path = field.getString("path");
                if (path == null || path.isBlank()) continue;
                addCatalogValue(fields, prefix + "body" + (path.startsWith("[") ? "" : ".") + path, field.getJSONObject("value"), sample);
            }
        }
    }

    private static void addCatalogValue(Map<String, Map<String, Object>> fields, String path, JSONObject operand, Object rawSample) {
        // 目录读取不能执行函数、固定版本调用或外数，仅从样例的静态路径识别结构。
        if (operand != null && "PATH".equals(operand.getString("kind")) && operand.get("refId") == null && rawSample != null) {
            Map<String, Object> response = new LinkedHashMap<>(); response.put("body", rawSample); response.put("httpStatus", 200);
            Map<String, Object> context = new LinkedHashMap<>(); context.put("response", response); context.put("body", rawSample);
            Object sample = ExternalApiRequestPlan.read(context, operand.getString("value"));
            if (sample != null) { add(fields, path, sample); return; }
        }
        String type = operand == null || operand.getString("valueType") == null ? "OBJECT" : operand.getString("valueType");
        fields.putIfAbsent(path, Map.of("value", path, "label", (path.startsWith("response.") || path.contains(".response.") ? "原始响应 · " : "组装结果 · ") + path, "type", type));
    }

    private static void add(Map<String, Map<String, Object>> fields, String path, Object value) {
        String type = value instanceof Map ? "OBJECT" : value instanceof List ? "LIST" : value instanceof Number ? "NUMBER" : value instanceof Boolean ? "BOOLEAN" : value == null ? "NULL" : "STRING";
        fields.putIfAbsent(path, Map.of("value", path, "label", (path.startsWith("response.") || path.contains(".response.") ? "原始响应 · " : "组装结果 · ") + path, "type", type));
        if (value instanceof Map<?, ?> object) object.forEach((key, child) -> {
            String name = String.valueOf(key);
            String suffix = name.matches("[A-Za-z_][A-Za-z0-9_]*") ? "." + name : "[" + JSON.toJSONString(name) + "]";
            add(fields, path + suffix, child);
        });
        if (value instanceof List<?> items && !items.isEmpty()) add(fields, path + "[0]", items.get(0));
    }
}
