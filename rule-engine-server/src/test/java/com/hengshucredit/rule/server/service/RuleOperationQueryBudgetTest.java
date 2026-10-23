package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.*;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Query budgets use real service paths; mapper fixtures represent a growing visible catalog. */
public class RuleOperationQueryBudgetTest {
    @org.junit.Before public void initializeMapperMetadata() {
        for (Class<?> type : List.of(RuleVersionBinding.class, RuleDefinitionVersion.class, RuleExternalDatasource.class, RuleExternalApiConfig.class)) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                    new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "query-budget"), type);
        }
    }

    @Test public void compileMetadataUsesOneCatalogReadAndRetainsFunctionContracts() {
        AtomicInteger reads = new AtomicInteger();
        RuleFunctionService service = new RuleFunctionService() {
            @Override public List<RuleFunction> listByProject(Long projectId) {
                assertEquals(Long.valueOf(7), projectId); reads.incrementAndGet();
                RuleFunction function = new RuleFunction(); function.setId(91L); function.setFuncCode("Keep_Code");
                function.setParamsJson("[{\"name\":\"arg\",\"type\":\"NUMBER\"}]"); function.setImplType("JAVA");
                return List.of(function);
            }
        };
        var metadata = service.buildCompileMetadata(7L);
        assertEquals(Map.of(91L, "Keep_Code"), metadata.codes());
        assertEquals(Map.of(91L, 1), metadata.arities());
        assertEquals(Set.of(91L), metadata.nativeIds());
        assertEquals(1, reads.get());
    }

    @Test public void publicationSnapshotsAreLoadedInBoundedBatches() {
        List<RuleVersionBinding> bindings = new ArrayList<>();
        List<RuleDefinitionVersion> history = new ArrayList<>();
        for (long id = 1; id <= 1200; id++) {
            RuleVersionBinding binding = new RuleVersionBinding();
            binding.setId(id); binding.setDefinitionId(id); binding.setSnapshotId(id); binding.setVersionNo(1); binding.setStatus(1);
            bindings.add(binding);
            RuleDefinitionVersion snapshot = new RuleDefinitionVersion();
            snapshot.setId(id); snapshot.setDefinitionId(id); snapshot.setModelJson("{}"); history.add(snapshot);
        }
        AtomicInteger reads = new AtomicInteger();
        Set<Long> selected = new HashSet<>();
        RulePublicationValidator validator = new RulePublicationValidator();
        ReflectionTestUtils.setField(validator, "bindings", mapper(RuleVersionBindingMapper.class, (p, m, a) -> bindings));
        ReflectionTestUtils.setField(validator, "snapshots", mapper(RuleDefinitionVersionMapper.class, (p, m, a) -> {
            reads.incrementAndGet();
            if (m.getName().equals("selectById")) return history.get(((Number) a[0]).intValue() - 1);
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) a[0];
            query.getSqlSegment();
            Set<Object> ids = new HashSet<>(query.getParamNameValuePairs().values());
            assertTrue(ids.size() <= 500);
            var batch = history.stream().filter(item -> ids.contains(item.getId())).toList();
            batch.forEach(item -> selected.add(item.getId()));
            return batch;
        }));
        assertNull(validator.previewCycle(2000L, "{\"nodes\":[{\"type\":\"rule-call\",\"ruleId\":1},{\"type\":\"rule-call\",\"ruleId\":1200}]}"));
        System.out.println("QUERY_BUDGET publication snapshots=" + reads.get() + " bindings=" + bindings.size());
        assertTrue("1200 versions must not cause 1200 round trips", reads.get() <= 3);
        assertEquals(1200, selected.size());
    }

    @Test public void externalCatalogLoadsSharedParentsInBatchAndPreservesScope() {
        RuleExternalDatasource own = datasource(10L, "PROJECT", 1L);
        RuleExternalDatasource global = datasource(20L, "GLOBAL", null);
        RuleExternalDatasource foreign = datasource(30L, "PROJECT", 2L);
        RuleExternalDatasource disabled = datasource(40L, "GLOBAL", null); disabled.setStatus(0);
        List<RuleExternalApiConfig> apis = new ArrayList<>();
        for (long id = 1; id <= 1000; id++) {
            RuleExternalApiConfig api = new RuleExternalApiConfig();
            api.setId(id); api.setDatasourceId(id <= 997 ? 10L : (id - 996) * 10); api.setStatus(1); apis.add(api);
        }
        AtomicInteger reads = new AtomicInteger();
        RuleReferenceIntegrityService service = new RuleReferenceIntegrityService();
        ReflectionTestUtils.setField(service, "variableService", new RuleVariableService() {
            @Override public Map<String, String> buildRefScriptNameMap(Long projectId) { return Map.of(); }
        });
        ReflectionTestUtils.setField(service, "externalApiMapper", mapper(RuleExternalApiConfigMapper.class, (p, m, a) -> apis));
        ReflectionTestUtils.setField(service, "externalDatasourceMapper", mapper(RuleExternalDatasourceMapper.class, (p, m, a) -> {
            reads.incrementAndGet();
            if (m.getName().equals("selectById")) return Map.of(10L, own, 20L, global, 30L, foreign, 40L, disabled).get(a[0]);
            return List.of(own, global, foreign, disabled);
        }));
        var report = service.audit(7L, 1L, "{\"refCode\":\"api\",\"refType\":\"EXTERNAL_API\",\"varId\":1}");
        assertTrue(report.getIssues().toString(), report.isValid());
        System.out.println("QUERY_BUDGET external parents=" + reads.get() + " apis=" + apis.size());
        assertEquals("Shared parents must be read once", 1, reads.get());
        assertTrue(service.audit(7L, 1L, "{\"refCode\":\"api\",\"refType\":\"EXTERNAL_API\",\"varId\":998}").isValid());
        assertFalse(service.audit(7L, 1L, "{\"refCode\":\"api\",\"refType\":\"EXTERNAL_API\",\"varId\":999}").isValid());
        assertFalse(service.audit(7L, 1L, "{\"refCode\":\"api\",\"refType\":\"EXTERNAL_API\",\"varId\":1000}").isValid());
    }

    private static RuleExternalDatasource datasource(Long id, String scope, Long projectId) {
        RuleExternalDatasource source = new RuleExternalDatasource(); source.setId(id); source.setScope(scope);
        source.setProjectId(projectId); source.setStatus(1); return source;
    }

    private static <T> T mapper(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
