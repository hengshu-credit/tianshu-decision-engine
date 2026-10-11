package com.hengshucredit.rule.model.dto;

import lombok.Data;

@Data
public class ApiDocScenarioSaveRequest {
    private String scenarioName;
    private String description;
    private String requestJson;
    private String responseJson;
    /** 版本化预期配置；更新时未提供则保留已有配置，显式 NONE 表示不校验。 */
    private String expectationJson;
    private String responseSource;
    private String businessCodePath;
    private Integer includeInDoc;
    private Integer sortOrder;
    private Integer status;
}
