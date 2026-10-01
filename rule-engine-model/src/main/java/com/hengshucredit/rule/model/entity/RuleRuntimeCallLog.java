package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_runtime_call_log")
public class RuleRuntimeCallLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    /** 一次逻辑外数调用的稳定关联 ID；重试 attempt 共用。 */
    private String callId;
    private String ruleTraceId;
    private String rootTraceId;
    /** 本次逻辑调用的受管 API 字段结果快照，重试尝试日志不参与统计。 */
    private String historyFields;
    private String moduleType;
    private String actionType;
    private Long projectId;
    private String projectCode;
    private Long datasourceId;
    private String requestId;
    private Long targetRefId;
    private String targetCode;
    private String targetName;
    private Integer success;
    private Integer requestSuccess;
    private Integer found;
    private Integer providerRequest;
    private String cacheStatus;
    private String cacheKey;
    private Integer attemptNo;
    private String circuitState;
    private String tokenCacheStatus;
    private String requestMethod;
    private String requestUrl;
    private String requestHeaders;
    private String requestParams;
    private String requestBody;
    /** 原始请求体，不做字段脱敏；仅通过受控 payload 查询接口返回。 */
    private String rawRequestBody;
    /** 原始请求留存策略的来源、解密和字段排除结果。 */
    private String rawRequestMetadata;
    /** 供应商实际收到的原始请求体，永远不被解密/裁剪副本覆盖。 */
    private String originalRequestBody;
    /** 外数调用阶段链路JSON，仅保存脱敏后的分析副本。 */
    private String traceSteps;
    private Integer responseStatus;
    private String responseBody;
    /** 上游原始响应体，不是 response script 或映射后的 body。 */
    private String rawResponseBody;
    /** 原始响应留存策略的来源、解密和字段排除结果。 */
    private String rawResponseMetadata;
    /** 供应商实际返回的原始响应体，永远不被解密/裁剪副本覆盖。 */
    private String originalResponseBody;
    private String errorType;
    private String errorMessage;
    private Long costTimeMs;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
