package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_definition_version")
public class RuleDefinitionVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long definitionId;
    private Integer version;
    private String modelJson;
    private String compiledScript;
    private String compiledType;
    private String changeLog;
    private String publishBy;
    private LocalDateTime publishTime;
}
