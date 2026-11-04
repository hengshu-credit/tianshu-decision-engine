package com.hengshucredit.rule.server.service.onnx;

import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxSequence;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 按文件中的精确节点名和 Tensor 类型执行，不进行业务预处理。 */
public final class GenericOnnxExecutor {
    private static final Set<String> INPUT_TYPES = Set.of(
            "FLOAT", "DOUBLE", "INT8", "INT16", "INT32", "INT64", "UINT8", "BOOL", "STRING");
    private final OnnxRuntimeSessionManager sessionManager;

    public GenericOnnxExecutor(OnnxRuntimeSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public static String fieldType(Map<?, ?> metadata, boolean input) {
        String type = String.valueOf(metadata.get("type"));
        if (input && (!INPUT_TYPES.contains(type) || !(metadata.get("shape") instanceof List<?>))) {
            throw new IllegalArgumentException("通用 ONNX 暂不支持输入类型 " + type
                    + "；支持 FLOAT、DOUBLE、INT8/16/32/64、UINT8、BOOL、STRING Tensor");
        }
        if (metadata.get("shape") instanceof List<?> shape && !shape.isEmpty()) return "LIST";
        return switch (type) {
            case "FLOAT", "DOUBLE", "FLOAT16", "BFLOAT16" -> "DOUBLE";
            case "INT8", "INT16", "INT32", "UINT8" -> "INTEGER";
            case "INT64" -> "LONG";
            case "BOOL" -> "BOOLEAN";
            case "STRING" -> "STRING";
            case "SequenceInfo" -> "LIST";
            default -> "OBJECT";
        };
    }

    public Map<String, Object> execute(byte[] bytes, OnnxRuntimeConfig config, Map<String, Object> params) {
        return sessionManager.withCpuFallback(bytes, config, effective -> executeExact(bytes, effective, params));
    }

    private Map<String, Object> executeExact(byte[] bytes, OnnxRuntimeConfig config, Map<String, Object> params) {
        Map<String, OnnxTensor> tensors = new LinkedHashMap<>();
        try (var lease = sessionManager.acquireSession(bytes, config)) {
            OrtSession session = lease.session();
            if (params == null || !session.getInputNames().equals(params.keySet())) {
                throw new IllegalArgumentException("ONNX 输入字段必须与模型完全一致，需要：" + session.getInputNames());
            }
            for (var entry : session.getInputInfo().entrySet()) {
                if (!(entry.getValue().getInfo() instanceof TensorInfo info)) {
                    throw new IllegalArgumentException("ONNX 输入 " + entry.getKey() + " 必须为 Tensor");
                }
                tensors.put(entry.getKey(), tensor(entry.getKey(), info, params.get(entry.getKey())));
            }
            try (var result = session.run(tensors)) {
                Map<String, Object> outputs = new LinkedHashMap<>();
                for (var entry : result) {
                    outputs.put(entry.getKey(), output(entry.getValue()));
                }
                return outputs;
            }
        } catch (OrtException e) {
            throw new IllegalArgumentException("ONNX 推理失败: " + e.getMessage(), e);
        } finally {
            tensors.values().forEach(OnnxTensor::close);
        }
    }

    private OnnxTensor tensor(String name, TensorInfo info, Object value) throws OrtException {
        if (!INPUT_TYPES.contains(info.type.name())) {
            throw new IllegalArgumentException("ONNX 输入 " + name + " 暂不支持类型 " + info.type);
        }
        long[] shape = info.getShape().clone();
        List<Object> flat = new ArrayList<>();
        flatten(name, value, shape, 0, flat);
        if (info.type == OnnxJavaType.STRING) {
            String[] data = new String[flat.size()];
            for (int i = 0; i < data.length; i++) {
                if (!(flat.get(i) instanceof String text)) throw invalidType(name, info.type);
                data[i] = text;
            }
            return OnnxTensor.createTensor(sessionManager.getEnvironment(), data, shape);
        }
        ByteBuffer data = ByteBuffer.allocateDirect(Math.multiplyExact(flat.size(), info.type.size))
                .order(ByteOrder.nativeOrder());
        for (Object item : flat) {
            if (info.type == OnnxJavaType.BOOL) {
                if (!(item instanceof Boolean flag)) throw invalidType(name, info.type);
                data.put((byte) (flag ? 1 : 0));
                continue;
            }
            if (!(item instanceof Number number)) throw invalidType(name, info.type);
            try {
                switch (info.type) {
                    case FLOAT -> {
                        float converted = number.floatValue();
                        if (!Float.isFinite(converted)) throw new ArithmeticException();
                        data.putFloat(converted);
                    }
                    case DOUBLE -> {
                        double converted = number.doubleValue();
                        if (!Double.isFinite(converted)) throw new ArithmeticException();
                        data.putDouble(converted);
                    }
                    case INT64 -> data.putLong(new BigDecimal(number.toString()).longValueExact());
                    case INT32 -> data.putInt(new BigDecimal(number.toString()).intValueExact());
                    case INT16 -> data.putShort(new BigDecimal(number.toString()).shortValueExact());
                    case INT8 -> data.put(new BigDecimal(number.toString()).byteValueExact());
                    case UINT8 -> {
                        int converted = new BigDecimal(number.toString()).intValueExact();
                        if (converted < 0 || converted > 255) throw new ArithmeticException();
                        data.put((byte) converted);
                    }
                    default -> throw invalidType(name, info.type);
                }
            } catch (ArithmeticException | NumberFormatException e) {
                throw new IllegalArgumentException("ONNX 输入 " + name + " 含超出 " + info.type + " 范围或非整数的数值", e);
            }
        }
        data.flip();
        // ByteBuffer 重载按指定类型读取，避免将整数 JSON 错建为 INT64 或将 BOOL 当作 INT8。
        return OnnxTensor.createTensor(sessionManager.getEnvironment(), data, shape, info.type);
    }

    private void flatten(String name, Object value, long[] shape, int depth, List<Object> flat) {
        if (depth == shape.length) {
            if (value == null || value instanceof List<?> || value instanceof Map<?, ?>) {
                throw new IllegalArgumentException("ONNX 输入 " + name + " 需要标量元素");
            }
            flat.add(value);
            return;
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("ONNX 输入 " + name + " 需要 " + shape.length + " 维数组");
        }
        if (shape[depth] < 0) shape[depth] = list.size();
        if (shape[depth] != list.size()) {
            throw new IllegalArgumentException("ONNX 输入 " + name + " 第 " + (depth + 1)
                    + " 维长度不符，需要 " + shape[depth] + "，实际 " + list.size());
        }
        if (list.isEmpty() && Arrays.stream(shape).anyMatch(dimension -> dimension < 0)) {
            throw new IllegalArgumentException("ONNX 输入 " + name + " 空数组无法确定后续动态维度");
        }
        for (Object item : list) flatten(name, item, shape, depth + 1, flat);
    }

    private IllegalArgumentException invalidType(String name, OnnxJavaType type) {
        return new IllegalArgumentException("ONNX 输入 " + name + " 元素必须符合 " + type + " 类型");
    }

    private Object unsignedBytes(Object value) {
        if (value instanceof List<?> list) return list.stream().map(this::unsignedBytes).toList();
        return Byte.toUnsignedInt((Byte) value);
    }

    private Object output(OnnxValue value) throws OrtException {
        if (value instanceof OnnxSequence sequence) {
            List<? extends OnnxValue> children = sequence.getValue();
            try {
                List<Object> result = new ArrayList<>(children.size());
                for (OnnxValue child : children) result.add(output(child));
                return result;
            } finally {
                children.forEach(OnnxValue::close);
            }
        }
        Object result = OnnxValueConverter.toJava(value.getValue());
        return value instanceof OnnxTensor tensor && tensor.getInfo().type == OnnxJavaType.UINT8
                ? unsignedBytes(result) : result;
    }
}
