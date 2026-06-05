package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("rule_engine.rule_model")
public class RuleModel {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目ID（全局模型可为空） */
    private Long projectId;
    private String projectCode;
    private String projectName;

    /** 作用范围：GLOBAL-全局，PROJECT-项目级 */
    private String scope;

    /** 模型基本信息 */
    private String modelCode;
    private String modelName;
    /** 模型大类：CLASSIFICATION-分类/REGRESSION-回归/CLUSTERING-聚类/ML-机器学习 */
    private String modelType;
    /** 模型格式：PMML/ONNX/TENSORFLOW/LIGHTGBM/PICKLE */
    private String modelFormat;
    private String description;

    /** 文件信息 */
    private String modelContent;
    private String modelFileName;
    private Long modelFileSize;

    /** 格式特有配置（JSON） */
    private String modelConfig;

    /** 字段统计 */
    private Integer inputFieldCount;
    private Integer outputFieldCount;
    private String targetCategories;

    /** 训练信息（JSON） */
    private String trainingInfo;

    /** 版本 */
    private String modelVersion;
    private Integer currentVersion;
    private Integer publishedVersion;

    /** 状态 */
    private Integer status;
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 非DB字段 */
    @TableField(exist = false)
    private List<RuleModelInputField> inputFields;

    @TableField(exist = false)
    private List<RuleModelOutputField> outputFields;
}