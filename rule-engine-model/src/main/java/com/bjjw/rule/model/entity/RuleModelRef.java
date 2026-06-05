package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_model_ref")
public class RuleModelRef {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long modelId;
    private Long projectId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}