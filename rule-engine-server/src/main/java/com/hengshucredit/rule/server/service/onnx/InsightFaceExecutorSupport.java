package com.hengshucredit.rule.server.service.onnx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class InsightFaceExecutorSupport {

    private InsightFaceExecutorSupport() {
    }

    static List<?> firstRow(Map<String, Object> raw) {
        if (raw.isEmpty()) throw new IllegalArgumentException("ONNX 模型没有输出节点");
        Object value = raw.values().iterator().next();
        if (!(value instanceof List) || ((List<?>) value).size() != 1
                || !(((List<?>) value).get(0) instanceof List)) {
            throw new IllegalArgumentException("ONNX 单人脸输出必须为批次大小 1 的二维数组");
        }
        return (List<?>) ((List<?>) value).get(0);
    }

    static Map<String, Object> itemRaw(Map<String, Object> raw,
                                       Map<String, List<Object>> aggregated) {
        Map<String, Object> itemRaw = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof List) || ((List<?>) value).size() != 1) {
                throw new IllegalArgumentException("ONNX 单人脸输出批次必须为 1: " + entry.getKey());
            }
            Object row = ((List<?>) value).get(0);
            itemRaw.put(entry.getKey(), row);
            aggregated.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).add(row);
        }
        return itemRaw;
    }

    static Map<String, Object> output(List<Map<String, Object>> results,
                                      Map<String, List<Object>> rawOutputs) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("results", results);
        output.put("rawOutputs", new LinkedHashMap<>(rawOutputs));
        return output;
    }
}
