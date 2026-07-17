package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_api_doc_scenario")
public class RuleApiDocScenario {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long definitionId;
    private String scenarioName;
    private String description;
    private String requestJson;
    private String responseJson;
    private String responseSource;
    private Integer outerCode;
    private String businessCodePath;
    private String businessCode;
    private Integer ruleVersion;
    private Integer includeInDoc;
    private Integer sortOrder;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
