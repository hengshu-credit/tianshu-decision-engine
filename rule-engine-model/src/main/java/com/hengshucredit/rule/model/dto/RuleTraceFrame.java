package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RuleTraceFrame {

    private int schemaVersion = 2;
    private String traceId;
    private String traceKind = "RULE";
    private Long ruleId;
    private String ruleCode;
    private String ruleName;
    private String modelType;
    private String modelJson;
    private String scope;
    private String status;
    private long durationMs;
    private List<Object> events = new ArrayList<>();
    private List<Object> expressionTrace = new ArrayList<>();
    private List<RuleTraceFrame> children = new ArrayList<>();
}
