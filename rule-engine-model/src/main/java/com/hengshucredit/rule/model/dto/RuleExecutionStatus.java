package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 跨节点幂等执行状态查询结果。 */
@Data
public class RuleExecutionStatus {
    private String traceId;
    private String status;
    private int attemptNo;
    private Long revisionId;
    private String artifactDigest;
    private RuleResult result;
    private String errorMessage;
    private LocalDateTime expireTime;
}
