package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.service.RuleBillingService;
import com.hengshucredit.rule.server.service.RuleVariableService;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ExternalApiObjectDependencyTest {
    @Test
    public void executionRequestOperandDependsOnTheOwningObjectNotTheFieldId() {
        List<ResourceDependencyRef> dependencies = dependencies(Map.of("executionConfig", Map.of(
                "version", 2, "requestFields", List.of(Map.of("id", "requestNo", "value",
                        Map.of("kind", "PATH", "refType", "DATA_OBJECT", "refId", 199))))));

        assertEquals(1, dependencies.size());
        assertEquals("DATA_OBJECT", dependencies.get(0).targetResourceType());
        assertEquals(Long.valueOf(42), dependencies.get(0).targetResourceId());
        assertEquals("$.executionConfig.requestFields[0].value.refId", dependencies.get(0).referencePath());
    }

    @Test
    public void serializedStepOperandAlsoDependsOnTheOwningObject() {
        List<ResourceDependencyRef> dependencies = dependencies(Map.of("executionConfig", JSON.toJSONString(Map.of(
                "steps", List.of(Map.of("requestFields", List.of(Map.of("value",
                        Map.of("kind", "REFERENCE", "refType", "DATA_OBJECT", "refId", 199)))))))));

        assertEquals(1, dependencies.size());
        assertEquals(Long.valueOf(42), dependencies.get(0).targetResourceId());
        assertEquals("$.executionConfig[json].steps[0].requestFields[0].value.refId", dependencies.get(0).referencePath());
    }

    @Test
    public void explicitObjectAndVariableIdsKeepTheirOwnIdentities() {
        List<ResourceDependencyRef> dependencies = dependencies(Map.of("requestObjectId", 199,
                "executionConfig", Map.of("requestFields", List.of(Map.of("value",
                        Map.of("kind", "PATH", "refType", "VARIABLE", "refId", 199))))));

        assertEquals(2, dependencies.size());
        assertEquals(1, dependencies.stream().filter(ref -> "DATA_OBJECT".equals(ref.targetResourceType())
                && Long.valueOf(199).equals(ref.targetResourceId())).count());
        assertEquals(1, dependencies.stream().filter(ref -> "VARIABLE".equals(ref.targetResourceType())
                && Long.valueOf(199).equals(ref.targetResourceId())).count());
    }

    private List<ResourceDependencyRef> dependencies(Map<String, Object> snapshot) {
        RuleDataObjectField field = new RuleDataObjectField();
        field.setId(199L);
        field.setObjectId(42L);
        RuleDataObjectFieldMapper mapper = (RuleDataObjectFieldMapper) Proxy.newProxyInstance(
                RuleDataObjectFieldMapper.class.getClassLoader(), new Class<?>[]{RuleDataObjectFieldMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) return Long.valueOf(199).equals(args[0]) ? field : null;
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    if ("toString".equals(method.getName())) return "dataObjectFieldMapper";
                    return null;
                });
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("billingService", new RuleBillingService());
            context.getBeanFactory().registerSingleton("variableService", new RuleVariableService());
            context.getBeanFactory().registerSingleton("dataObjectFieldMapper", mapper);
            context.registerBean("adapter", ExternalApiGovernedResourceAdapter.class,
                    () -> new ExternalApiGovernedResourceAdapter(null, new GovernanceSecretCodec(null), null, null));
            context.refresh();
            return context.getBean(ExternalApiGovernedResourceAdapter.class).collectDependencies(
                    new ResourceSnapshot(JSON.toJSONString(snapshot), "ACTIVE", null, null));
        }
    }
}
