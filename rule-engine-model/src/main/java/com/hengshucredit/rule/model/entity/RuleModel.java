package com.hengshucredit.rule.model.entity;

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
    /** 模型大类：LR-逻辑回归/XGBOOST-XGBoost/LIGHTGBM-LightGBM/CATBOOST-CatBoost/RANDOM_FOREST-RandomForest/NEURAL_NET-神经网络/SVM-SVM */
    private String modelType;
    /** 模型格式：PMML/ONNX/TENSORFLOW/LIGHTGBM/PICKLE */
    private String modelFormat;
    private String description;

    /** 文件信息 */
    private String modelContent;
    private String modelFileName;
    private Long modelFileSize;
    private String modelDigest;
    private String inputSchemaJson;
    private String outputSchemaJson;
    private String validationReportJson;
    private String runtimeConstraintsJson;

    /** 格式特有配置（JSON） */
    private String modelConfig;

    /** 是否在服务启动时预加载模型：0-否，1-是 */
    private Integer preloadOnStartup;
    /** 单次模型执行超时时间（毫秒） */
    private Integer executionTimeoutMs;

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
