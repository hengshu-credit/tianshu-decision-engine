package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 从统一 Operand JSON 递归收集字段路径。 */
public final class OperandDependencyCollector {

    private OperandDependencyCollector() {
    }

    static void collect(JSONObject operand, Set<String> paths) {
        if (operand == null || paths == null) return;
        String kind = operand.getString("kind");
        if ("PATH".equals(kind) || "REFERENCE".equals(kind)) {
            String path = firstText(operand.getString("code"), operand.getString("value"));
            if (path != null) paths.add(path);
            return;
        }
        if ("FUNCTION".equals(kind)) collectArray(operand.getJSONArray("args"), paths);
        else if ("OPERATION".equals(kind)) collectTerms(operand.getJSONArray("terms"), paths);
        else if ("ARRAY".equals(kind)) collectArray(operand.getJSONArray("items"), paths);
        else if ("ACCESS".equals(kind)) {
            collect(operand.getJSONObject("target"), paths);
            collect(operand.getJSONObject("accessor"), paths);
        } else if ("CAST".equals(kind)) collect(operand.getJSONObject("operand"), paths);
    }

    public static List<Reference> collectReferences(Object root) {
        List<Reference> references = new ArrayList<>();
        collectReferencesFromTree(root, "$", references);
        return references;
    }

    public static void collectReferences(JSONObject operand, List<Reference> result) {
        if (operand == null || result == null) return;
        collectReferencesFromTree(operand, "$", result);
    }

    private static void collectReferencesFromTree(Object node, String path, List<Reference> result) {
        if (node instanceof JSONObject object) {
            String kind = object.getString("kind");
            if ("REFERENCE".equals(kind)) {
                result.add(new Reference(object.getString("refType"), object.getLong("refId"),
                        firstText(object.getString("code"), object.getString("value")), path));
            } else if ("FUNCTION".equals(kind)) {
                result.add(new Reference("FUNCTION", object.getLong("functionId"),
                        object.getString("functionCode"), path));
            } else if ("RULE_CALL".equals(kind)) {
                result.add(new Reference("RULE", object.getLong("ruleId"),
                        firstText(object.getString("ruleCode"), object.getString("code")), path));
            } else if (object.getLong("ruleId") != null && isRuleNode(object)) {
                result.add(new Reference("RULE", object.getLong("ruleId"),
                        firstText(object.getString("ruleCode"), object.getString("code")), path));
            }
            collectPersistedIdReferences(object, path, result);
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                collectReferencesFromTree(entry.getValue(), path + "." + entry.getKey(), result);
            }
        } else if (node instanceof JSONArray array) {
            for (int index = 0; index < array.size(); index++) {
                collectReferencesFromTree(array.get(index), path + "[" + index + "]", result);
            }
        }
    }

    private static void collectPersistedIdReferences(JSONObject object, String path,
                                                     List<Reference> result) {
        addPair(object, path, result, "_varId",
                firstText(object.getString("_refType"), object.getString("refType")),
                firstText(object.getString("varCode"), object.getString("refCode"),
                        object.getString("code"), object.getString("value")));
        addPair(object, path, result, "varId", object.getString("refType"),
                firstText(object.getString("refCode"), object.getString("varCode"),
                        object.getString("code"), object.getString("value")));
        addPair(object, path, result, "leftVarId", object.getString("leftRefType"),
                firstText(object.getString("leftVar"), object.getString("leftLabel")));
        addPair(object, path, result, "rightVarId", object.getString("rightRefType"),
                firstText(object.getString("rightVar"), object.getString("rightLabel")));
        addPair(object, path, result, "_targetVarId", object.getString("_targetRefType"),
                object.getString("target"));
        addPair(object, path, result, "_condVarId", object.getString("_condVarRefType"),
                object.getString("condVar"));
        addPair(object, path, result, "_matchVarId", object.getString("_matchVarRefType"),
                object.getString("matchVar"));
        addPair(object, path, result, "_checkVarId", object.getString("_checkVarRefType"),
                object.getString("checkVar"));
        addPair(object, path, result, "_rightVarId", object.getString("_rightRefType"),
                object.getString("value"));
        addFixedType(object, path, result, "functionId", "FUNCTION",
                object.getString("functionCode"));
        addFixedType(object, path, result, "modelId", "MODEL",
                firstText(object.getString("modelCode"), object.getString("code")));
    }

    private static void addPair(JSONObject object, String path, List<Reference> result,
                                String idField, String refType, String displayCode) {
        Long refId = object.getLong(idField);
        if (refId == null) return;
        addUnique(result, new Reference(refType, refId, displayCode, path + "." + idField));
    }

    private static void addFixedType(JSONObject object, String path, List<Reference> result,
                                     String idField, String refType, String displayCode) {
        Long refId = object.getLong(idField);
        if (refId == null) return;
        addUnique(result, new Reference(refType, refId, displayCode, path + "." + idField));
    }

    private static void addUnique(List<Reference> result, Reference candidate) {
        for (Reference existing : result) {
            if (same(existing.refType, candidate.refType)
                    && java.util.Objects.equals(existing.refId, candidate.refId)
                    && java.util.Objects.equals(existing.path, candidate.path)) {
                return;
            }
        }
        result.add(candidate);
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equalsIgnoreCase(right);
    }

    private static boolean isRuleNode(JSONObject object) {
        String type = firstText(object.getString("type"), object.getString("actionType"));
        return type != null && type.toUpperCase(java.util.Locale.ROOT).contains("RULE");
    }

    private static void collectArray(JSONArray values, Set<String> paths) {
        if (values == null) return;
        for (int i = 0; i < values.size(); i++) collect(values.getJSONObject(i), paths);
    }

    private static void collectTerms(JSONArray terms, Set<String> paths) {
        if (terms == null) return;
        for (int i = 0; i < terms.size(); i++) {
            JSONObject term = terms.getJSONObject(i);
            collect(term == null ? null : term.getJSONObject("operand"), paths);
        }
    }

    private static String firstText(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return null;
    }

    public static final class Reference {
        private final String refType;
        private final Long refId;
        private final String displayCode;
        private final String path;

        private Reference(String refType, Long refId, String displayCode, String path) {
            this.refType = refType;
            this.refId = refId;
            this.displayCode = displayCode;
            this.path = path;
        }

        public String getRefType() {
            return refType;
        }

        public Long getRefId() {
            return refId;
        }

        public String getDisplayCode() {
            return displayCode;
        }

        public String getPath() {
            return path;
        }
    }
}
