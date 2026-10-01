package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("governance_dependency_snapshot")
public class GovernanceDependencySnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requestId;
    private Long versionId;
    private String sourceResourceType;
    private Long sourceResourceId;
    private String targetResourceType;
    private Long targetResourceId;
    private Long targetVersionId;
    private Integer targetVersionNo;
    private String referencePath;
    private String relationType;
    private Integer required;
    private String resolutionStatus;
    private String targetDigest;
    private String issueCode;
    private String issueMessage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
