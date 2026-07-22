package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.dto.ResolutionPlan;
import com.hengshucredit.rule.model.dto.ResolvedField;
import com.hengshucredit.rule.model.dto.RuleTestSchema;
import com.hengshucredit.rule.model.dto.RuleTestSchemaRequest;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 根据统一依赖计划生成测试字段和嵌套样例参数。 */
@Service
public class RuleTestSchemaService {

    @Resource
    private FieldDependencyResolver fieldDependencyResolver;

    public RuleTestSchema build(RuleTestSchemaRequest request) {
        return build(fieldDependencyResolver.resolve(request));
    }

    public RuleTestSchema build(ResolutionPlan plan) {
        RuleTestSchema schema = new RuleTestSchema();
        schema.setInputs(new ArrayList<>(plan.getExternalInputs()));
        schema.setRuntimeNodes(new ArrayList<>(plan.getRuntimeNodes()));
        schema.setOutputs(new ArrayList<>(plan.getOutputs()));
        schema.setDiagnostics(new ArrayList<>(plan.getDiagnostics()));
        Map<String, Object> params = new LinkedHashMap<>();
        for (ResolvedField field : plan.getExternalInputs()) {
            String path = firstText(field.getScriptName(), field.getCode());
            if (path != null) {
                String conflict = setPathValue(params, path, sampleValue(field));
                if (conflict != null) schema.getDiagnostics().add(conflict);
            }
        }
        schema.setSampleParams(params);
        return schema;
    }

    private Object sampleValue(ResolvedField field) {
        String type = field.getValueType() == null ? "STRING" : field.getValueType().toUpperCase(Locale.ROOT);
        if (field.getExampleValue() != null && !field.getExampleValue().isEmpty()) {
            return parseConfiguredValue(field.getExampleValue(), type);
        }
        if (field.getDefaultValue() != null && !field.getDefaultValue().isEmpty()) {
            return parseConfiguredValue(field.getDefaultValue(), type);
        }
        if ("INTEGER".equals(type) || "INT".equals(type) || "LONG".equals(type)) return 0;
        if ("NUMBER".equals(type) || "DOUBLE".equals(type) || "FLOAT".equals(type)
                || "DECIMAL".equals(type) || "PROBABILITY".equals(type)) return 0d;
        if ("BOOLEAN".equals(type) || "BOOL".equals(type)) return false;
        if ("ARRAY".equals(type) || "LIST".equals(type) || "VECTOR".equals(type)) return new ArrayList<>();
        if ("OBJECT".equals(type) || "MAP".equals(type)) return new LinkedHashMap<>();
        return "";
    }

    private Object parseConfiguredValue(String value, String type) {
        String raw = value == null ? "" : value.trim();
        if ("null".equalsIgnoreCase(raw)) return null;
        if ("\"\"".equals(raw) || "''".equals(raw)) return "";
        try {
            if ("INTEGER".equals(type) || "INT".equals(type)) return Integer.valueOf(raw);
            if ("LONG".equals(type)) return Long.valueOf(raw);
            if ("NUMBER".equals(type) || "DOUBLE".equals(type) || "FLOAT".equals(type)
                    || "DECIMAL".equals(type) || "PROBABILITY".equals(type)) {
                if ("Infinity".equals(raw)) return Double.POSITIVE_INFINITY;
                if ("-Infinity".equals(raw)) return Double.NEGATIVE_INFINITY;
                return Double.valueOf(raw);
            }
            if ("BOOLEAN".equals(type) || "BOOL".equals(type)) return Boolean.valueOf(raw);
            if ("ARRAY".equals(type) || "LIST".equals(type) || "VECTOR".equals(type)) {
                Object parsed = JSON.parse(raw);
                return parsed instanceof JSONArray ? parsed : raw;
            }
            if ("OBJECT".equals(type) || "MAP".equals(type)) {
                Object parsed = JSON.parse(raw);
                return parsed instanceof JSONObject ? parsed : raw;
            }
        } catch (Exception ignored) {
            return stripQuotedString(raw);
        }
        return stripQuotedString(raw);
    }

    private String stripQuotedString(String raw) {
        if (raw == null) return "";
        if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    @SuppressWarnings("unchecked")
    private String setPathValue(Map<String, Object> target, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> probe = target;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;
            Object child = probe.get(part);
            if (child == null) break;
            if (!(child instanceof Map)) {
                return "测试参数路径冲突[" + path + "]: 父路径[" + part + "]已经是普通值";
            }
            probe = (Map<String, Object>) child;
        }
        Map<String, Object> current = target;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;
            if (i == parts.length - 1) {
                Object existing = current.get(part);
                if (existing != null && (existing instanceof Map) != (value instanceof Map)) {
                    return "测试参数路径冲突[" + path + "]: 同一路径同时声明为对象和普通值";
                }
                current.put(part, value);
            } else {
                Object child = current.get(part);
                if (!(child instanceof Map)) {
                    child = new LinkedHashMap<String, Object>();
                    current.put(part, child);
                }
                current = (Map<String, Object>) child;
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }
}
