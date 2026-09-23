package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_execution_log")
public class RuleExecutionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private Long rootRuleId;
    private Long executionProjectId;
    private LocalDateTime startedAt;
    /** 按执行时字段 ID 保存的请求入参和显式开启记录的结果，不按名称回填旧日志。 */
    private String historyFields;
    private String ruleCode;
    private String projectCode;
    private Integer ruleVersion;
    private Long revisionId;
    private String artifactDigest;
    private String modelType;
    private String source;
    private String clientAppName;
    private String clientIp;
    private Long authId;
    private String authCode;
    private String authType;
    private Long tokenId;
    private String tokenCode;
    private String authPhase;
    private String inputParams;
    private String outputResult;
    private String traceInfo;
    private Integer success;
    private String errorMessage;
    private Long executeTimeMs;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
