package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_definition_output_field")
public class RuleDefinitionOutputField {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long definitionId;

    /** 关联字段ID，需结合 refType 判断所属资源表 */
    private Long varId;

    /** 引用类型：VARIABLE/CONSTANT/DATA_OBJECT/MODEL */
    private String refType;

    private String fieldName;

    private String fieldLabel;

    /** 脚本中的引用名（驼峰） */
    private String scriptName;

    /** 字段类型：STRING/NUMBER/INTEGER/DOUBLE */
    private String fieldType;

    /** 转换方法：NONE/RENAME/SCALE/OHE */
    private String transformType;

    private String transformParams;

    /** 有效值列表（JSON数组，分类变量） */
    private String validValues;

    private Integer sortOrder;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
