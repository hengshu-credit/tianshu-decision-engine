package com.hengshucredit.rule.model.dto;

public record ApiScenarioExecutionResponse(RuleResult execution, String responseJson,
                                           TestExpectationResult validation) {
}
