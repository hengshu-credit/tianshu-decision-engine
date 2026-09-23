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
    /** 本次执行使用的冻结变量路径；null 表示尚未装载，不得将空快照回退为主表。 */
    private Map<String, String> variableReferencePaths;
    /** 衍生表达式使用的 ID 路径与函数快照，历史侧字段不参与本次请求的上游计算。 */
    private Map<String, String> derivedReferencePaths;
    /** 对象本身的路径，用于已有子字段优先；不改变冻结的字段 ID。 */
    private Map<String, String> dataObjectReferencePaths;
    private Map<Long, com.hengshucredit.rule.model.entity.RuleFunction> derivedFunctions;
    /** 同一根规则请求（含子规则）共享的外数调用 single-flight/cache。 */
    private VariableResolutionInvocationCache invocationCache;
    private Map<String, com.hengshucredit.rule.server.derived.HistoricalFieldDefinition> historyFieldDefinitions;
    private boolean captureDatabasePreview;
    private java.util.List<Map<String, Object>> databasePreviewRows;

    public boolean requiresSourceStatus(String refType, Long refId) {
        return refId != null && refType != null && statusReferenceKeys != null
                && statusReferenceKeys.contains(statusKey(refType, refId));
    }

    public void recordSourceState(String refType, Long refId, String dimension, Object value) {
        if (refType == null || refId == null || dimension == null) return;
        String key = statusKey(refType, refId);
        Map<String, Object> state = sourceStates.get(key);
        if (state == null) {
            state = new LinkedHashMap<>();
            sourceStates.put(key, state);
        }
        state.put(dimension.trim().toUpperCase(), value);
    }

    public void mergeSourceStates(Map<String, Map<String, Object>> states) {
        if (states == null || states.isEmpty()) return;
        for (Map.Entry<String, Map<String, Object>> entry : states.entrySet()) {
            Map<String, Object> state = sourceStates.get(entry.getKey());
            if (state == null) {
                state = new LinkedHashMap<>();
                sourceStates.put(entry.getKey(), state);
            }
            if (entry.getValue() != null) {
                state.putAll(entry.getValue());
            }
        }
    }

    private String statusKey(String refType, Long refId) {
        return refType.trim().toUpperCase() + ":" + refId;
    }

    public static VariableResolveOptions defaults() {
        return new VariableResolveOptions();
    }
}
