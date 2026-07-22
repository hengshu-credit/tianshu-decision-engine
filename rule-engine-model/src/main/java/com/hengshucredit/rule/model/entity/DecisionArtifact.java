package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.decision_artifact")
public class DecisionArtifact {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long definitionId;
    private Long revisionId;
    private String artifactDigest;
    private String packageDigest;
    private String formatVersion;
    private String manifestJson;
    private String validationReportJson;
    private String runtimeConstraintsJson;
    private byte[] packageContent;
    private Long packageSize;
    private String createBy;
    private LocalDateTime createTime;
}
