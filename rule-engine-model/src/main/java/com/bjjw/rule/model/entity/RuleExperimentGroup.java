package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_experiment_group")
public class RuleExperimentGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long experimentId;
    private String groupCode;
    private String groupName;
    private String groupType;
    private String ruleCode;
    private BigDecimal trafficRatio;
    private String conditionValue;
    private String conditionExpression;
    private Integer invokeExternalSource;
    private Integer status;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
