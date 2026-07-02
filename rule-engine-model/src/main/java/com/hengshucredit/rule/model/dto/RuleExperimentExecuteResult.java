package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RuleExperimentExecuteResult {
    private String experimentCode;
    private String experimentName;
    private String requestKey;
    private boolean success;
    private String errorMessage;
    private long executeTimeMs;
    private RuleExperimentGroupResult productionGroup;
    private List<RuleExperimentGroupResult> testGroups = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
}
