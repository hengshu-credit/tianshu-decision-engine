package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("governed_resource_version")
public class GovernedResourceVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long governedResourceId;
    private String resourceType;
    private Long resourceId;
    private Integer versionNo;
    private Long sourceVersionId;
    private Long approvalRequestId;
    private String snapshotJson;
    private String snapshotDigest;
    private String secretPayloadCiphertext;
    private String secretDigest;
    private String effectiveStatus;
    private String changeSummary;
    private String legacySourceType;
    private Long legacySourceId;
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
