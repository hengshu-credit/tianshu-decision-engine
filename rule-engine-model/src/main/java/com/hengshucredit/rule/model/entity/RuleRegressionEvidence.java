package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_regression_evidence")
public class RuleRegressionEvidence {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long definitionId;
    private Long scenarioId;
    private String scenarioName;
    private Long revisionId;
    private Long projectId;
    private String sourceType;
    private String sourceId;
    private String contentDigest;
    private String dependencyDigest;
    private String scenarioDigest;
    private Integer eligible;
    private String state;
    private Integer success;
    private String validationStatus;
    private String validationJson;
    private String message;
    private String traceId;
    private String executionIdentityJson;
    private String executedBy;
    private LocalDateTime createTime;
    private LocalDateTime completedAt;
}
