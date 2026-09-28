package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 发布 outbox 管理列表摘要；不返回完整 messageJson。 */
@Data
public class RulePublishOutboxSummary {
    private Long id;
    private String operationId;
    private Long definitionId;
    private Long revisionId;
    private Long artifactId;
    private String deliveryStatus;
    private Integer retryCount;
    private String lastError;
    private String messageDigest;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime deliveredTime;
    private LocalDateTime deadLetterTime;
}
