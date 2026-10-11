package com.hengshucredit.rule.model.dto;

import lombok.Data;

@Data
public class ApiScenarioExecutionRequest {
    private Long scenarioId;
    private Long projectId;
    /** 原始报文字符串避免客户端和JSON请求转换器先将大整数/小数舍入。 */
    private String requestJson;
    private String expectationJson;
    private String expectedResponseJson;
    private RuleTestExecutionSource executionSource;
}
