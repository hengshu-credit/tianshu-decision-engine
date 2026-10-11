package com.hengshucredit.rule.model.dto;

import lombok.Data;

/** Optional management-test attribution. IDs are strings so browser JSON parsing cannot round them. */
@Data
public class RuleTestExecutionIdentity {
    private String sourceType;
    private String sourceId;
    private String baseSourceType;
    private String baseSourceId;
    private String definitionId;
    private String revisionId;
    private Integer lockVersion;
    private String revisionState;
    private Integer revisionNo;
    private Integer versionNo;
    private String artifactId;
    private String artifactDigest;
    private String modelDigest;
    private String modelType;
}
