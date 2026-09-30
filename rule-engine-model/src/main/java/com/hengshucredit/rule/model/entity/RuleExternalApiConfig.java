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
    /** V2 请求字段、响应分支、多步协议及测试样例；旧配置为空时沿用原协议。 */
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String executionConfig;
    /** 链路步骤只计入父调用，避免重复生成账单。 */
    @TableField(exist = false)
    private boolean billingSuppressed;
    @TableField(exist = false)
    private String executionCallId;
    private String bodyTemplate;
    private String requestScript;
    private String responseScript;
    /** API 请求/响应诊断报文留存策略 JSON；仅影响分析副本，不改变规则执行结果。 */
    private String payloadCaptureConfig;
    private String authMode;
    private String authApiConfig;
    private Integer tokenCacheSeconds;
    private Integer responseCacheSeconds;
    private String cacheKeyConfig;
    private String successCondition;
    /** 响应异常判断条件树；命中后按 exceptionStrategy 处理。 */
    private String exceptionCondition;
    private Integer timeoutMs;
    /** 异步提交后轮询/回调等待预算；为空时使用服务端默认 30 秒。 */
    private Integer asyncTimeoutMs;
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
    private String tokenFailureCondition;
    private Integer tokenLogEnabled;
    private Integer retryCount;
    /** 是否允许对 POST/PUT/PATCH/DELETE 等非幂等方法自动重试。 */
    private Integer retryNonIdempotent;
    private Integer retryIntervalMs;
    private String retryStatusCodes;
    private Integer retryOnConnectionError;
    private Integer retryOnTimeout;
    private String retryCondition;
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
