package com.hengshucredit.rule.server.derived;

import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 字段 ID 快照，不以编码作为跨进件的关联键。列表内叶子读取为多值集合。 */
public final class HistoryFieldValues {
    private HistoryFieldValues() { }

    /** 按路径是否存在判断；显式 null（包括空父对象）不能被当成需要取数。 */
    public static boolean present(Map<String, Object> values, String path) {
        if (values == null || path == null) return false;
        if (values.containsKey(path)) return true;
        Object current = values;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(part)) return false;
            current = map.get(part);
            if (current == null) return true;
        }
        return true;
    }

    public static void preferAssignedObjectPaths(Map<String, String> resolved,
            Map<String, String> objectPaths, Map<String, Object> values) {
        objectPaths.forEach((key, path) -> {
            if (key.startsWith("DATA_OBJECT:") && present(values, path)) resolved.put(key, path);
        });
    }

    public static Map<String, Object> snapshot(Map<String, String> paths, Map<String, Object> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        paths.forEach((key, path) -> {
            Object value = read(values, path);
            if (value != null) result.put(key, value);
        });
        return result;
    }

    public static Object read(Object values, String path) {
        if (path == null || path.isBlank()) return null;
        if (values instanceof Map<?, ?> map && map.containsKey(path)) return map.get(path);
        return read(values, path.replace("[]", "").split("\\."), 0);
    }

    private static Object read(Object node, String[] path, int index) {
        if (index == path.length) return node;
        if (node instanceof Map<?, ?> map) return read(map.get(path[index]), path, index + 1);
        if (node instanceof List<?> list) {
            if (path[index].matches("[0-9]+")) {
                int position = Integer.parseInt(path[index]);
                return position < list.size() ? read(list.get(position), path, index + 1) : null;
            }
            List<Object> values = new ArrayList<>();
            for (Object item : list) {
                Object value = read(item, path, index);
                if (value instanceof List<?> nested) values.addAll(nested);
                else if (value != null) values.add(value);
            }
            return values;
        }
        return null;
    }

    public static Map<String, String> frozenPaths(ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot) {
        Map<String, String> paths = new LinkedHashMap<>(snapshot.getReferencePaths());
        for (RuleVariable variable : snapshot.getVariables()) {
            paths.put(("CONSTANT".equals(variable.getVarSource()) ? "CONSTANT:" : "VARIABLE:") + variable.getId(),
                    first(variable.getScriptName(), variable.getVarCode()));
        }
        snapshot.getInputFields().forEach(field -> put(paths, field.getRefType(), field.getVarId(), first(field.getScriptName(), field.getFieldName())));
        snapshot.getOutputFields().forEach(field -> put(paths, field.getRefType(), field.getVarId(), first(field.getScriptName(), field.getFieldName())));
        snapshot.getNestedRules().forEach(rule -> {
            rule.getInputFields().forEach(field -> put(paths, field.getRefType(), field.getVarId(), first(field.getScriptName(), field.getFieldName())));
            rule.getOutputFields().forEach(field -> put(paths, field.getRefType(), field.getVarId(), first(field.getScriptName(), field.getFieldName())));
        });
        snapshot.getDataObjectFields().forEach(field -> put(paths, "DATA_OBJECT", field.getId(), first(field.getScriptName(), field.getVarCode())));
        snapshot.getModels().forEach(model -> {
            if (model.getOutputFields() != null) model.getOutputFields().forEach(field -> put(paths, "MODEL_OUTPUT", field.getId(),
                    model.getModelCode() + "." + first(field.getFieldName(), field.getFeatureName())));
        });
        applyAliases(paths, snapshot.getDataObjectFields());
        return paths;
    }

    public static void applyAliases(Map<String, String> paths, List<com.hengshucredit.rule.model.entity.RuleDataObjectField> fields) {
        Map<String, String> original = new LinkedHashMap<>(paths);
        Map<Long, com.hengshucredit.rule.model.entity.RuleDataObjectField> byId = new LinkedHashMap<>();
        fields.forEach(field -> byId.put(field.getId(), field));
        for (var field : fields) {
            String path = aliasPath(field, original, byId, new java.util.HashSet<>());
            if (path != null) paths.put("DATA_OBJECT:" + field.getId(), path);
        }
    }

    private static String aliasPath(com.hengshucredit.rule.model.entity.RuleDataObjectField field,
            Map<String, String> original, Map<Long, com.hengshucredit.rule.model.entity.RuleDataObjectField> fields,
            java.util.Set<Long> visited) {
        if (!visited.add(field.getId())) throw new IllegalArgumentException("数据对象字段父级存在循环");
        if (field.referencesValue()) {
            String variable = original.get("VARIABLE:" + field.getRefVariableId());
            if (variable == null) variable = original.get("CONSTANT:" + field.getRefVariableId());
            if (variable != null) return variable;
        }
        String path = original.get("DATA_OBJECT:" + field.getId());
        var parent = fields.get(field.getParentFieldId());
        if (parent != null) {
            String previousParent = original.get("DATA_OBJECT:" + parent.getId());
            String resolvedParent = aliasPath(parent, original, fields, visited);
            if (path != null && previousParent != null && resolvedParent != null && path.startsWith(previousParent + ".")) {
                return resolvedParent + path.substring(previousParent.length());
            }
        }
        return path;
    }

    private static void put(Map<String, String> paths, String type, Long id, String path) {
        if (type != null && id != null && path != null) paths.putIfAbsent(type + ":" + id, path);
    }

    private static String first(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
