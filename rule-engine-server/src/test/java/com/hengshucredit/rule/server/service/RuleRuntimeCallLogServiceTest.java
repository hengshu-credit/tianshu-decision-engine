package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RuleRuntimeCallLogServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void externalApiStatsUsePersistedProviderCacheAndConditionResults() {
        List<RuleRuntimeCallLog> logs = Arrays.asList(
                log(1L, "vendor_a", 1, "MISS", 1, 1, 100L),
                log(1L, "vendor_a", 1, "MISS", 0, 0, 200L),
                log(1L, "vendor_a", 0, "HIT", 1, 1, 0L),
                log(1L, "vendor_a", 1, "CACHE_KEY_INCOMPLETE", 1, 1, 300L),
                log(2L, "vendor_b", 1, "MISS", 1, 1, 50L));

        Map<String, Object> result = new RuleRuntimeCallLogService().buildExternalApiStats(logs);
        Map<String, Object> overview = (Map<String, Object>) result.get("overview");
        List<Map<String, Object>> providers = (List<Map<String, Object>>) result.get("providers");

        assertEquals(5L, overview.get("totalInvocations"));
        assertEquals(4L, overview.get("queryCount"));
        assertEquals(3L, overview.get("requestSuccessCount"));
        assertEquals(3L, overview.get("foundCount"));
        assertEquals(1L, overview.get("cacheHitCount"));
        assertEquals(3L, overview.get("cacheMissCount"));
        assertEquals(1L, overview.get("cacheKeyIncompleteCount"));
        assertEquals(0.25D, (Double) overview.get("cacheHitRate"), 0.0001D);
        assertEquals(0.75D, (Double) overview.get("requestSuccessRate"), 0.0001D);
        assertEquals(0.25D, (Double) overview.get("failureRate"), 0.0001D);
        assertEquals(0.75D, (Double) overview.get("foundRate"), 0.0001D);
        assertEquals(162.5D, (Double) overview.get("avgCostTimeMs"), 0.0001D);
        assertEquals(300L, overview.get("p95CostTimeMs"));
        assertEquals(300L, overview.get("p99CostTimeMs"));
        assertEquals(2, providers.size());
        assertEquals("vendor_a", providers.get(0).get("targetCode"));
        assertEquals(3L, providers.get(0).get("queryCount"));
    }

    private RuleRuntimeCallLog log(Long targetId, String targetCode, int providerRequest, String cacheStatus,
                                   int requestSuccess, int found, Long costTimeMs) {
        RuleRuntimeCallLog log = new RuleRuntimeCallLog();
        log.setTargetRefId(targetId);
        log.setTargetCode(targetCode);
        log.setTargetName(targetCode);
        log.setProviderRequest(providerRequest);
        log.setCacheStatus(cacheStatus);
        log.setRequestSuccess(requestSuccess);
        log.setFound(found);
        log.setCostTimeMs(costTimeMs);
        return log;
    }
}
