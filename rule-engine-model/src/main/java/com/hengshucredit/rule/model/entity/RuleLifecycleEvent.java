package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_lifecycle_event")
public class RuleLifecycleEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long definitionId;
    private Long revisionId;
    private String action;
    private String fromState;
    private String toState;
    private String actor;
    private String comment;
    private String contentDigest;
    private String validationReportDigest;
    private String artifactDigest;
    private String requestSource;
    private Long deploymentId;
    private String detailsJson;
    private LocalDateTime createTime;
}
