package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_trace_registry")
public class RuleTraceRegistry {
    @TableId
    private String traceId;
    private String traceType;
    private String scopeType;
    private String scopeCode;
    private Long projectId;
    private String resourceType;
    private Long resourceId;
    private String resourceCode;
    private String parentTraceId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
