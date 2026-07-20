package com.hengshucredit.rule.server.service;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class VariableResolveOptions {
    private boolean skipApiSources;
    private boolean forceRefreshSource;
    /** 仅沿直接引用向上游展开依赖，不根据已满足输入推导无关的下游模型。 */
    private boolean requiredNamesUpstreamOnly;
    private LocalDateTime listMatchTime;
    private Set<String> requiredScriptNames;
    /** 模型中显式使用来源状态操作符的 refType:refId 集合。 */
    private Set<String> statusReferenceKeys;
    /** 本次执行的来源状态 sidecar，键严格为 refType:refId。 */
    private Map<String, Map<String, Object>> sourceStates = new LinkedHashMap<>();

    public boolean requiresSourceStatus(String refType, Long refId) {
        return refId != null && refType != null && statusReferenceKeys != null
                && statusReferenceKeys.contains(statusKey(refType, refId));
    }

    public void recordSourceState(String refType, Long refId, String dimension, Object value) {
        if (!requiresSourceStatus(refType, refId) || dimension == null) return;
        String key = statusKey(refType, refId);
        Map<String, Object> state = sourceStates.get(key);
        if (state == null) {
            state = new LinkedHashMap<>();
            sourceStates.put(key, state);
        }
        state.put(dimension.trim().toUpperCase(), value);
    }

    private String statusKey(String refType, Long refId) {
        return refType.trim().toUpperCase() + ":" + refId;
    }

    public static VariableResolveOptions defaults() {
        return new VariableResolveOptions();
    }
}
