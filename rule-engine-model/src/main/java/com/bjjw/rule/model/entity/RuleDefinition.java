package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("rule_engine.rule_definition")
public class RuleDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String projectCode;
    private String projectName;
    private String ruleCode;
    private String ruleName;
    private String modelType;
    private String description;
    private String scope;
    private Integer currentVersion;
    private Integer publishedVersion;
    private Integer status;
    /** JSON 字符串（旧格式，迁移后可忽略）；优先读取 inputFieldsJson / outputFieldsJson */
    private String inputFields;
    private String outputFields;
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ===== 以下为非数据库字段 =====

    /** 输入字段列表（从 rule_definition_input_field 表读取） */
    @TableField(exist = false)
    private List<RuleDefinitionInputField> inputFieldsJson;

    /** 输出字段列表（从 rule_definition_output_field 表读取） */
    @TableField(exist = false)
    private List<RuleDefinitionOutputField> outputFieldsJson;
}
