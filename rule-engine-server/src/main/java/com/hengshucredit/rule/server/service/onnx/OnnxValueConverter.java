package com.hengshucredit.rule.server.service.onnx;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OnnxValueConverter {

    private OnnxValueConverter() {
    }

    public static Object toJava(Object value) {
        if (value == null) return null;
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                result.add(toJava(Array.get(value, i)));
            }
            return result;
        }
        if (value instanceof Iterable) {
            List<Object> result = new ArrayList<>();
            for (Object item : (Iterable<?>) value) result.add(toJava(item));
            return result;
        }
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                result.put(String.valueOf(entry.getKey()), toJava(entry.getValue()));
            }
            return result;
        }
        return value;
    }

    public static List<Long> shape(long[] shape) {
        List<Long> result = new ArrayList<>();
        if (shape == null) return result;
        for (long dimension : shape) result.add(dimension);
        return result;
    }
}
