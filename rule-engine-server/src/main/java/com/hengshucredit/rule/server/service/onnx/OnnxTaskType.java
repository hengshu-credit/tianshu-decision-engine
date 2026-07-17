package com.hengshucredit.rule.server.service.onnx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public enum OnnxTaskType {

    YUNET_FACE_DETECTION(
            fields(field("image", "图片 Base64", "STRING")),
            fields(field("faces", "人脸列表", "LIST"))),
    FACENOX_ANTISPOOF(
            fields(field("image", "图片 Base64", "STRING"), field("faces", "人脸列表", "LIST")),
            fields(field("results", "逐人脸 logits", "LIST"), field("rawOutputs", "原始节点输出", "OBJECT"))),
    MN3_ANTISPOOF(
            fields(field("image", "图片 Base64", "STRING"), field("faces", "人脸列表", "LIST")),
            fields(field("results", "逐人脸 probs", "LIST"), field("rawOutputs", "原始节点输出", "OBJECT"))),
    SCRFD_FACE_DETECTION(
            fields(field("image", "图片 Base64", "STRING")),
            fields(field("faces", "人脸列表", "LIST"), field("rawOutputs", "原始节点输出", "OBJECT"))),
    ARCFACE_RECOGNITION(
            fields(field("image", "图片 Base64", "STRING"), field("faces", "人脸列表", "LIST")),
            fields(field("results", "逐人脸特征向量", "LIST"), field("rawOutputs", "原始节点输出", "OBJECT"))),
    LANDMARK_2D106(
            fields(field("image", "图片 Base64", "STRING"), field("faces", "人脸列表", "LIST")),
            fields(field("results", "逐人脸二维关键点", "LIST"), field("rawOutputs", "原始节点输出", "OBJECT"))),
    LANDMARK_3D68(
            fields(field("image", "图片 Base64", "STRING"), field("faces", "人脸列表", "LIST")),
            fields(field("results", "逐人脸三维关键点", "LIST"), field("rawOutputs", "原始节点输出", "OBJECT"))),
    GENDER_AGE(
            fields(field("image", "图片 Base64", "STRING"), field("faces", "人脸列表", "LIST")),
            fields(field("results", "逐人脸性别年龄", "LIST"), field("rawOutputs", "原始节点输出", "OBJECT")));

    private final List<FieldSpec> inputs;
    private final List<FieldSpec> outputs;

    OnnxTaskType(List<FieldSpec> inputs, List<FieldSpec> outputs) {
        this.inputs = Collections.unmodifiableList(inputs);
        this.outputs = Collections.unmodifiableList(outputs);
    }

    public List<FieldSpec> getInputs() {
        return inputs;
    }

    public List<FieldSpec> getOutputs() {
        return outputs;
    }

    public List<String> inputNames() {
        return inputs.stream().map(FieldSpec::getName).collect(Collectors.toList());
    }

    public List<String> outputNames() {
        return outputs.stream().map(FieldSpec::getName).collect(Collectors.toList());
    }

    public static OnnxTaskType fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("ONNX 任务类型不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的 ONNX 任务类型: " + code);
        }
    }

    private static FieldSpec field(String name, String label, String type) {
        return new FieldSpec(name, label, type);
    }

    private static List<FieldSpec> fields(FieldSpec... fields) {
        return new ArrayList<>(Arrays.asList(fields));
    }

    public static final class FieldSpec {
        private final String name;
        private final String label;
        private final String type;

        private FieldSpec(String name, String label, String type) {
            this.name = name;
            this.label = label;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public String getLabel() {
            return label;
        }

        public String getType() {
            return type;
        }
    }
}
