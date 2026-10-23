package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 鉴权与脚本变量使用同一操作数契约，字符串仅用于协议字段名和地址。 */
public final class ExternalApiAuthConfig {
    private ExternalApiAuthConfig() { }

    public static JSONObject inputs(String text) {
        JSONObject source = text == null || text.isBlank() ? new JSONObject() : JSON.parseObject(text);
        JSONObject result = new JSONObject(true);
        for (String field : List.of("username", "password", "token", "value", "headers", "body", "scriptVariables")) {
            if (source.containsKey(field)) result.put(field, source.get(field));
        }
        return result;
    }

    public static void validate(String text, Map<String, String> paths) {
        JSONObject values = inputs(text);
        for (String field : List.of("username", "password", "token", "value")) {
            if (values.get(field) != null) operand(values.get(field), paths);
        }
        for (String field : List.of("headers", "body")) if (values.get(field) != null) object(values.get(field), paths);
        if (values.get("scriptVariables") instanceof Iterable<?> rows) for (Object row : rows) {
            if (row instanceof Map<?, ?> value) operand(value.get("value"), paths);
        }
    }

    private static void object(Object node, Map<String, String> paths) {
        if (node instanceof Map<?, ?> map && !(map.get("kind") instanceof String)) map.values().forEach(value -> object(value, paths));
        else operand(node, paths);
    }

    private static void operand(Object raw, Map<String, String> paths) {
        if (!(raw instanceof Map<?, ?> value) || !Set.of("LITERAL", "REFERENCE", "PATH", "FUNCTION", "OPERATION", "ACCESS", "CAST", "ARRAY").contains(String.valueOf(value.get("kind")))) {
            throw new IllegalArgumentException("鉴权取值必须通过内容选择器配置字段、固定值或表达式");
        }
        JSONObject node = JSON.parseObject(JSON.toJSONString(raw));
        if (paths != null) ExternalApiExecutionConfig.bindReferences(node, paths);
    }

    public static Map<String, Object> resolveVariables(String text, Map<String, Object> input, Map<String, String> paths) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (inputs(text).get("scriptVariables") instanceof Iterable<?> rows) for (Object raw : rows) {
            JSONObject row = JSON.parseObject(JSON.toJSONString(raw));
            String name = row.getString("name");
            if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*") || result.containsKey(name)) throw new IllegalArgumentException("脚本变量名为空、重复或格式错误");
            result.put(name, ExternalApiRequestPlan.evaluate(row.get("value"), input, paths));
        }
        return result;
    }
}
