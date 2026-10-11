package com.hengshucredit.rule.model.dto;

import lombok.Data;

/** Shared log and billing filter. ALL keeps historical records visible by default. */
@Data
public class ExecutionVersionQuery {
    private Long ruleId;
    private String versionMode;
    private Integer ruleVersion;
    private Long revisionId;
}
