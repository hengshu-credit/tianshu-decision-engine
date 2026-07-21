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
    private String requestScript;
    private String responseScript;
    private String authMode;
    private String authApiConfig;
    private Integer tokenCacheSeconds;
    private Integer responseCacheSeconds;
    private String cacheKeyConfig;
    private String successCondition;
    private Integer timeoutMs;
    private Integer maxConnections;
    private Integer maxConnectionsPerRoute;
    private Integer connectionRequestTimeoutMs;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private Integer idleConnectionTimeoutSeconds;
    private Integer connectionTtlSeconds;
    private BigDecimal qpsLimit;
    private Integer burstCapacity;
    private Integer maxConcurrent;
    private Integer concurrentWaitTimeoutMs;
    private Integer tokenRefreshAheadSeconds;
    private Integer tokenRefreshOnUnauthorized;
    private Integer tokenLogEnabled;
    private Integer retryCount;
    private Integer retryIntervalMs;
    private String retryStatusCodes;
    private Integer retryOnConnectionError;
    private Integer retryOnTimeout;
    private BigDecimal retryBackoffMultiplier;
    private Integer retryMaxIntervalMs;
    private Integer circuitBreakerEnabled;
    private Integer circuitFailureRate;
    private Integer circuitMinCalls;
    private Integer circuitWindowSize;
    private Integer circuitOpenSeconds;
    private Integer circuitHalfOpenCalls;
    private String exceptionStrategy;
    private String fallbackValue;
    private Integer responseCacheMaxSize;
    private Integer responseCacheMaxBytes;
    private Integer responseCacheRedisEnabled;
    private Integer staleCacheSeconds;
    private String asyncResultMode;
    private String asyncPollConfig;
    private String asyncCallbackConfig;
    private String asyncCallbackUrl;
    private String asyncResultPath;
    private String billingItemCode;
    private String billingCondition;
    private BigDecimal unitPrice;
    private String description;
    private String testSampleParams;
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
