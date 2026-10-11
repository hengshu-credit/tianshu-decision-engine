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
    /** 业务版本号可复用；versionNo 始终为不可变快照序号。 */
    private Integer businessVersion;
    /** Logical retirement retains execution and approval evidence. */
    private Boolean deleted;
    public Integer getDisplayVersion() { return businessVersion == null ? versionNo : businessVersion; }
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
