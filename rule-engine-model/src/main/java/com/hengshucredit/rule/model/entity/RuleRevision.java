package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_revision")
public class RuleRevision {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long definitionId;
    private Integer revisionNo;
    private String state;
    private Long baseRevisionId;
    private Long baseArtifactId;
    private String modelJson;
    private String compiledScript;
    private String compiledType;
    private String openApiConfigJson;
    private String inputSchemaJson;
    private String outputSchemaJson;
    private String contentDigest;
    private String validationReportDigest;
    private Long artifactId;
    private String forcePublishReason;
    private Integer lockVersion;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String submitBy;
    private LocalDateTime submitTime;
    private String approveBy;
    private LocalDateTime approveTime;
    private String publishBy;
    private LocalDateTime publishTime;
    private String offlineBy;
    private LocalDateTime offlineTime;
}
