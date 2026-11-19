package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class FrozenExternalApiScopeTest {
    @Test public void resolverOpensOnlyBindingsOwnedByModelsRequiredInThisPlan() {
        var resolver = new VariableSourceResolver();
        var selected = new java.util.ArrayList<Set<Long>>();
        var bindings = new ModelExternalBindings(null,null,null,null,null,null,null,null,null) {
            @Override public FrozenExternalApiScope open(Map<Long, com.alibaba.fastjson.JSONObject> descriptors, Long project) {
                selected.add(Set.copyOf(descriptors.keySet()));
                return FrozenExternalApiScope.open(Map.of(), Map.of(), Map.of(), "test");
            }
        };
        org.springframework.test.util.ReflectionTestUtils.setField(resolver,"modelExternalBindings",bindings);
        var runtime = new com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService.RuntimeSnapshot();
        org.springframework.test.util.ReflectionTestUtils.setField(runtime,"frozenModelApis",Map.of(
                61L,new com.alibaba.fastjson.JSONObject(), 62L,new com.alibaba.fastjson.JSONObject()));
        org.springframework.test.util.ReflectionTestUtils.setField(runtime,"modelApiIds",Map.of(1L,List.of(61L),2L,List.of(62L)));
        var a = new com.hengshucredit.rule.model.entity.RuleModel(); a.setId(1L); a.setModelCode("A");
        var b = new com.hengshucredit.rule.model.entity.RuleModel(); b.setId(2L); b.setModelCode("B");
        var options = VariableResolveOptions.defaults();
        options.setRuntimeSnapshot(runtime); options.setRequiredNamesUpstreamOnly(true);
        options.setRequiredScriptNames(Set.of("unrelated"));
        resolver.resolveIntoSnapshot(List.of(),List.of(a,b),List.of(),new java.util.HashMap<>(),options);
        assertTrue(selected.isEmpty());
        options.setRequiredScriptNames(Set.of("A.score"));
        resolver.resolveIntoSnapshot(List.of(),List.of(a,b),List.of(),new java.util.HashMap<>(),options);
        assertEquals(List.of(Set.of(61L)),selected);
        options.setRequiredScriptNames(null);
        resolver.resolveIntoSnapshot(List.of(),List.of(a,b),List.of(),new java.util.HashMap<>(),options);
        assertEquals(List.of(Set.of(61L),Set.of(61L,62L)),selected);
        assertFalse(FrozenExternalApiScope.active());
    }

    @Test public void sourceWorkersUseSelectedApiAndRestoreParentWhenAnotherApiSharesItsDatasource() throws Exception {
        var api = new RuleExternalApiConfig();
        api.setId(9L); api.setDatasourceId(4L);
        var source = new RuleExternalDatasource();
        source.setId(4L); source.setBaseUrl("http://original.invalid");
        Map<String,String> paths = Map.of("VARIABLE:2", "oldName");
        try (var workers = new SourceResolutionExecutor(2)) {
            try (var scope = FrozenExternalApiScope.open(Map.of(9L,api), Map.of(4L,source), paths, "first")) {
                source.setBaseUrl("http://later.invalid");
                assertEquals("http://original.invalid", workers.submit(() -> {
                    try (var context = FrozenExternalApiScope.bindApi(9L)) {
                        assertEquals(paths, FrozenExternalApiScope.invocation().paths());
                        try (var unrelated = FrozenExternalApiScope.bindApi(11L)) {
                            assertNull(FrozenExternalApiScope.invocation());
                        }
                        return FrozenExternalApiScope.invocation().datasource(4L).getBaseUrl();
                    }
                }).get(5, TimeUnit.SECONDS));
                try (var changed = FrozenExternalApiScope.open(Map.of(9L,api), Map.of(4L,source), Map.of(), "second")) {
                    assertSame(changed,FrozenExternalApiScope.forApi(9L));
                }
                assertSame(scope,FrozenExternalApiScope.forApi(9L));
                assertNull(FrozenExternalApiScope.forApi(11L));
            }
            assertNull(workers.submit(() -> FrozenExternalApiScope.forApi(9L)).get(5, TimeUnit.SECONDS));
            assertNull(FrozenExternalApiScope.forApi(9L));
        }
    }
}
