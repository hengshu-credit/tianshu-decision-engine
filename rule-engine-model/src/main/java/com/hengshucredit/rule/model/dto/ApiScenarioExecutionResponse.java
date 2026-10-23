package com.hengshucredit.rule.model.dto;

public record ApiScenarioExecutionResponse(RuleResult execution, String responseJson,
                                           TestExpectationResult validation, String reportId) {
    public ApiScenarioExecutionResponse(RuleResult execution, String responseJson, TestExpectationResult validation) {
        this(execution, responseJson, validation, null);
    }
}
