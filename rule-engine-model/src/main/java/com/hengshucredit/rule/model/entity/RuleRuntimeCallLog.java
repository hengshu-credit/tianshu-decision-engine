package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_runtime_call_log")
public class RuleRuntimeCallLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private String ruleTraceId;
    private String moduleType;
    private String actionType;
    private Long projectId;
    private String projectCode;
    private Long targetRefId;
    private String targetCode;
    private String targetName;
    private Integer success;
    private String requestMethod;
    private String requestUrl;
    private String requestHeaders;
    private String requestParams;
    private String requestBody;
    private Integer responseStatus;
    private String responseBody;
    private String errorMessage;
    private Long costTimeMs;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
