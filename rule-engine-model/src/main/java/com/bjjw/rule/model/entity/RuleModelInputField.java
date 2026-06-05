package com.bjjw.rule.model.entity;

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

    /** 字段名称（原始名称） */
    private String fieldName;
    private String fieldLabel;
    private String scriptName;

    /** 数据类型：STRING/NUMBER/INTEGER/DOUBLE/BOOLEAN/DATE */
    private String fieldType;

    /** 数据用途类型：CONTINUOUS-连续/CATEGORICAL-类别/ORDINAL-有序 */
    private String dataType;

    /** 缺失值处理策略 */
    private String missingValue;
    private String defaultValue;

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