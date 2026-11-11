package com.hengshucredit.rule.model.dto;

import lombok.Data;

/** 统一测试字段结构请求。 */
@Data
public class RuleTestSchemaRequest {
    /** RULE / MODEL / VARIABLE / EXPERIMENT */
    private String targetType;
    private Long targetId;
    private Long projectId;
    private String modelType;
    private String modelJson;
    private RuleTestExecutionSource executionSource;
    /** 规则测试页需要与输入及执行身份同一次解析的设计内容。 */
    private boolean includeDesign;
    private String versionMode;
    private Long versionId;
}
