package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_model_version")
public class RuleModelVersion {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long modelId;
    private Integer version;
    private String modelContent;
    private String modelConfig;
    private String changeLog;
    private String publishBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime publishTime;
}