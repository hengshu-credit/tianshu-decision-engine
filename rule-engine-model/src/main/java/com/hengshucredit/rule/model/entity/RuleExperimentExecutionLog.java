package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_experiment_execution_log")
public class RuleExperimentExecutionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String experimentTraceId;
    private String childTraceId;
    private Long experimentId;
    private String experimentCode;
    private String requestKey;
    private String stage;
    private Long groupId;
    private String groupCode;
    private String groupName;
    private String groupType;
    private String ruleCode;
    private String routeReason;
    private Integer success;
    private String inputParams;
    private String outputResult;
    private String traceInfo;
    private String errorMessage;
    private Long executeTimeMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
