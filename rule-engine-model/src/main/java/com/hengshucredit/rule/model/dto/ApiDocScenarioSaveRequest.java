package com.hengshucredit.rule.model.dto;

import lombok.Data;

@Data
public class ApiDocScenarioSaveRequest {
    private String scenarioName;
    private String description;
    private String requestJson;
    private String responseJson;
    private String responseSource;
    private String businessCodePath;
    private Integer includeInDoc;
    private Integer sortOrder;
    private Integer status;
}
