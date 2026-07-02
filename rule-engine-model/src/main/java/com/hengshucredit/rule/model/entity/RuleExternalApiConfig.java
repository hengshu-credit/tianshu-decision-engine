package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_external_api_config")
public class RuleExternalApiConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasourceId;
    private String apiCode;
    private String apiName;
    private String requestMethod;
    private String endpointUrl;
    private String contentType;
    private String requestMode;
    private Long requestObjectId;
    private Long responseObjectId;
    private String headerConfig;
    private String queryConfig;
    private String requestMapping;
    private String responseMapping;
    private String bodyTemplate;
    private String authMode;
    private String authApiConfig;
    private Integer tokenCacheSeconds;
    private Integer responseCacheSeconds;
    private Integer timeoutMs;
    private Integer retryCount;
    private Integer retryIntervalMs;
    private String exceptionStrategy;
    private String fallbackValue;
    private String asyncCallbackUrl;
    private String asyncResultPath;
    private String billingItemCode;
    private BigDecimal unitPrice;
    private String description;
    private Integer status;
    @TableField(exist = false)
    private String datasourceName;
    @TableField(exist = false)
    private String datasourceCode;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
