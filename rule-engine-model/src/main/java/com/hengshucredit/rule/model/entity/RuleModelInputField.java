package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_model_input_field")
public class RuleModelInputField {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long modelId;

    /** 关联字段ID，需结合 refType 判断所属资源表 */
    private Long varId;

    /** 引用类型：VARIABLE/CONSTANT/DATA_OBJECT/MODEL */
    private String refType;

    /** 字段名称（原始名称） */
    private String fieldName;
    private String fieldLabel;
    private String scriptName;

    /** 数据类型：STRING/NUMBER/INTEGER/DOUBLE/BOOLEAN/DATE */
    private String fieldType;

    /** 数据用途类型：CONTINUOUS-连续/CATEGORICAL-类别/ORDINAL-有序 */
    private String dataType;

    private String defaultValue;

    /** 统一 Operand JSON：模型字段从哪个引擎字段/路径取值 */
    private String sourceOperand;

    /** 统一 Operand JSON：源值为空时使用的阈值、路径或引用 */
    private String defaultOperand;

    /** 有效值列表（JSON数组） */
    private String validValues;

    /** 模型内部特征名称（如XGBoost的f0） */
    private String featureName;

    /** 预处理类型：NONE/NORMALIZE/DISCRETIZE/MAPVALUES/MINMAX */
    private String transformType;
    private String transformParams;

    private BigDecimal importanceScore;
    private Integer sortOrder;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
