package com.hengshucredit.rule.model.dto;
import lombok.Data;
@Data
public class RuleDesignerPublishRequest {
    private Long revisionId;
    private Integer lockVersion;
    private String publishMode;
    private Long targetVersionId;
    private Long targetGeneration;
    private String comment;
}
