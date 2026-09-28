package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 规则执行日志/计费部分失败后的可恢复事件。 */
@Data
@TableName("rule_engine.rule_execution_persistence_outbox")
public class RuleExecutionPersistenceOutbox {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String logJson;
    private String definitionJson;
    private Integer success;
    private Long costTimeMs;
    private String errorMessage;
    private Long projectId;
    private String projectCode;
    private Long authId;
    private String authCode;
    private String authType;
    private Long tokenId;
    private String tokenCode;
    private String authPhase;
    private Integer logPending;
    private Integer billingPending;
    private String deliveryStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime deliveredTime;
    private String claimToken;
    private LocalDateTime leaseUntil;
}
