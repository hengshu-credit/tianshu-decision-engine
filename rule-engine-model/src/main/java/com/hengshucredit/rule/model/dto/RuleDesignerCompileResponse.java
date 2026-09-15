package com.hengshucredit.rule.model.dto;

import lombok.Data;

@Data
public class RuleDesignerCompileResponse {
    private boolean compileSuccess;
    private String compileMessage;
    private String compiledScript;
    private String compiledType;
    private RulePreflightReport preflightReport;
}
