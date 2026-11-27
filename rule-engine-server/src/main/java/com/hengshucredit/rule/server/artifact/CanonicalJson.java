package com.hengshucredit.rule.server.artifact;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.Map;

public final class CanonicalJson {
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .build();
    // 老版本迁移包把模型原件内嵌在一个 Base64 字段中；只扩大该组件入口的读取预算。
    private static final JsonMapper MODEL_COMPONENT_MAPPER = JsonMapper.builder(
            com.fasterxml.jackson.core.JsonFactory.builder().streamReadConstraints(
                    com.fasterxml.jackson.core.StreamReadConstraints.builder().maxStringLength(512 * 1024 * 1024).build()).build())
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    private CanonicalJson() {
    }

    /** Compare JSON documents without changing stored text, literal strings or array order. */
    public static boolean sameDocument(String left, String right) {
        if (java.util.Objects.equals(left, right)) return true;
        if (left == null || right == null) return false;
        var reader = MAPPER.readerFor(com.fasterxml.jackson.databind.JsonNode.class)
                .with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try {
            var parsed = reader.readTree(left);
            return parsed != null && !parsed.isMissingNode() && parsed.equals(reader.readTree(right));
        } catch (IOException invalid) {
            return false;
        }
    }

    public static byte[] writeBytes(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("无法序列化规范 JSON", e);
        }
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("无法序列化规范 JSON", e);
        }
    }

    public static Map<String, Object> readMap(byte[] value) {
        try {
            return MAPPER.readValue(value, new TypeReference<>() { });
        } catch (IOException e) {
            throw new IllegalArgumentException("JSON 格式无效", e);
        }
    }

    public static Map<String, Object> readLegacyModelComponent(byte[] value) {
        try {
            Map<String, Object> row = MODEL_COMPONENT_MAPPER.readValue(value, new TypeReference<>() { });
            if (!(row.get("configuration") instanceof Map<?, ?> cfg)) throw new IllegalArgumentException("模型迁移配置无效");
            Object original = cfg.remove("modelContent");
            Map<String, Object> normalized = readMap(writeBytes(row));
            if (original != null) {
                if (!(original instanceof String)) throw new IllegalArgumentException("模型原件编码无效");
                @SuppressWarnings("unchecked") Map<String, Object> restored = (Map<String, Object>) normalized.get("configuration");
                restored.put("modelContent", original);
            }
            return normalized;
        } catch (IOException e) { throw new IllegalArgumentException("模型迁移 JSON 格式无效", e); }
    }

    public static Map<String,Object> readModelSnapshot(String json) {
        try { return MODEL_COMPONENT_MAPPER.readValue(json, new TypeReference<>() { }); }
        catch (IOException e) { throw new IllegalArgumentException("模型版本快照 JSON 无效", e); }
    }

    /** Runtime snapshots contain arbitrary business maps, including exact decimals and explicit nulls. */
    public static <T> T readSnapshot(String value, Class<T> type) {
        try {
            return MAPPER.readerFor(type)
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .without(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(value);
        } catch (IOException invalid) {
            throw new IllegalArgumentException("执行快照格式无效", invalid);
        }
    }

    public static Map<String, Object> readMap(String value) {
        try {
            return MAPPER.readValue(value, new TypeReference<>() { });
        } catch (IOException e) {
            throw new IllegalArgumentException("JSON 格式无效", e);
        }
    }
}
