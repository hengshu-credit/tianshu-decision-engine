package com.hengshucredit.rule.server.service.onnx;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

public final class OnnxTaskConfig {

    private final OnnxTaskType taskType;
    private final JSONObject values;
    private final OnnxRuntimeConfig runtimeConfig;

    private OnnxTaskConfig(OnnxTaskType taskType, JSONObject values, OnnxRuntimeConfig runtimeConfig) {
        this.taskType = taskType;
        this.values = values;
        this.runtimeConfig = runtimeConfig;
    }

    public static OnnxTaskConfig parse(String json) {
        JSONObject values;
        try {
            values = json == null || json.trim().isEmpty() ? new JSONObject() : JSON.parseObject(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("ONNX 配置不是有效 JSON", e);
        }
        OnnxTaskType taskType = OnnxTaskType.fromCode(values.getString("onnxTaskType"));
        values.put("onnxTaskType", taskType.name());
        applyDefaults(taskType, values);
        OnnxRuntimeConfig runtimeConfig = OnnxRuntimeConfig.from(values);
        runtimeConfig.applyTo(values);
        return new OnnxTaskConfig(taskType, values, runtimeConfig);
    }

    private static void applyDefaults(OnnxTaskType taskType, JSONObject values) {
        if (taskType == OnnxTaskType.YUNET_FACE_DETECTION) {
            putIfAbsent(values, "confidenceThreshold", 0.8d);
            putIfAbsent(values, "nmsThreshold", 0.3d);
            putIfAbsent(values, "topK", 5000);
            putIfAbsent(values, "minFaceSize", 60);
            return;
        }
        if (taskType == OnnxTaskType.SCRFD_FACE_DETECTION) {
            putIfAbsent(values, "confidenceThreshold", 0.5d);
            putIfAbsent(values, "nmsThreshold", 0.4d);
            putIfAbsent(values, "inputWidth", 640);
            putIfAbsent(values, "inputHeight", 640);
        }
    }

    private static void putIfAbsent(JSONObject values, String key, Object value) {
        if (!values.containsKey(key) || values.get(key) == null) {
            values.put(key, value);
        }
    }

    public OnnxTaskType getTaskType() {
        return taskType;
    }

    public OnnxRuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    public String getString(String key) {
        return values.getString(key);
    }

    public int getInt(String key) {
        Integer value = values.getInteger(key);
        if (value == null) throw new IllegalArgumentException("ONNX 配置缺少整数参数: " + key);
        return value;
    }

    public double getDouble(String key) {
        Double value = values.getDouble(key);
        if (value == null) throw new IllegalArgumentException("ONNX 配置缺少数值参数: " + key);
        return value;
    }

    public JSONObject toJsonObject() {
        return new JSONObject(values);
    }

    public String toJson() {
        return values.toJSONString();
    }
}
