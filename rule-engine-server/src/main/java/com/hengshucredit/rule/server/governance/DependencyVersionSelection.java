package com.hengshucredit.rule.server.governance;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The original field/owner and version namespace must survive governance owner resolution. */
public record DependencyVersionSelection(String refType, Long refId, String versionSource, Long versionId) {
    static DependencyVersionSelection read(String json) {
        if (json == null || json.isBlank()) return null;
        var value = com.alibaba.fastjson.JSON.parseObject(json);
        return new DependencyVersionSelection(value.getString("refType"), value.getLong("refId"),
                value.getString("versionSource"), value.getLong("versionId"));
    }

    static DependencyVersionSelection forReference(String key, Map<String, Object> owner, Long id) {
        DependencyVersionSelection direct = from(owner);
        if (direct != null && Objects.equals(id, direct.refId()) && key.equals(idField(owner))) return direct;
        List<String> operands = switch (key) {
            case "leftVarId", "_condVarId" -> List.of("leftOperand");
            case "rightVarId", "_rightVarId" -> List.of("rightOperand");
            case "_targetVarId" -> List.of("targetOperand");
            case "_matchVarId" -> List.of("matchOperand");
            case "_checkVarId" -> List.of("checkOperand");
            case "_varId", "varId" -> List.of("leftOperand", "targetOperand", "operand", "sourceOperand", "valueOperand");
            default -> List.of();
        };
        for (String operand : operands) {
            Object raw = owner.get(operand);
            if (raw instanceof String json && json.stripLeading().startsWith("{")) raw = com.alibaba.fastjson.JSON.parseObject(json);
            if (!(raw instanceof Map<?, ?> map)) continue;
            DependencyVersionSelection selection = from(map);
            if (selection != null && Objects.equals(id, selection.refId())) return selection;
        }
        return null;
    }

    private static String idField(Map<?, ?> value) {
        return value.get("versionBindingId") != null ? "ruleId" : "FUNCTION".equals(value.get("kind"))
                || "FUNCTION".equals(value.get("refType")) ? "functionId" : value.containsKey("refId") ? "refId"
                : value.containsKey("varId") ? "varId" : "_varId";
    }

    private static DependencyVersionSelection from(Map<?, ?> value) {
        if (!"FIXED".equals(value.get("versionMode"))) return null;
        boolean rule = value.get("versionBindingId") != null;
        String type = rule ? "RULE" : "FUNCTION".equals(value.get("kind")) ? "FUNCTION"
                : Objects.toString(value.get("refType"), Objects.toString(value.get("_refType"), "VARIABLE"));
        return new DependencyVersionSelection(type, id(value.get(idField(value))),
                rule ? "RULE_BINDING" : Objects.toString(value.get("versionSource"), "SNAPSHOT"),
                id(value.get(rule ? "versionBindingId" : "versionId")));
    }

    private static Long id(Object value) {
        if (value == null || !String.valueOf(value).matches("[1-9][0-9]*")) throw new IllegalArgumentException("固定版本依赖缺少有效 ID");
        return Long.valueOf(String.valueOf(value));
    }
}
