package com.hengshucredit.rule.core.function;

import com.hengshucredit.rule.core.engine.RuntimeContextBridge;

import java.util.List;
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
}
