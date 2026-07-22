package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.artifact_resource_binding")
public class ArtifactResourceBinding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deploymentId;
    private String componentId;
    private String resourceType;
    private Long targetResourceId;
    private String bindingDigest;
    private LocalDateTime createTime;
}
