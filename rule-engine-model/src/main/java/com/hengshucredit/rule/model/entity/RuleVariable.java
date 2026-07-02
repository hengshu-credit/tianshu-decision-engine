package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_variable")
public class RuleVariable {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属项目ID，0表示全局 */
    private Long projectId;
    /** 作用域：GLOBAL-全局，PROJECT-项目级 */
    private String scope;
    private String varCode;
    private String varLabel;
    /** 脚本中的变量名（默认驼峰，如 totalAmount） */
    private String scriptName;
    private String varType;
    private String varSource;
    /** API/DB 等外部来源的绑定配置JSON */
    private String sourceConfig;
    private String defaultValue;
    private String valueRange;
    private String exampleValue;
    private String description;
    private Integer sortOrder;
    private Integer status;
    /** 所属项目名称（非DB字段，查询时由Service层填充） */
    @TableField(exist = false)
    private String projectName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
