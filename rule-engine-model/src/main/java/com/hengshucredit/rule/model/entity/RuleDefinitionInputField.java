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

    /** Test sample value from variable metadata; not persisted in rule_definition_input_field. */
    @TableField(exist = false)
    private String exampleValue;

    /** 有效值列表（JSON数组） */
    private String validValues;

    /** 转换类型：NONE/NORMALIZE/DISCRETIZE/MAPVALUES/MINMAX */
    private String transformType;

    private String transformParams;

    /** 关联的字段校验规则 ID 列表（JSON 数组）。 */
    private String validationRuleIds;

    /** 是否由当前规则显式覆盖子规则校验：0-继承，1-当前规则覆盖（含显式清空）。 */
    private Integer validationOverride;

    private Integer sortOrder;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
