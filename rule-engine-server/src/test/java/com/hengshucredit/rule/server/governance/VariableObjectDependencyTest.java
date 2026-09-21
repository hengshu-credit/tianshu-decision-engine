package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class VariableObjectDependencyTest {
    @Test
    public void listQueryOperandDependsOnTheOwningObjectNotTheFieldId() {
        List<ResourceDependencyRef> dependencies = dependencies(Map.of(
                "sourceConfig", JSON.toJSONString(Map.of("queryOperands", List.of(
                        Map.of("kind", "PATH", "refType", "DATA_OBJECT", "refId", 240))))));
        assertEquals(1, dependencies.size());
        assertEquals("DATA_OBJECT", dependencies.get(0).targetResourceType());
        assertEquals(Long.valueOf(42), dependencies.get(0).targetResourceId());
        assertEquals("$.sourceConfig[json].queryOperands[0].refId", dependencies.get(0).referencePath());
    }

    @Test
    public void explicitObjectIdsAreNotReinterpretedAsFieldIds() {
        List<ResourceDependencyRef> dependencies = dependencies(Map.of("requestObjectId", 240));
        assertEquals(Long.valueOf(240), dependencies.get(0).targetResourceId());
    }

    @Test
    public void missingFieldRemainsAnUnresolvedRequiredDependency() {
        List<ResourceDependencyRef> dependencies = dependencies(Map.of(
                "refType", "DATA_OBJECT", "refId", 999));
        assertEquals(Long.valueOf(999), dependencies.get(0).targetResourceId());
        assertEquals(true, dependencies.get(0).required());
    }

    private List<ResourceDependencyRef> dependencies(Map<String, Object> snapshot) {
        RuleDataObjectField field = new RuleDataObjectField();
        field.setId(240L);
        field.setObjectId(42L);
        RuleDataObjectFieldMapper mapper = (RuleDataObjectFieldMapper) Proxy.newProxyInstance(
                RuleDataObjectFieldMapper.class.getClassLoader(), new Class<?>[]{RuleDataObjectFieldMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) return Long.valueOf(240).equals(args[0]) ? field : null;
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    if ("toString".equals(method.getName())) return "dataObjectFieldMapper";
                    return null;
                });
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("dataObjectFieldMapper", RuleDataObjectFieldMapper.class, () -> mapper);
            context.registerBean("adapter", VariableGovernedResourceAdapter.class,
                    () -> new VariableGovernedResourceAdapter(null, null, new GovernanceSecretCodec(null)));
            context.refresh();
            return context.getBean(VariableGovernedResourceAdapter.class).collectDependencies(
                    new ResourceSnapshot(JSON.toJSONString(snapshot), "ACTIVE", null, null));
        }
    }
}
