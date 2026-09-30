package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import org.junit.Test;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.BeforeClass;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuleRuntimeCallLogServiceTest {
    @BeforeClass
    public static void initializeMapping() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""),
                RuleRuntimeCallLog.class);
    }

    @Test
    public void detailMergesEveryAssignmentButAttemptKeepsItsOwnEvidence() {
        RuleRuntimeCallLog summary = traceLog("call-1", "API_INVOKE", "[{\"sequence\":1,\"type\":\"EXTERNAL_RESPONSE\"}]");
        QueryService service = new QueryService(summary, List.of(
                traceLog("call-1", "API_ASSIGNMENT", "[{\"sequence\":1,\"refId\":11,\"value\":0}]"),
                traceLog("call-1", "API_ASSIGNMENT", "[{\"sequence\":1,\"refId\":12,\"value\":false}]")));

        List<?> steps = (List<?>) service.payload(9L).get("traceSteps");
        assertEquals(3, steps.size());
        assertEquals(2, ((Map<?, ?>) steps.get(1)).get("sequence"));
        assertEquals(0, ((Map<?, ?>) steps.get(1)).get("value"));
        assertEquals(3, ((Map<?, ?>) steps.get(2)).get("sequence"));
        assertEquals(false, ((Map<?, ?>) steps.get(2)).get("value"));
        assertEquals(1, service.queries.size());
        assertTrue(service.queries.get(0).getParamNameValuePairs().containsValue(5L));

        summary.setActionType("API_ATTEMPT");
        QueryService attempt = new QueryService(summary);
        assertEquals(1, ((List<?>) attempt.payload(9L).get("traceSteps")).size());
        assertEquals(0, attempt.queries.size());
    }

    @Test
    public void latestSummaryUsesDescendingLimitAndProjectsAssignments() {
        RuleRuntimeCallLog summary = traceLog("call-1", "API_INVOKE", "[]");
        QueryService service = new QueryService(null, List.of(summary), List.of());
        assertEquals("call-1", service.payloadByCallId("call-1", 5L).get("callId"));
        assertEquals(2, service.queries.size());
        assertTrue(service.queries.get(0).getSqlSegment().contains("LIMIT 1"));
        assertTrue(service.queries.get(1).getParamNameValuePairs().containsValue(5L));
    }

    @Test
    public void rootTraceBatchesAssignmentsAndPreservesHistoricalRows() {
        RuleRuntimeCallLog first = traceLog("first", "API_INVOKE", "[]");
        first.setRuleTraceId("root");
        RuleRuntimeCallLog second = traceLog("second", "API_INVOKE", "[]");
        second.setTraceId("root");
        RuleRuntimeCallLog historical = traceLog(null, "API_INVOKE", null);
        QueryService service = new QueryService(null, List.of(first, second, historical), List.of(
                traceLog("second", "API_ASSIGNMENT", "[{\"sequence\":1,\"refId\":12,\"value\":false}]")));
        List<Map<String, Object>> result = service.payloadsByRootTraceId("root", 5L);
        assertEquals(3, result.size());
        assertEquals(2, service.queries.size());
        assertEquals(0, ((List<?>) result.get(0).get("traceSteps")).size());
        assertEquals(1, ((List<?>) result.get(1).get("traceSteps")).size());
        assertEquals(0, ((List<?>) result.get(2).get("traceSteps")).size());
        assertTrue(service.queries.get(1).getParamNameValuePairs().containsValue(5L));
    }

    @Test
    public void rootTraceQueryMatchesAnyPersistedTraceIdentity() {
        RuleRuntimeCallLog summary = traceLog("call-trace", "API_INVOKE", "[]");
        summary.setTraceId("root-trace");
        QueryService service = new QueryService(null, List.of(summary), List.of());

        List<Map<String, Object>> result = service.payloadsByRootTraceId("root-trace", 5L);

        assertEquals(1, result.size());
        assertTrue(service.queries.get(0).getSqlSegment().contains("OR"));
        assertTrue(service.queries.get(0).getParamNameValuePairs().containsValue("root-trace"));
        assertTrue(service.queries.get(0).getParamNameValuePairs().containsValue(5L));
    }

    private static RuleRuntimeCallLog traceLog(String callId, String action, String steps) {
        RuleRuntimeCallLog log = new RuleRuntimeCallLog();
        log.setId(9L);
        log.setProjectId(5L);
        log.setModuleType("DATASOURCE");
        log.setActionType(action);
        log.setCallId(callId);
        log.setTraceSteps(steps);
        return log;
    }

    private static class QueryService extends RuleRuntimeCallLogService {
        final RuleRuntimeCallLog detail;
        final ArrayDeque<List<RuleRuntimeCallLog>> results = new ArrayDeque<>();
        final List<LambdaQueryWrapper<RuleRuntimeCallLog>> queries = new ArrayList<>();

        @SafeVarargs
        QueryService(RuleRuntimeCallLog detail, List<RuleRuntimeCallLog>... results) {
            this.detail = detail;
            this.results.addAll(Arrays.asList(results));
        }

        @Override
        public RuleRuntimeCallLog getById(java.io.Serializable id) { return detail; }

        @Override
        public List<RuleRuntimeCallLog> list(Wrapper<RuleRuntimeCallLog> query) {
            LambdaQueryWrapper<RuleRuntimeCallLog> wrapper = (LambdaQueryWrapper<RuleRuntimeCallLog>) query;
            wrapper.getSqlSegment();
            queries.add(wrapper);
            return results.removeFirst();
        }
    }

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

    @Test
    public void payloadProjectionReturnsRawBodiesAndStableKeys() {
        RuleRuntimeCallLog source = new RuleRuntimeCallLog();
        source.setId(9L);
        source.setCallId("call-9");
        source.setRootTraceId("root-9");
        source.setTargetCode("credit_score");
        source.setRawRequestBody("{\"age\":18}");
        source.setOriginalRequestBody("{\"age\":18,\"encrypted\":true}");
        source.setRawRequestMetadata("{\"status\":\"FILTERED\",\"omittedPaths\":[\"$.file\"]}");
        source.setRawResponseBody("{\"score\":720}");
        source.setOriginalResponseBody("{\"ciphertext\":\"abc\"}");
        source.setRawResponseMetadata("{\"status\":\"DECRYPTED\"}");
        RuleRuntimeCallLogService service = new RuleRuntimeCallLogService() {
            @Override
            public RuleRuntimeCallLog getById(java.io.Serializable id) {
                return source;
            }
        };

        Map<String, Object> payload = service.payload(9L);

        assertEquals("call-9", payload.get("callId"));
        assertEquals("{\"age\":18}", payload.get("requestBody"));
        assertEquals("{\"score\":720}", payload.get("responseBody"));
        assertEquals("{\"age\":18,\"encrypted\":true}", payload.get("originalRequestBody"));
        assertEquals("{\"ciphertext\":\"abc\"}", payload.get("originalResponseBody"));
        assertTrue(Boolean.TRUE.equals(payload.get("originalRequestAvailable")));
        assertTrue(Boolean.TRUE.equals(payload.get("originalResponseAvailable")));
        assertTrue(String.valueOf(payload.get("rawRequestMetadata")).contains("FILTERED"));
        assertTrue(String.valueOf(payload.get("rawResponseMetadata")).contains("DECRYPTED"));
        assertTrue(Boolean.TRUE.equals(payload.get("rawRequestAvailable")));
        assertTrue(Boolean.TRUE.equals(payload.get("rawResponseAvailable")));
        assertTrue(Boolean.TRUE.equals(payload.get("rawPayloadAvailable")));
    }

    @Test
    public void payloadByIdCanEnforceProjectScope() {
        RuleRuntimeCallLog source = traceLog("call-scope", "API_INVOKE", "[]");
        RuleRuntimeCallLogService service = new RuleRuntimeCallLogService() {
            @Override
            public RuleRuntimeCallLog getById(java.io.Serializable id) {
                return source;
            }

            @Override
            public List<RuleRuntimeCallLog> list(Wrapper<RuleRuntimeCallLog> query) {
                return List.of();
            }
        };

        assertTrue(service.payload(9L, 5L).containsKey("callId"));
        assertTrue(service.payload(9L, 6L).isEmpty());
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
