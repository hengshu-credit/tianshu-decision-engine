package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_function")
public class RuleFunction {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属项目ID，0表示全局 */
    private Long projectId;
    /** 作用域：GLOBAL-全局，PROJECT-项目级 */
    private String scope;
    private String funcCode;
    private String funcName;
    private String description;
    private String paramsJson;
    private String returnType;
    private String implType;
    private String implScript;
    private String implClass;
    /** 方法名（JAVA/BEAN 类型时指定） */
    private String implMethod;
    /** Spring Bean 名称（BEAN 类型时指定） */
    private String implBeanName;
    private Integer status;
    /** 所属项目名称（非DB字段，查询时由Service层填充） */
    @TableField(exist = false)
    private String projectName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
