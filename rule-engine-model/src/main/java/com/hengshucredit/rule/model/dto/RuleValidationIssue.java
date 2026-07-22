package com.hengshucredit.rule.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleValidationIssue {
    private String severity;
    private String code;
    private String path;
    private String resourceType;
    private Long resourceId;
    private String message;

    public static RuleValidationIssue error(String code, String path, String message) {
        return new RuleValidationIssue("ERROR", code, path, null, null, message);
    }

    public static RuleValidationIssue error(String code, String path, String resourceType,
                                            Long resourceId, String message) {
        return new RuleValidationIssue("ERROR", code, path, resourceType, resourceId, message);
    }

    public static RuleValidationIssue warning(String code, String path, String message) {
        return new RuleValidationIssue("WARNING", code, path, null, null, message);
    }
}
