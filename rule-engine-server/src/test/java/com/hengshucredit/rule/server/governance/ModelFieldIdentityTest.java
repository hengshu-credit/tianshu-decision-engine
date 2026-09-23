package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ModelFieldIdentityTest {
    @Test
    public void enablingModelCreatesExecutableVersionSnapshot() {
        var adapter = new ModelGovernedResourceAdapter(null, null, null, null);
        AtomicInteger publications = new AtomicInteger();
        ReflectionTestUtils.setField(adapter, "modelService", new com.hengshucredit.rule.server.service.RuleModelService() {
            @Override public void publish(Long id, String message, String actor) {
                assertEquals(Long.valueOf(2), id); assertEquals("tester", actor); publications.incrementAndGet();
            }
        });
        var context = new ApprovalApplyContext(1L, 2L, 10, "ENABLE", ResourceSnapshot.ofJson("{}"), "tester", null);
        adapter.afterAggregateApplied(context, new AppliedResource(2L, 10, "ACTIVE", null));
        assertEquals(1, publications.get());
    }
    @Test
    public void updatingBindingOrRecordingDoesNotReplaceFieldIdentity() {
        var input = new RuleModelInputField(); input.setId(41L); input.setModelId(2L);
        var output = new RuleModelOutputField(); output.setId(7L); output.setModelId(2L);
        AtomicInteger updates = new AtomicInteger();
        var inputs = mapper(RuleModelInputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return List.of(input);
            if ("updateById".equals(method.getName())) {
                assertEquals(41L, ((RuleModelInputField) args[0]).getId().longValue()); updates.incrementAndGet(); return 1;
            }
            if ("delete".equals(method.getName()) || "insert".equals(method.getName())) throw new AssertionError("字段配置修改不能删除重建");
            return null;
        });
        var outputs = mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return List.of(output);
            if ("updateById".equals(method.getName())) {
                var value = (RuleModelOutputField) args[0];
                assertEquals(7L, value.getId().longValue()); assertEquals(Boolean.TRUE, value.getRecordResult()); updates.incrementAndGet(); return 1;
            }
            if ("delete".equals(method.getName()) || "insert".equals(method.getName())) throw new AssertionError("字段配置修改不能删除重建");
            return null;
        });
        var adapter = new ModelGovernedResourceAdapter(null, inputs, outputs, null);
        adapter.applyAggregate(2L, Map.of("inputFields", List.of(Map.of("id", 41L, "fieldName", "x", "sourceOperand", "{}")),
                "outputFields", List.of(Map.of("id", 7L, "fieldName", "score", "recordResult", true))));
        assertEquals(2, updates.get());
    }

    @Test
    public void dataObjectFieldDependencyUsesOwningGovernanceResourceId() {
        var adapter = new ModelGovernedResourceAdapter(null, null, null, null);
        var field = new RuleDataObjectField(); field.setId(258L); field.setObjectId(10L);
        ReflectionTestUtils.setField(adapter, "dataObjectFieldMapper", mapper(RuleDataObjectFieldMapper.class,
                (proxy, method, args) -> "selectById".equals(method.getName()) ? field : null));
        var refs = adapter.collectDependencies(ResourceSnapshot.ofJson("{\"inputFields\":[{\"varId\":258,\"refType\":\"DATA_OBJECT\"}]}"));
        assertTrue(refs.stream().anyMatch(ref -> "DATA_OBJECT".equals(ref.targetResourceType()) && Long.valueOf(10).equals(ref.targetResourceId())));
        assertFalse(refs.stream().anyMatch(ref -> Long.valueOf(258).equals(ref.targetResourceId())));
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
