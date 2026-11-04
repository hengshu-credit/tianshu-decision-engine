package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_model_version")
public class RuleModelVersion implements AccountOwnedRecord {
    @com.baomidou.mybatisplus.annotation.TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private Long actorUserId;
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long modelId;
    private Integer version;
    private Integer businessVersion;
    public Integer getDisplayVersion() { return businessVersion == null ? version : businessVersion; }
    private String modelContent;
    private String modelConfig;
    private String modelFormat;
    private String modelFileName;
    private Long modelFileSize;
    private String modelDigest;
    private String inputSchemaJson;
    private String outputSchemaJson;
    private String validationReportJson;
    private String runtimeConstraintsJson;
    /** 完整模型字段与冻结依赖执行包，不通过版本列表接口传输。 */
    private String executionSnapshotJson;
    private String executionSnapshotDigest;
    private String sampleStatus;
    private Integer status;
    private String changeLog;
    private String publishBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime publishTime;
}
