package com.hengshucredit.rule.model.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class RuleRegressionGate {
    private boolean allowed;
    private int requiredCount;
    private int passedCount;
    private int missingCount;
    private int staleCount;
    private int failedCount;
    private String message;
    private List<CaseStatus> cases = new ArrayList<>();
    public record CaseStatus(String scenarioId, String scenarioName, String reportId, String status) { }
}
