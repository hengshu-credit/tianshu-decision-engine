package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("governance_approval_request")
public class GovernanceApprovalRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestNo;
    private String resourceType;
    private Long resourceId;
    private Long projectId;
    private String action;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String activeResourceKey;
    private Long baseVersionId;
    private Integer baseVersionNo;
    private Long sourceVersionId;
    private String draftSnapshotJson;
    private String submittedSnapshotJson;
    private String snapshotDigest;
    private String secretPayloadCiphertext;
    private String secretDigest;
    private String dependencyDigest;
    private String validationReportJson;
    private String changeSummary;
    private String submitComment;
    private String reviewComment;
    private String applicant;
    private LocalDateTime submitTime;
    private String reviewer;
    private LocalDateTime reviewTime;
    @Version
    private Integer lockVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
