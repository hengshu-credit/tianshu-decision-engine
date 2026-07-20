package com.hengshucredit.rule.core.function;

import com.hengshucredit.rule.core.engine.RuntimeContextBridge;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** QLExpress 请求级运行时上下文函数。 */
public class RuntimeContextBuiltinFunctions {

    public Object setRuntimeValue(String path, Object value) {
        return RuntimeContextBridge.setValue(path, value);
    }

    public Map<String, Object> currentRule() {
        return RuntimeContextBridge.currentRule();
    }

    public String currentRuleName() {
        Map<String, Object> rule = currentRule();
        Object name = rule.get("name");
        if (name == null || String.valueOf(name).trim().isEmpty()) {
            name = rule.get("code");
        }
        return name == null ? "" : String.valueOf(name);
    }

    public List<String> currentMatchedConditions() {
        return RuntimeContextBridge.currentMatchedConditions();
    }

    public boolean sourceStatus(String refType, String refId, String dimension, String expected) {
        return RuntimeContextBridge.sourceStatusMatches(refType, refId, dimension, expected);
    }

    public Object recordRuleSetItem(String ruleCode, String ruleName, Object hit) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "RULE_SET_ITEM");
        event.put("ruleCode", ruleCode);
        event.put("ruleName", ruleName);
        event.put("evaluated", true);
        event.put("hit", booleanValue(hit));
        RuntimeContextBridge.addTraceEvent(event);
        return hit;
    }

    public Object recordRuleSetSummary(Object hit) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "RULE_SET_SUMMARY");
        event.put("evaluated", true);
        event.put("hit", booleanValue(hit));
        RuntimeContextBridge.addTraceEvent(event);
        return hit;
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean ? (Boolean) value
                : value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }
}
