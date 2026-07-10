package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 在所有执行入口进入变量解析和 QLExpress 前，按声明字段类型绑定参数。 */
@Service
public class ExecutionParameterBinder {

    public Map<String, Object> bindRuleInputs(List<RuleDefinitionInputField> fields,
                                               Map<String, Object> params) {
        Map<String, Object> result = copyMap(params);
        for (RuleDefinitionInputField field : fields == null ? Collections.<RuleDefinitionInputField>emptyList() : fields) {
            bindOne(result, firstText(field.getScriptName(), field.getFieldName()), field.getFieldType());
        }
        return result;
    }

    public Map<String, Object> bindModelInputs(List<RuleModelInputField> fields,
                                                Map<String, Object> params) {
        Map<String, Object> result = copyMap(params);
        for (RuleModelInputField field : fields == null ? Collections.<RuleModelInputField>emptyList() : fields) {
            bindOne(result, firstText(field.getScriptName(), field.getFieldName()), field.getFieldType());
        }
        return result;
    }

    private void bindOne(Map<String, Object> params, String path, String type) {
        if (path == null) return;
        PathValue pathValue = readPath(params, path);
        if (!pathValue.present) return;
        Object value = coerce(path, type, pathValue.value);
        setPath(params, path, value);
        if (path.indexOf('.') >= 0) {
            params.remove(path);
        }
    }

    private Object coerce(String path, String type, Object value) {
        if (value == null || type == null) return value;
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        try {
            switch (normalized) {
                case "INTEGER":
                case "INT":
                    if (value instanceof Integer) return value;
                    return decimal(value).intValueExact();
                case "LONG":
                    if (value instanceof Long) return value;
                    return decimal(value).longValueExact();
                case "DECIMAL":
                    return decimal(value);
                case "NUMBER":
                case "DOUBLE":
                case "FLOAT":
                case "PROBABILITY":
                    if (value instanceof Number && !(value instanceof BigDecimal)) {
                        return ((Number) value).doubleValue();
                    }
                    return decimal(value).doubleValue();
                case "BOOLEAN":
                case "BOOL":
                    return bool(value);
                case "ARRAY":
                case "LIST":
                case "VECTOR":
                    return list(value);
                case "OBJECT":
                case "MAP":
                    return map(value);
                case "STRING":
                case "ENUM":
                    return value instanceof String ? value : String.valueOf(value);
                default:
                    return value;
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("字段[" + path + "]期望类型" + normalized
                    + "，实际值类型" + value.getClass().getSimpleName() + "，值无法转换", e);
        }
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(String.valueOf(value));
        return new BigDecimal(String.valueOf(value).trim());
    }

    private Boolean bool(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) {
            int number = ((Number) value).intValue();
            if (number == 1) return true;
            if (number == 0) return false;
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "1".equals(text)) return true;
        if ("false".equals(text) || "0".equals(text)) return false;
        throw new IllegalArgumentException("invalid boolean");
    }

    private List<Object> list(Object value) {
        if (value instanceof List) return copyList((List<?>) value);
        if (value.getClass().isArray()) {
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) result.add(copyValue(Array.get(value, i)));
            return result;
        }
        if (value instanceof String) {
            return copyList(JSON.parseArray(((String) value).trim()));
        }
        throw new IllegalArgumentException("invalid list");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map) return copyMap((Map<String, Object>) value);
        if (value instanceof String) return copyMap(JSON.parseObject(((String) value).trim()));
        throw new IllegalArgumentException("invalid map");
    }

    private PathValue readPath(Map<String, Object> params, String path) {
        if (params.containsKey(path)) return new PathValue(true, params.get(path));
        Object current = params;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map) || !((Map<?, ?>) current).containsKey(part)) {
                return new PathValue(false, null);
            }
            current = ((Map<?, ?>) current).get(part);
        }
        return new PathValue(true, current);
    }

    @SuppressWarnings("unchecked")
    private void setPath(Map<String, Object> params, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = params;
        for (int i = 0; i < parts.length; i++) {
            if (i == parts.length - 1) {
                current.put(parts[i], value);
            } else {
                Object child = current.get(parts[i]);
                if (!(child instanceof Map)) {
                    child = new LinkedHashMap<String, Object>();
                    current.put(parts[i], child);
                }
                current = (Map<String, Object>) child;
            }
        }
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source != null) {
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                result.put(entry.getKey(), copyValue(entry.getValue()));
            }
        }
        return result;
    }

    private List<Object> copyList(List<?> source) {
        List<Object> result = new ArrayList<>();
        if (source != null) {
            for (Object value : source) result.add(copyValue(value));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object copyValue(Object value) {
        if (value instanceof Map) return copyMap((Map<String, Object>) value);
        if (value instanceof List) return copyList((List<?>) value);
        return value;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    private static class PathValue {
        private final boolean present;
        private final Object value;

        private PathValue(boolean present, Object value) {
            this.present = present;
            this.value = value;
        }
    }
}
