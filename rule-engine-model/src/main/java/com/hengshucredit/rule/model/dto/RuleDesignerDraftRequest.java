package com.hengshucredit.rule.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RuleDesignerDraftRequest extends RuleDesignerCompileRequest {
    private String saveMode;
    private Long revisionId;
    private Integer lockVersion;
    private String requestId;
    private String openApiConfigJson;
    private Boolean updateOpenApiConfig;
}
