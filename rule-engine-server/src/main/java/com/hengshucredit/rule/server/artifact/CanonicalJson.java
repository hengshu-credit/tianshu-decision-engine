package com.hengshucredit.rule.server.artifact;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

public final class CanonicalJson {
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .build();

    private CanonicalJson() {
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

    public static Map<String, Object> readMap(String value) {
        try {
            return MAPPER.readValue(value, new TypeReference<>() { });
        } catch (IOException e) {
            throw new IllegalArgumentException("JSON 格式无效", e);
        }
    }
}
