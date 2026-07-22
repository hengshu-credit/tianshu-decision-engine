package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.decision_artifact_component")
public class DecisionArtifactComponent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long artifactId;
    private String componentId;
    private String componentType;
    private String sourceType;
    private Long sourceId;
    private Integer sourceVersion;
    private String packagePath;
    private String mediaType;
    private String contentDigest;
    private Long contentSize;
    private String metadataJson;
    private LocalDateTime createTime;
}
