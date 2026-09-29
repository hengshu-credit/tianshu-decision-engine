package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.DecisionExecutionPersistence;
import com.hengshucredit.rule.server.service.OpenRuleExecutionExecutor;
import com.hengshucredit.rule.server.service.SourceResolutionExecutor;
import com.hengshucredit.rule.server.service.ExternalApiResponseCache;
import com.hengshucredit.rule.server.service.ExternalApiCircuitBreakerRegistry;
import com.hengshucredit.rule.server.service.DBConnectPools;
import com.hengshucredit.rule.server.service.RuleScriptPreparationService;
import com.hengshucredit.rule.server.service.RulePublishOutboxService;
import com.hengshucredit.rule.server.service.RuleExecutionPersistenceOutboxService;
import com.hengshucredit.rule.server.service.LogRetentionService;
import com.hengshucredit.rule.server.health.RuleWarmupStatus;
import com.hengshucredit.rule.server.security.RequirePermission;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;
import java.lang.management.ManagementFactory;

/** 管理端查看规则执行持久化队列和写入延迟。 */
@RestController
@RequestMapping("/api/rule/ops")
public class ExecutionMetricsController {
    @Resource
    private DecisionExecutionPersistence executionPersistence;
    @Resource
    private SourceResolutionExecutor sourceResolutionExecutor;
    @Resource
    private OpenRuleExecutionExecutor openRuleExecutionExecutor;
    @Resource
    private ExternalApiResponseCache externalApiResponseCache;
    @Resource
    private ExternalApiCircuitBreakerRegistry externalApiCircuitBreakerRegistry;
    @Resource
    private DBConnectPools dbConnectPools;
    @Resource
    private RuleWarmupStatus ruleWarmupStatus;
    @Resource
    private RuleScriptPreparationService ruleScriptPreparationService;
    @Resource
    private RulePublishOutboxService rulePublishOutboxService;
    @Resource
    private RuleExecutionPersistenceOutboxService executionPersistenceOutboxService;
    @Resource
    private LogRetentionService logRetentionService;
    @Value("${RULE_ENGINE_INSTANCE_ID:}")
    private String configuredInstanceId;

    @GetMapping("/execution-metrics")
    @RequirePermission("rule:view")
    public R<Map<String, Object>> executionMetrics() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("instanceId", instanceId());
        result.put("observedAt", java.time.Instant.now());
        result.put("persistence", executionPersistence.snapshot());
        result.put("sourceResolution", sourceResolutionExecutor.snapshot());
        result.put("openExecution", openRuleExecutionExecutor.snapshot());
        result.put("externalResponseCache", externalApiResponseCache.snapshot());
        result.put("externalCircuitBreakers", externalApiCircuitBreakerRegistry.snapshot());
        result.put("databasePools", dbConnectPools.snapshot());
        result.put("ruleWarmup", ruleWarmupStatus.details());
        result.put("publishOutbox", rulePublishOutboxService.snapshot());
        result.put("executionPersistenceOutbox", executionPersistenceOutboxService.snapshot());
        result.put("logRetention", logRetentionService == null ? java.util.Collections.emptyMap()
                : logRetentionService.snapshot());
        return R.ok(result);
    }

    private String instanceId() {
        if (configuredInstanceId != null && !configuredInstanceId.isBlank()) return configuredInstanceId;
        return ManagementFactory.getRuntimeMXBean().getName();
    }

    /** 修复发布规则或函数后，允许运维在不重启服务的情况下重新验证预热。 */
    @org.springframework.web.bind.annotation.PostMapping("/rule-warmup/retry")
    @RequirePermission("rule:submit")
    public R<Map<String, Object>> retryRuleWarmup() {
        return R.ok(ruleScriptPreparationService.warmupPublishedRules());
    }
}
