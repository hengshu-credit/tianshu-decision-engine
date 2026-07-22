package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.artifact_deployment")
public class ArtifactDeployment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long artifactId;
    private String environmentCode;
    private Long targetDefinitionId;
    private Integer createRule;
    private String status;
    private String compatibilityReportJson;
    private String bindingReportJson;
    private String errorMessage;
    private String deployBy;
    private LocalDateTime deployTime;
    private LocalDateTime createTime;
}
