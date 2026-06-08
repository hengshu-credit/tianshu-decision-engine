package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_model_output_field")
public class RuleModelOutputField {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long modelId;

    /** 关联的变量ID（外键 -> rule_variable.id） */
    private Long varId;

    private String fieldName;
    private String fieldLabel;

    /** 脚本中的引用名（对应变量，用于关联引擎变量） */
    private String scriptName;

    /** 字段类型：STRING/NUMBER/INTEGER/DOUBLE/PROBABILITY/VECTOR */
    private String fieldType;

    private String targetField;
    private String featureName;

    /** 转换方法：NONE/RENAME/SCALE/OHE */
    private String transformType;

    /** 是否概率输出：0-否，1-是 */
    private Integer isProbability;

    /** 类别标签（概率输出时指定） */
    private String category;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}