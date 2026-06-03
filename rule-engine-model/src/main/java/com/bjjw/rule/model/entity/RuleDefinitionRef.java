package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_definition_ref")
public class RuleDefinitionRef {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long definitionId;
    private Long projectId;
    private LocalDateTime createTime;
}