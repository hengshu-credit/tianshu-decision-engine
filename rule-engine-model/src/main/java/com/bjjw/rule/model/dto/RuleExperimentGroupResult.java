package com.bjjw.rule.model.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RuleExperimentGroupResult {
    private String stage;
    private String groupCode;
    private String groupName;
    private String groupType;
    private String ruleCode;
    private String routeReason;
    private boolean matched;
    private boolean skipped;
    private boolean success;
    private Object result;
    private String errorMessage;
    private long executeTimeMs;
    private List<Object> traces;
    private Map<String, Object> resolvedParams;
}
