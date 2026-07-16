package com.hengshucredit.rule.client;

import com.hengshucredit.rule.client.cache.CachedRule;
import com.hengshucredit.rule.client.cache.L1MemoryCache;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import org.junit.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleEngineClientTest {

    @Test
    public void localExecutionConvertsNestedAllRulesTerminationToSuccess() throws Exception {
        RecordingReporter reporter = new RecordingReporter();
        RedisConnectionFactory connectionFactory = (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(),
                new Class<?>[]{RedisConnectionFactory.class},
                (proxy, method, args) -> null);
        RuleEngineClient client = RuleEngineClient.builder()
                .connectionFactory(connectionFactory)
                .logReporter(reporter)
                .projectId(1L)
                .build();

        CachedRule root = rule("ROOT", "RULE_SET", "executeRule(\"CHILD\"); "
                + "setRuntimeValue(\"parentAfterChild\", true)");
        root.setOutputScriptNames(Arrays.asList("decision", "notAssigned"));
        CachedRule child = rule("CHILD", "FLOW", "setRuntimeValue(\"decision\", \"STOP\"); "
                + "terminateAllRules(); setRuntimeValue(\"childAfterEnd\", true)");
        L1MemoryCache cache = (L1MemoryCache) getField(client, "l1Cache");
        cache.put(root);
        cache.put(child);

        Map<String, Object> values = new LinkedHashMap<>();
        RuleResult result = client.execute("ROOT", values);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals("STOP", ((Map<?, ?>) result.getResult()).get("decision"));
        assertTrue(((Map<?, ?>) result.getResult()).containsKey("notAssigned"));
        assertFalse(values.containsKey("parentAfterChild"));
        assertFalse(values.containsKey("childAfterEnd"));
        assertEquals(1, reporter.logs.size());
        assertEquals(Integer.valueOf(1), reporter.logs.get(0).getSuccess());
    }

    private CachedRule rule(String code, String modelType, String script) {
        CachedRule rule = new CachedRule();
        rule.setRuleCode(code);
        rule.setProjectCode("P001");
        rule.setModelType(modelType);
        rule.setCompiledScript(script);
        rule.setVersion(1);
        return rule;
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static class RecordingReporter implements com.hengshucredit.rule.client.log.ExecutionLogReporter {
        private List<RuleExecutionLog> logs;

        @Override
        public void report(List<RuleExecutionLog> logs) {
            this.logs = logs;
        }
    }
}
