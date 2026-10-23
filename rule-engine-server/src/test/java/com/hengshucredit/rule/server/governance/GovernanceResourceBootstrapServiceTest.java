package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import com.hengshucredit.rule.model.entity.RuleDefinitionRef;
import com.hengshucredit.rule.server.mapper.GovernedResourceMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionRefMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.List;

public class GovernanceResourceBootstrapServiceTest {

    @Test
    public void existingProjectRuleBindingBootstrapsByBindingIdAndProjectId() {
        RuleDefinitionRef binding = new RuleDefinitionRef();
        binding.setId(15L);
        binding.setDefinitionId(30L);
        binding.setProjectId(9L);
        RuleDefinitionRefMapper refMapper = proxy(
                RuleDefinitionRefMapper.class,
                (method, args) -> "selectById".equals(method)
                        ? binding : null);
        RuleProjectBindingGovernedResourceAdapter adapter =
                new RuleProjectBindingGovernedResourceAdapter(
                        refMapper,
                        proxy(RuleDefinitionMapper.class,
                                (method, args) -> null),
                        proxy(RuleProjectMapper.class,
                                (method, args) -> null));
        final GovernedResource[] storedResource = {null};
        final GovernedResourceVersion[] storedVersion = {null};
        GovernedResourceMapper resourceMapper = proxy(
                GovernedResourceMapper.class, (method, args) -> {
                    if ("selectOne".equals(method)) return null;
                    if ("insert".equals(method)) {
                        storedResource[0] = (GovernedResource) args[0];
                        storedResource[0].setId(71L);
                        return 1;
                    }
                    if ("linkBaselineVersion".equals(method)) {
                        Assert.assertEquals(Long.valueOf(71L), args[0]);
                        Assert.assertEquals(Long.valueOf(72L), args[1]);
                        Assert.assertEquals(Integer.valueOf(1), args[2]);
                        return 1;
                    }
                    return null;
                });
        GovernedResourceVersionMapper versionMapper = proxy(
                GovernedResourceVersionMapper.class, (method, args) -> {
                    if ("insert".equals(method)) {
                        storedVersion[0] =
                                (GovernedResourceVersion) args[0];
                        storedVersion[0].setId(72L);
                        return 1;
                    }
                    return null;
                });
        GovernanceResourceBootstrapService service =
                new GovernanceResourceBootstrapService();
        ReflectionTestUtils.setField(service, "resourceMapper",
                resourceMapper);
        ReflectionTestUtils.setField(service, "versionMapper",
                versionMapper);
        ReflectionTestUtils.setField(service, "adapterRegistry",
                new GovernedResourceAdapterRegistry(List.of(adapter)));

        GovernedResource resource = service.ensure(
                GovernanceResourceTypes.RULE_PROJECT_BINDING, 15L);

        Assert.assertSame(storedResource[0], resource);
        Assert.assertEquals(Long.valueOf(15L), resource.getResourceId());
        Assert.assertEquals(Long.valueOf(9L), resource.getProjectId());
        Assert.assertEquals(Integer.valueOf(1),
                resource.getEffectiveVersionNo());
        Assert.assertEquals(Long.valueOf(72L),
                resource.getEffectiveVersionId());
        Assert.assertEquals(
                GovernanceResourceTypes.RULE_PROJECT_BINDING,
                storedVersion[0].getResourceType());
        Assert.assertEquals(Long.valueOf(15L),
                storedVersion[0].getResourceId());
        Assert.assertTrue(storedVersion[0].getSnapshotJson()
                .contains("\"definitionId\":30"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, MapperCall call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(),
                new Class<?>[]{type}, (proxy, method, args) -> {
                    Object value = call.invoke(method.getName(), args);
                    if (value != null || !method.getReturnType().isPrimitive()) {
                        return value;
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == long.class) return 0L;
                    return 0;
                });
    }

    @FunctionalInterface
    private interface MapperCall {
        Object invoke(String method, Object[] args);
    }
}
