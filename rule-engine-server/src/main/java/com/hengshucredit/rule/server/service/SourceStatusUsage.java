package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 从模型 JSON 中提取显式使用来源状态操作符的稳定引用。 */
final class SourceStatusUsage {

    private SourceStatusUsage() {
    }

    static Set<String> scan(String modelJson) {
        if (modelJson == null || modelJson.trim().isEmpty()) return Collections.emptySet();
        Object root;
        try {
            root = JSON.parse(modelJson);
        } catch (RuntimeException ignored) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        visit(root, result);
        return result;
    }

    private static void visit(Object value, Set<String> result) {
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            String operator = firstText(map.get("operator"), map.get("condOperator"));
            Object leftValue = map.get("leftOperand");
            addReference(operator, leftValue, result);
            Object segments = map.get("segments");
            Object operand = map.get("operand");
            if (segments instanceof Iterable && operand instanceof Map) {
                for (Object segment : (Iterable<?>) segments) {
                    if (segment instanceof Map) {
                        addReference(firstText(((Map<?, ?>) segment).get("operator")), operand, result);
                    }
                }
            }
            for (Object nested : map.values()) visit(nested, result);
        } else if (value instanceof Iterable) {
            for (Object nested : (Iterable<?>) value) visit(nested, result);
        }
    }

    private static void addReference(String operator, Object operand, Set<String> result) {
        if (operator == null || !operator.startsWith("source_") || !(operand instanceof Map)) return;
        Map<?, ?> reference = (Map<?, ?>) operand;
        Object refId = reference.get("refId");
        String refType = firstText(reference.get("refType"));
        if (refId != null && refType != null) {
            result.add(refType.toUpperCase() + ":" + refId);
        }
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }
}
