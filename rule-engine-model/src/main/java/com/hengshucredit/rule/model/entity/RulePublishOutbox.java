package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_publish_outbox")
public class RulePublishOutbox {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String operationId;
    private Long definitionId;
    private Long revisionId;
    private Long artifactId;
    private String messageJson;
    private String deliveryStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime deliveredTime;
}
