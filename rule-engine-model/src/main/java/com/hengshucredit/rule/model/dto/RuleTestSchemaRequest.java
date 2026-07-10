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
}
