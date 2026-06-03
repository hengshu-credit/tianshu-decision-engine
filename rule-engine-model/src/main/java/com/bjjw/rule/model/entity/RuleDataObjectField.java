package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据对象下的字段定义（与 {@link RuleVariable} 分离存储）。
 */
@Data
@TableName("rule_data_object_field")
public class RuleDataObjectField {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属项目ID，0表示全局 */
    private Long projectId;
    /** 作用域：GLOBAL-全局，PROJECT-项目级 */
    private String scope;
    private Long objectId;
    private String varCode;
    private String varLabel;
    private String scriptName;
    private String varType;
    private String refObjectCode;
    private Long parentFieldId;
    private Integer sortOrder;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
