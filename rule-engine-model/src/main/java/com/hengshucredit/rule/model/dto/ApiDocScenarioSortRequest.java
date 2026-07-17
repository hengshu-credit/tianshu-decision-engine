package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class ApiDocScenarioSortRequest {
    private List<Long> scenarioIds;
}
