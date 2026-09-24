package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.DecisionExecutionPersistence;
import com.hengshucredit.rule.server.service.OpenRuleExecutionExecutor;
import com.hengshucredit.rule.server.service.SourceResolutionExecutor;
import com.hengshucredit.rule.server.service.ExternalApiResponseCache;
import com.hengshucredit.rule.server.service.ExternalApiCircuitBreakerRegistry;
import com.hengshucredit.rule.server.service.DBConnectPools;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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

    @GetMapping("/execution-metrics")
    public R<Map<String, Object>> executionMetrics() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("persistence", executionPersistence.snapshot());
        result.put("sourceResolution", sourceResolutionExecutor.snapshot());
        result.put("openExecution", openRuleExecutionExecutor.snapshot());
        result.put("externalResponseCache", externalApiResponseCache.snapshot());
        result.put("externalCircuitBreakers", externalApiCircuitBreakerRegistry.snapshot());
        result.put("databasePools", dbConnectPools.snapshot());
        return R.ok(result);
    }
}
