package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Lossless checkpoint transport. Raw data, assembled outputs and trace fields can contain the same large report. */
final class ExternalResponseReceipt {
    private static final int LARGE_FIELDS = 128;
    private static final int LARGE_TEXT = 8192;

    static String encode(Object value) {
        if (value == null) return null;
        var encoder = new ExternalResponseReceipt();
        Object tree = encoder.pack(value);
        if (encoder.parts.isEmpty()) return JSON.toJSONString(value, SerializerFeature.WriteMapNullValue);
        String json = JSON.toJSONString(java.util.Arrays.asList(tree, encoder.parts, encoder.references), SerializerFeature.WriteMapNullValue);
        try (var output = new ByteArrayOutputStream()) {
            try (var gzip = new GZIPOutputStream(output)) { gzip.write(json.getBytes(StandardCharsets.UTF_8)); }
            return "r1:" + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException error) { throw new IllegalStateException("无法保存外数回执", error); }
    }

    private final List<Object> parts = new ArrayList<>();
    private final Map<Object,Integer> indexes = new HashMap<>();
    private final List<List<Object>> references = new ArrayList<>();
    private final List<Object> path = new ArrayList<>();

    private Object pack(Object value) {
        if (value instanceof Map<?,?> map && map.size() >= LARGE_FIELDS || value instanceof List<?> list && list.size() >= LARGE_FIELDS
                || value instanceof String text && text.length() >= LARGE_TEXT) {
            int index = indexes.computeIfAbsent(value, key -> {
                parts.add(key);
                return parts.size() - 1;
            });
            references.add(List.of(List.copyOf(path), index));
            return null;
        }
        if (value instanceof Map<?,?> map) {
            JSONObject copy = new JSONObject(true);
            map.forEach((key, item) -> copy.put(String.valueOf(key), child(String.valueOf(key), item)));
            return copy;
        }
        if (value instanceof List<?> list) {
            JSONArray copy = new JSONArray(list.size());
            for (int i = 0; i < list.size(); i++) copy.add(child(i, list.get(i)));
            return copy;
        }
        return value;
    }

    private Object child(Object key, Object value) {
        path.add(key);
        try { return pack(value); } finally { path.remove(path.size() - 1); }
    }

    static Object decode(String stored) {
        if (stored == null || !stored.startsWith("r1:") && !stored.startsWith("gz:")) return JSON.parse(stored);
        try (var in = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(stored.substring(3))))) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (stored.startsWith("gz:")) return JSON.parse(json);
            JSONArray envelope = JSON.parseArray(json);
            Object root = envelope.get(0);
            JSONArray parts = envelope.getJSONArray(1);
            for (Object item : envelope.getJSONArray(2)) {
                JSONArray ref = (JSONArray) item;
                Object value = parts.get(ref.getIntValue(1));
                JSONArray path = ref.getJSONArray(0);
                if (path.isEmpty()) { root = value; continue; }
                Object parent = root;
                for (int i = 0; i < path.size() - 1; i++)
                    parent = parent instanceof JSONObject object ? object.get(path.get(i)) : ((JSONArray) parent).get(path.getIntValue(i));
                int last = path.size() - 1;
                if (parent instanceof JSONObject object) object.put(path.getString(last), value);
                else ((JSONArray) parent).set(path.getIntValue(last), value);
            }
            return root;
        } catch (IOException e) { throw new IllegalStateException("原外数回执损坏，已停止恢复", e); }
    }
}
