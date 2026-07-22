package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RulePreflightReport {
    private Long revisionId;
    private boolean valid;
    private boolean breakingSchemaChange;
    private boolean breakingChangeReasonRequired;
    private String inputSchemaJson;
    private String outputSchemaJson;
    private String schemaCompatibilityJson;
    private String dependencyDigest;
    private String contentDigest;
    private String compiledScript;
    private String compiledType;
    private List<RuleValidationIssue> errors = new ArrayList<>();
    private List<RuleValidationIssue> warnings = new ArrayList<>();
}
