package com.hengshucredit.rule.server.transfer;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.Sha256Digests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 离线包跨环境比较字段结构；只输出明确的 ID 映射，不将路径保存为业务引用。 */
final class TransferObjectFieldIndex {
    private final Map<Long, String> paths;
    private final Map<String, Long> idsByPath;
    private final List<Map<String, Object>> sortedFields;

    private TransferObjectFieldIndex(Map<Long, String> paths, Map<String, Long> idsByPath,
                                     List<Map<String, Object>> sortedFields) {
        this.paths = Map.copyOf(paths);
        this.idsByPath = Map.copyOf(idsByPath);
        this.sortedFields = List.copyOf(sortedFields);
    }

    static TransferObjectFieldIndex of(Map<String, Object> configuration) {
        Object raw = configuration.get("fields");
        if (raw == null) return new TransferObjectFieldIndex(Map.of(), Map.of(), List.of());
        if (!(raw instanceof List<?> values)) throw new IllegalArgumentException("数据对象字段必须为数组");
        Map<Long, Map<String, Object>> fields = new LinkedHashMap<>();
        Map<Long, Long> parents = new HashMap<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("数据对象字段配置无效");
            @SuppressWarnings("unchecked") Map<String, Object> field = (Map<String, Object>) value;
            Long id = id(field.get("id"));
            if (id == null) throw new IllegalArgumentException("数据对象字段缺少 ID，无法建立引用映射");
            if (fields.putIfAbsent(id, field) != null) throw new IllegalArgumentException("数据对象字段 ID 重复: " + id);
            if (!(field.get("varCode") instanceof String code) || code.isBlank()
                    || !(field.get("varType") instanceof String type) || type.isBlank()
                    || (field.get("genericType") != null && !(field.get("genericType") instanceof String))) {
                throw new IllegalArgumentException("数据对象字段编码或类型无效，ID=" + id);
            }
            parents.put(id, id(field.get("parentFieldId")));
        }
        Map<Long, String> paths = new LinkedHashMap<>();
        Map<String, Long> idsByPath = new TreeMap<>();
        for (Long fieldId : fields.keySet()) {
            List<Long> pending = new ArrayList<>();
            var visiting = new HashSet<Long>();
            Long current = fieldId;
            while (current != null && !paths.containsKey(current)) {
                if (!fields.containsKey(current)) throw new IllegalArgumentException("数据对象字段引用的父字段不存在: " + current);
                if (!visiting.add(current)) throw new IllegalArgumentException("数据对象字段父子引用存在循环: " + current);
                pending.add(current);
                current = parents.get(current);
            }
            String parentPath = current == null ? null : paths.get(current);
            for (int i = pending.size() - 1; i >= 0; i--) {
                Long id = pending.get(i);
                Map<String, Object> field = fields.get(id);
                // 只保留父结构摘要，避免扁平字段数组中的深链导致完整祖先路径占用平方级内存。
                List<Object> segment = new ArrayList<>(4);
                segment.add(parentPath);
                segment.add(field.get("varCode"));
                segment.add(field.get("varType"));
                segment.add(field.get("genericType"));
                String path = Sha256Digests.text(CanonicalJson.write(segment));
                if (idsByPath.putIfAbsent(path, id) != null) {
                    throw new IllegalArgumentException("数据对象字段结构路径重复，无法建立唯一引用映射，ID=" + id);
                }
                paths.put(id, path);
                parentPath = path;
            }
        }
        return new TransferObjectFieldIndex(paths, idsByPath,
                idsByPath.values().stream().map(fields::get).toList());
    }

    Map<Long, Long> matchingIds(TransferObjectFieldIndex target) {
        Map<Long, Long> result = new LinkedHashMap<>();
        paths.forEach((sourceId, path) -> {
            Long targetId = target.idsByPath.get(path);
            if (targetId != null) result.put(sourceId, targetId);
        });
        return Map.copyOf(result);
    }

    String path(Long id) {
        String path = paths.get(id);
        if (path == null) throw new IllegalArgumentException("数据对象字段不存在: " + id);
        return path;
    }

    List<Map<String, Object>> sortedFields() { return sortedFields; }

    private static Long id(Object value) {
        if (value == null) return null;
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("数据对象字段 ID 无效: " + value, exception); }
    }
}
