package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_definition_input_field")
public class RuleDefinitionInputField {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long definitionId;

    /** 关联字段ID，需结合 refType 判断所属资源表 */
    private Long varId;

    /** 引用类型：VARIABLE/CONSTANT/DATA_OBJECT/MODEL */
    private String refType;

    /** 字段名称（原始名称） */
    private String fieldName;

    private String fieldLabel;

    /** 脚本中的引用名（驼峰） */
    private String scriptName;

    /** 数据类型：STRING/NUMBER/INTEGER/DOUBLE/BOOLEAN/DATE */
    private String fieldType;

    /** 缺失值处理策略 */
    private String missingValue;

    /** 默认值 */
    private String defaultValue;

    /** 有效值列表（JSON数组） */
    private String validValues;

    /** 转换类型：NONE/NORMALIZE/DISCRETIZE/MAPVALUES/MINMAX */
    private String transformType;

    private String transformParams;

    private Integer sortOrder;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
