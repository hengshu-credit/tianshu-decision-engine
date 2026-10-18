package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("rule_experiment")
public class RuleExperiment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String projectCode;
    private String experimentCode;
    private String experimentName;
    private String description;
    private String routingMode;
    private String testRoutingMode;
    private String conditionRuleCode;
    private String requestKeyPath;
    /** 输入字段校验绑定：稳定字段键（refType:refId 或 PATH:path）到规则 ID 数组。 */
    private String inputValidationConfig;
    private Integer testExclusive;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<RuleExperimentGroup> groups;
}
