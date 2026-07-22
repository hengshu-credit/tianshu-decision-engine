package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RulePublished;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RuleExecutionLogServiceTest {

    @Test
    public void keepsClientArtifactAttributionWhenAsyncReportArrivesAfterNewPublish() {
        RuleExecutionLogService service = new RuleExecutionLogService() {
            @Override
            protected RulePublished findPublished(String ruleCode, String projectCode) {
                RulePublished current = new RulePublished();
                current.setVersion(8);
                current.setRevisionId(88L);
                current.setArtifactDigest("current-digest");
                return current;
            }
        };
        RuleExecutionLog log = new RuleExecutionLog();
        log.setRuleCode("risk_rule");
        log.setRuleVersion(7);
        log.setRevisionId(77L);
        log.setArtifactDigest("executed-digest");

        service.applyPublishedAttribution(log, "project-a");

        assertEquals(Integer.valueOf(7), log.getRuleVersion());
        assertEquals(Long.valueOf(77L), log.getRevisionId());
        assertEquals("executed-digest", log.getArtifactDigest());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void ruleSetStatsIncludeRootNestedSetsAndEveryActuallyEvaluatedRuleOnly() {
        RuleExecutionLog first = log("[{\"modelType\":\"FLOW\",\"children\":["
                + "{\"ruleId\":10,\"ruleCode\":\"RS-A\",\"ruleName\":\"准入规则集\",\"modelType\":\"RULE_SET\",\"status\":\"SUCCESS\",\"durationMs\":100,\"events\":["
                + "{\"type\":\"RULE_SET_ITEM\",\"ruleCode\":\"A-1\",\"ruleName\":\"年龄准入\",\"evaluated\":true,\"hit\":true},"
                + "{\"type\":\"RULE_SET_ITEM\",\"ruleCode\":\"A-2\",\"ruleName\":\"评分准入\",\"evaluated\":true,\"hit\":false},"
                + "{\"type\":\"RULE_SET_SUMMARY\",\"evaluated\":true,\"hit\":true}],\"children\":["
                + "{\"ruleId\":11,\"ruleCode\":\"RS-B\",\"ruleName\":\"子规则集\",\"modelType\":\"RULE_SET\",\"status\":\"SUCCESS\",\"durationMs\":50,\"events\":["
                + "{\"type\":\"RULE_SET_ITEM\",\"ruleCode\":\"B-1\",\"ruleName\":\"名单规则\",\"evaluated\":true,\"hit\":false},"
                + "{\"type\":\"RULE_SET_SUMMARY\",\"evaluated\":true,\"hit\":false}]}]},"
                + "{\"modelType\":\"TABLE\",\"events\":[{\"type\":\"RULE_SET_ITEM\",\"hit\":true}]}]}]");
        RuleExecutionLog second = log("[{\"ruleId\":10,\"ruleCode\":\"RS-A\",\"ruleName\":\"准入规则集\",\"modelType\":\"RULE_SET\",\"status\":\"FAILED\",\"durationMs\":200,\"events\":["
                + "{\"type\":\"RULE_SET_ITEM\",\"ruleCode\":\"A-1\",\"ruleName\":\"年龄准入\",\"evaluated\":true,\"hit\":false},"
                + "{\"type\":\"RULE_SET_SUMMARY\",\"evaluated\":true,\"hit\":false}]}]");

        Map<String, Object> result = new RuleExecutionLogService().buildRuleSetStats(Arrays.asList(first, second), null);
        Map<String, Object> overview = (Map<String, Object>) result.get("overview");
        List<Map<String, Object>> ruleSets = (List<Map<String, Object>>) result.get("ruleSets");
        Map<String, Object> setA = ruleSets.get(0);
        List<Map<String, Object>> items = (List<Map<String, Object>>) setA.get("items");

        assertEquals(3L, overview.get("evaluationCount"));
        assertEquals(1L, overview.get("hitCount"));
        assertEquals(1L, overview.get("failureCount"));
        assertEquals(2, ruleSets.size());
        assertEquals("RS-A", setA.get("ruleCode"));
        assertEquals(2L, setA.get("evaluationCount"));
        assertEquals(1L, setA.get("hitCount"));
        assertEquals(0.5D, (Double) setA.get("hitRate"), 0.0001D);
        assertEquals(0.5D, (Double) setA.get("failureRate"), 0.0001D);
        assertEquals(200L, setA.get("p95CostTimeMs"));
        assertEquals(2, items.size());
        assertEquals("A-1", items.get(0).get("ruleCode"));
        assertEquals(2L, items.get(0).get("evaluationCount"));
        assertEquals(1L, items.get(0).get("hitCount"));
        assertEquals(1L, items.get(1).get("evaluationCount"));
    }

    private RuleExecutionLog log(String traceInfo) {
        RuleExecutionLog log = new RuleExecutionLog();
        log.setTraceInfo(traceInfo);
        return log;
    }
}
