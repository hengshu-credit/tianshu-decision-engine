package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ArtifactDeployRequest {
    private Long artifactId;
    private String environmentCode;
    private Boolean createRule;
    private Long targetDefinitionId;
    private Long targetProjectId;
    private String targetRuleCode;
    private String targetRuleName;
    private String targetModelType;
    private String comment;
    private Map<String, Long> bindings = new LinkedHashMap<>();
}
