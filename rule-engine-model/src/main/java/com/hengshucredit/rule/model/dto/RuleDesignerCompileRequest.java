package com.hengshucredit.rule.model.dto;

import com.hengshucredit.rule.model.enums.RuleDraftSourceType;
import lombok.Data;

@Data
public class RuleDesignerCompileRequest {
    private String modelJson;
    private RuleDraftSourceType sourceType;
    private Long sourceId;
}
