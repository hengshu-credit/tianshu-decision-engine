package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.Set;

/** 从统一 Operand JSON 递归收集字段路径。 */
final class OperandDependencyCollector {

    private OperandDependencyCollector() {
    }

    static void collect(JSONObject operand, Set<String> paths) {
        if (operand == null || paths == null) return;
        String kind = operand.getString("kind");
        if ("PATH".equals(kind) || "REFERENCE".equals(kind)) {
            String path = firstText(operand.getString("code"), operand.getString("value"));
            if (path != null) paths.add(path);
            return;
        }
        if ("FUNCTION".equals(kind)) collectArray(operand.getJSONArray("args"), paths);
        else if ("OPERATION".equals(kind)) collectArray(operand.getJSONArray("operands"), paths);
        else if ("ARRAY".equals(kind)) collectArray(operand.getJSONArray("items"), paths);
        else if ("ACCESS".equals(kind)) {
            collect(operand.getJSONObject("target"), paths);
            collect(operand.getJSONObject("accessor"), paths);
        } else if ("CAST".equals(kind)) collect(operand.getJSONObject("operand"), paths);
    }

    private static void collectArray(JSONArray values, Set<String> paths) {
        if (values == null) return;
        for (int i = 0; i < values.size(); i++) collect(values.getJSONObject(i), paths);
    }

    private static String firstText(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first;
        return second == null || second.trim().isEmpty() ? null : second;
    }
}
