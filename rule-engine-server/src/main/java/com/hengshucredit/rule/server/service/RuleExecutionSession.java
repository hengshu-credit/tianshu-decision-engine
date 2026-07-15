package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.RuleTraceFrame;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public class RuleExecutionSession {

    private Long currentProjectId;
    private String currentProjectCode;
    private final Map<String, Object> values;
    private final Map<String, Object> originalInput;
    private final boolean testMode;
    private final Deque<String> ruleStack = new ArrayDeque<>();
    private final Deque<RuleTraceFrame> traceStack = new ArrayDeque<>();
    private final RuleTraceFrame rootTrace;

    RuleExecutionSession(Long projectId, String projectCode, Map<String, Object> values,
                         Map<String, Object> originalInput, boolean testMode,
                         String rootRuleCode, RuleTraceFrame rootTrace) {
        this.currentProjectId = projectId;
        this.currentProjectCode = projectCode;
        this.values = values == null ? new LinkedHashMap<String, Object>() : values;
        this.originalInput = originalInput == null
                ? new LinkedHashMap<>(this.values) : new LinkedHashMap<>(originalInput);
        this.testMode = testMode;
        this.rootTrace = rootTrace;
        if (rootRuleCode != null && !rootRuleCode.trim().isEmpty()) {
            this.ruleStack.addLast(rootRuleCode);
        }
        this.traceStack.addLast(rootTrace);
    }

    public Long getCurrentProjectId() {
        return currentProjectId;
    }

    void setCurrentProjectId(Long currentProjectId) {
        this.currentProjectId = currentProjectId;
    }

    public String getCurrentProjectCode() {
        return currentProjectCode;
    }

    void setCurrentProjectCode(String currentProjectCode) {
        this.currentProjectCode = currentProjectCode;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public Map<String, Object> getOriginalInput() {
        return originalInput;
    }

    public boolean isTestMode() {
        return testMode;
    }

    Deque<String> getRuleStack() {
        return ruleStack;
    }

    Deque<RuleTraceFrame> getTraceStack() {
        return traceStack;
    }

    public RuleTraceFrame getRootTrace() {
        return rootTrace;
    }

    RuleTraceFrame currentTrace() {
        return traceStack.peekLast();
    }
}
