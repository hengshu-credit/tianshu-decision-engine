package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.Map;

/** 表达式编译与测试请求；只作用于当前 Operand，不保存规则内容。 */
@Data
public class RuleExpressionRequest {
    private Long ruleId;
    private Long projectId;
    /** CURRENT / DEEP */
    private String resolutionMode;
    private Map<String, Object> operand;
    private Map<String, Object> params;
}
