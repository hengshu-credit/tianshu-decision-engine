package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("rule_engine.rule_variable_option")
public class RuleVariableOption {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long variableId;
    private String optionValue;
    private String optionLabel;
    private Integer sortOrder;
}
