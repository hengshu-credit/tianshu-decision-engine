package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_model_output_field")
public class RuleModelOutputField {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long modelId;

    /** 关联字段ID，需结合 refType 判断所属资源表 */
    private Long varId;

    /** 引用类型：VARIABLE/CONSTANT/DATA_OBJECT/MODEL */
    private String refType;

    private String fieldName;
    private String fieldLabel;

    /** 脚本中的引用名（对应变量，用于关联引擎变量） */
    private String scriptName;

    /** 字段类型：STRING/NUMBER/INTEGER/DOUBLE/PROBABILITY/VECTOR */
    private String fieldType;

    private String targetField;

    /** 统一 Operand JSON：模型输出写入的引擎字段/路径 */
    private String targetOperand;
    private String featureName;

    /** 函数转换 Operand；顶层必须是 FUNCTION */
    private String transformOperand;

    /** 是否概率输出：0-否，1-是 */
    private Integer isProbability;

    /** 类别标签（概率输出时指定） */
    private String category;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
