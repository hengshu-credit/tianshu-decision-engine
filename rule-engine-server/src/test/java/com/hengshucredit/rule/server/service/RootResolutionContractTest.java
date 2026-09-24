package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.derived.DerivedVariableService;
import org.junit.After;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class RootResolutionContractTest {
    @After public void cleanup() { RuntimeContextBridge.clear(); }

    @Test
    public void derivedNullIsComputedOnceAcrossChildScopesAndCallerValuesAreNotTrusted() {
        var derived = variable(11, "derived", "DERIVED",
                "{\"mode\":\"EXPRESSION\",\"expression\":{\"kind\":\"LITERAL\",\"value\":0,\"valueType\":\"NUMBER\"}}");
        AtomicInteger calls = new AtomicInteger();
        VariableSourceResolver resolver = new VariableSourceResolver();
        ReflectionTestUtils.setField(resolver, "derivedVariableService", new DerivedVariableService() {
            @Override public Object resolve(RuleVariable variable, Map<String, Object> values,
                    Map<String, String> paths, Map<Long, RuleFunction> functions,
                    Map<String, com.hengshucredit.rule.server.derived.HistoricalFieldDefinition> definitions) {
                calls.incrementAndGet();
                return null;
            }
        });
        var cache = new VariableResolutionInvocationCache();
        var values = new LinkedHashMap<String, Object>(Map.of("derived", 999));
        resolver.resolveIntoSnapshot(List.of(derived), List.of(), List.of(), values, options(cache, "derived"));
        assertTrue(values.containsKey("derived"));
        assertNull(values.get("derived"));
        values.put("derived", 888);
        resolver.resolveIntoSnapshot(List.of(derived), List.of(), List.of(), values, options(cache, "derived"));
        assertNull(values.get("derived"));
        assertEquals("父子规则复用首次的空结果", 1, calls.get());
        resolver.resolveIntoSnapshot(List.of(derived), List.of(), List.of(), new LinkedHashMap<>(), options(new VariableResolutionInvocationCache(), "derived"));
        assertEquals("下一次根请求重新计算", 2, calls.get());
    }

    @Test
    public void sameApiKeyIsReusedButChangedKeyCallsAgain() {
        var first = variable(11, "score", "API", "{\"apiConfigId\":7,\"paramMapping\":{\"id\":\"$.customerId\"},\"resultPath\":\"body.score\"}");
        var second = variable(12, "limit", "API", "{\"apiConfigId\":7,\"paramMapping\":{\"id\":\"$.customerId\"},\"resultPath\":\"body.limit\"}");
        AtomicInteger calls = new AtomicInteger();
        VariableSourceResolver resolver = new VariableSourceResolver();
        ReflectionTestUtils.setField(resolver, "externalApiInvokeService", new ExternalApiInvokeService() {
            @Override public Map<String, Object> invoke(Long id, Map<String, Object> params) {
                calls.incrementAndGet();
                return Map.of("body", Map.of("score", 88, "limit", 100));
            }
        });
        var cache = new VariableResolutionInvocationCache();
        var values = new LinkedHashMap<String, Object>(Map.of("customerId", "first", "score", 999));
        resolver.resolveIntoSnapshot(List.of(first, second), List.of(), List.of(), values, options(cache, "score"));
        assertEquals(88, values.get("score"));
        values.put("customerId", "changed");
        resolver.resolveIntoSnapshot(List.of(first, second), List.of(), List.of(), values, options(cache, "limit"));
        assertEquals(100, values.get("limit"));
        assertEquals("参数变化后调用键变化，应重新调用外数API", 2, calls.get());
    }

    @Test
    public void modelIsComputedOnceEvenWhenSourceStatusIsReadInAnotherChild() {
        RuleModel model = new RuleModel();
        model.setId(22L); model.setModelCode("riskModel"); model.setModelContent("frozen");
        model.setInputFields(List.of()); model.setOutputFields(List.of());
        AtomicInteger calls = new AtomicInteger();
        VariableSourceResolver resolver = new VariableSourceResolver();
        ReflectionTestUtils.setField(resolver, "ruleModelService", new RuleModelService() {
            @Override public Map<String, Object> executeSnapshot(RuleModel frozen, Map<String, Object> params, Map<Long, RuleFunction> functions) {
                calls.incrementAndGet();
                return Map.of("success", true, "outputs", Map.of("score", 88));
            }
        });
        var cache = new VariableResolutionInvocationCache();
        var values = new LinkedHashMap<String, Object>(Map.of("riskModel", Map.of("score", 999)));
        resolver.resolveIntoSnapshot(List.of(), List.of(model), List.of(), values, options(cache, "riskModel"));
        assertEquals(Map.of("score", 88), values.get("riskModel"));
        var child = options(cache, "riskModel");
        child.setForceRefreshSource(true);
        resolver.resolveIntoSnapshot(List.of(), List.of(model), List.of(), values, child);
        assertEquals(1, calls.get());
    }

    @Test
    public void listVariableReusesFirstMatchAcrossChildrenEvenWithForceRefresh() {
        var list = variable(11, "listHit", "LIST", "{\"listIds\":[9],\"queryOperands\":[{\"kind\":\"PATH\",\"value\":\"mobile\"}],"
                + "\"combinationMode\":\"ANY_FIELD_ANY_LIST\",\"matchMode\":\"IN_LIST\",\"itemTypes\":[\"MOBILE\"],\"returnMode\":\"NUMBER\"}");
        AtomicInteger calls = new AtomicInteger();
        VariableSourceResolver resolver = new VariableSourceResolver();
        ReflectionTestUtils.setField(resolver, "ruleListService", new RuleListService() {
            @Override public boolean matchAt(Long id, Object value, List<String> types, String mode, java.time.LocalDateTime time) {
                calls.incrementAndGet();
                return "first".equals(value);
            }
        });
        var cache = new VariableResolutionInvocationCache();
        var values = new LinkedHashMap<String, Object>(Map.of("mobile", "first", "listHit", 0));
        resolver.resolveIntoSnapshot(List.of(list), List.of(), List.of(), values, options(cache, "listHit"));
        assertEquals(1, values.get("listHit"));
        values.put("mobile", "changed");
        var child = options(cache, "listHit"); child.setForceRefreshSource(true);
        resolver.resolveIntoSnapshot(List.of(list), List.of(), List.of(), values, child);
        assertEquals(1, values.get("listHit"));
        assertEquals(1, calls.get());
    }

    @Test
    public void failedApiIsNotRetriedByAnotherChildAndStatusReadsDoNotEraseFailure() {
        var api = variable(11, "score", "API", "{\"apiConfigId\":7,\"resultPath\":\"body.score\"}");
        AtomicInteger calls = new AtomicInteger();
        VariableSourceResolver resolver = new VariableSourceResolver();
        ReflectionTestUtils.setField(resolver, "externalApiInvokeService", new ExternalApiInvokeService() {
            @Override public Map<String, Object> invoke(Long id, Map<String, Object> values) {
                calls.incrementAndGet(); throw new IllegalStateException("provider unavailable");
            }
        });
        var cache = new VariableResolutionInvocationCache();
        var values = new LinkedHashMap<String, Object>();
        var status = options(cache, "score"); status.setStatusReferenceKeys(Set.of("VARIABLE:11"));
        resolver.resolveIntoSnapshot(List.of(api), List.of(), List.of(), values, status);
        assertNull(values.get("score"));
        assertEquals("ERROR", status.getSourceStates().get("VARIABLE:11").get("OUTCOME"));
        assertThrows(IllegalStateException.class, () -> resolver.resolveIntoSnapshot(List.of(api), List.of(), List.of(), values, options(cache, "score")));
        assertEquals(1, calls.get());
    }

    private static VariableResolveOptions options(VariableResolutionInvocationCache cache, String required) {
        var options = VariableResolveOptions.defaults();
        options.setInvocationCache(cache);
        options.setRequiredScriptNames(Set.of(required));
        options.setRequiredNamesUpstreamOnly(true);
        return options;
    }

    private static RuleVariable variable(long id, String name, String source, String config) {
        var variable = new RuleVariable();
        variable.setId(id); variable.setVarCode(name); variable.setScriptName(name);
        variable.setVarSource(source); variable.setSourceConfig(config); variable.setVarType("NUMBER");
        variable.setStatus(1);
        return variable;
    }
}
