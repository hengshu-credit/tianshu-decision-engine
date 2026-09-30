package com.hengshucredit.rule.model.dto;

import lombok.Data;

/** 后台规则编译任务的查询结果。 */
@Data
public class RuleDesignerCompileTaskResponse {
    private String taskId;
    private String status;
    private RuleDesignerCompileResponse result;
    private String errorMessage;
}
