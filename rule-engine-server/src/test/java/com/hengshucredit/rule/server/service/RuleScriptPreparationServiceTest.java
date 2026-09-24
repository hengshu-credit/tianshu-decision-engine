package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService;
import com.hengshucredit.rule.server.health.RuleWarmupState;
import com.hengshucredit.rule.server.health.RuleWarmupStatus;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionVersionMapper;
import com.hengshucredit.rule.server.mapper.RuleVersionBindingMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Map;
import static org.junit.Assert.*;

public class RuleScriptPreparationServiceTest {
    @Test
    public void publicationRejectsFunctionMissingFromFrozenDependenciesEvenIfAnotherRuleRegisteredIt() {
        QLExpressEngine engine = new QLExpressEngine();
        FunctionRegistrar registrar = new FunctionRegistrar();
        RuleFunction function = new RuleFunction();
        function.setId(77L); function.setFuncCode("versioned"); function.setImplType("JAVA");
        function.setImplClass(FunctionRegistrarTest.VersionOne.class.getName()); function.setImplMethod("value");
        registrar.prepareFunctions(List.of(function), engine.getRunner());
        RuleScriptPreparationService service = service(engine);
        ReflectionTestUtils.setField(service, "functionRegistrar", registrar);
        var snapshot = new ArtifactRuntimeSnapshotService.RuntimeSnapshot();
        ReflectionTestUtils.setField(snapshot, "compiledScript", "return versioned();");
        ReflectionTestUtils.setField(service, "snapshots", new ArtifactRuntimeSnapshotService() {
            @Override public RuntimeSnapshot load(Long artifact, Long definition, Long project) { return snapshot; }
        });
        assertThrows(IllegalStateException.class, () -> service.prepareArtifact(1L, 2L, 3L));
        snapshot.getFunctions().add(function);
        assertNotNull(service.prepareArtifact(1L, 2L, 3L));
    }
    @Test
    public void validatesFrozenConstantsBeforeActivationWithoutExecutingScript() {
        QLExpressEngine engine = new QLExpressEngine();
        RuleScriptPreparationService service = service(engine);
        ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot = new ArtifactRuntimeSnapshotService.RuntimeSnapshot();
        RuleVariable constant = new RuleVariable();
        constant.setId(9L); constant.setVarCode("LIMIT"); constant.setVarSource("CONSTANT");
        snapshot.getVariables().add(constant);
        ReflectionTestUtils.setField(snapshot, "compiledScript", "LIMIT = 1;");
        ReflectionTestUtils.setField(service, "snapshots", new ArtifactRuntimeSnapshotService() {
            @Override public RuntimeSnapshot load(Long artifact, Long definition, Long project) { return snapshot; }
        });
        assertThrows(IllegalStateException.class, () -> service.prepareArtifact(1L, 2L, 3L));
        int[] calls = {0};
        engine.getRunner().addFunction("sideEffect", (Runnable) () -> calls[0]++);
        ReflectionTestUtils.setField(snapshot, "compiledScript", "sideEffect(); return LIMIT;");
        QLExpressEngine.PreparedScript prepared = service.prepareArtifact(1L, 2L, 3L);
        assertEquals(0, calls[0]);
        assertEquals(7, engine.execute(prepared, Map.of("LIMIT", 7), false).getResult());
        assertEquals(1, calls[0]);
    }

    @Test
    public void startupWarmsLatestLegacyAndFixedArtifactVersions() {
        List<String> warmed = new ArrayList<>();
        RuleScriptPreparationService service = new RuleScriptPreparationService() {
            @Override public QLExpressEngine.PreparedScript prepareProject(String script, Long projectId) {
                warmed.add("legacy:" + script); return new QLExpressEngine().prepare(script);
            }
            @Override public QLExpressEngine.PreparedScript prepareArtifact(Long artifact, Long definition, Long project) {
                warmed.add("artifact:" + artifact); return new QLExpressEngine().prepare("return 1;");
            }
        };
        RulePublished latest = new RulePublished();
        latest.setDefinitionId(2L); latest.setVersion(2); latest.setCompiledScript("return 2;");
        RuleVersionBinding fixed = new RuleVersionBinding();
        fixed.setId(10L); fixed.setVersionNo(1);
        fixed.setSnapshotId(20L);
        RuleVersionBinding unavailable = new RuleVersionBinding();
        unavailable.setId(11L); unavailable.setVersionNo(0); unavailable.setSnapshotId(21L);
        ReflectionTestUtils.setField(service, "publishedMapper", mapper(RulePublishedMapper.class, List.of(latest)));
        ReflectionTestUtils.setField(service, "bindingMapper", mapper(RuleVersionBindingMapper.class, List.of(fixed, unavailable)));
        ReflectionTestUtils.setField(service, "versionMapper", Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {RuleDefinitionVersionMapper.class}, (proxy, method, args) -> {
                    RuleDefinitionVersion version = new RuleDefinitionVersion();
                    if (Long.valueOf(20).equals(args[0])) { version.setArtifactId(99L); version.setRevisionId(7L); }
                    return version;
                }));
        ReflectionTestUtils.setField(service, "definitionService", new RuleDefinitionService() {
            @Override public RuleDefinition getById(java.io.Serializable id) {
                RuleDefinition definition = new RuleDefinition(); definition.setId(2L); definition.setProjectId(3L); return definition;
            }
        });
        ReflectionTestUtils.setField(service, "versionService", new RuleVersionBindingService() {
            @Override public RulePublished resolvePublished(RulePublished published, Long bindingId) {
                assertEquals(Long.valueOf(10), bindingId);
                RulePublished historical = new RulePublished();
                historical.setDefinitionId(2L); historical.setArtifactId(99L); return historical;
            }
        });
        service.run(null);
        assertEquals(List.of("legacy:return 2;", "artifact:99"), warmed);
        assertEquals(RuleWarmupState.READY,
                ((RuleWarmupStatus) ReflectionTestUtils.getField(service, "warmupStatus")).getState());
    }

    @Test
    public void invalidFixedVersionDoesNotAbortOtherVersionsOrFollowingRules() {
        List<String> warmed = new ArrayList<>();
        RuleScriptPreparationService service = new RuleScriptPreparationService() {
            @Override public QLExpressEngine.PreparedScript prepareProject(String script, Long projectId) {
                warmed.add(script);
                return new QLExpressEngine().prepare(script);
            }
            @Override public QLExpressEngine.PreparedScript prepareArtifact(Long artifact, Long definition, Long project) {
                warmed.add("artifact:" + artifact);
                return new QLExpressEngine().prepare("return 2;");
            }
        };
        RulePublished first = new RulePublished();
        first.setDefinitionId(2L); first.setVersion(3); first.setCompiledScript("return 3;");
        RulePublished next = new RulePublished();
        next.setDefinitionId(4L); next.setVersion(3); next.setCompiledScript("return 4;");
        RuleVersionBinding broken = new RuleVersionBinding();
        broken.setId(10L); broken.setVersionNo(1); broken.setSnapshotId(20L);
        RuleVersionBinding valid = new RuleVersionBinding();
        valid.setId(11L); valid.setVersionNo(2); valid.setSnapshotId(21L);
        ReflectionTestUtils.setField(service, "publishedMapper", mapper(RulePublishedMapper.class, List.of(first, next)));
        java.util.concurrent.atomic.AtomicInteger bindingQueries = new java.util.concurrent.atomic.AtomicInteger();
        ReflectionTestUtils.setField(service, "bindingMapper", Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {RuleVersionBindingMapper.class}, (proxy, method, args) ->
                        "selectList".equals(method.getName())
                                ? (bindingQueries.getAndIncrement() == 0 ? List.of(broken, valid) : List.of()) : null));
        ReflectionTestUtils.setField(service, "versionMapper", Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {RuleDefinitionVersionMapper.class}, (proxy, method, args) -> {
                    RuleDefinitionVersion version = new RuleDefinitionVersion();
                    version.setArtifactId(99L); version.setRevisionId(7L);
                    return version;
                }));
        ReflectionTestUtils.setField(service, "definitionService", new RuleDefinitionService() {
            @Override public RuleDefinition getById(java.io.Serializable id) {
                RuleDefinition definition = new RuleDefinition();
                definition.setId((Long) id); definition.setProjectId(3L);
                return definition;
            }
        });
        ReflectionTestUtils.setField(service, "versionService", new RuleVersionBindingService() {
            @Override public RulePublished resolvePublished(RulePublished published, Long bindingId) {
                if (Long.valueOf(10).equals(bindingId)) {
                    throw new IllegalStateException("指定版本没有可执行脚本");
                }
                assertEquals(Long.valueOf(11), bindingId);
                RulePublished historical = new RulePublished();
                historical.setDefinitionId(published.getDefinitionId());
                historical.setArtifactId(99L);
                historical.setVersion(2); historical.setCompiledScript("return 2;");
                return historical;
            }
        });

        service.run(null);

        assertEquals(List.of("return 3;", "artifact:99", "return 4;"), warmed);
        assertEquals(2, bindingQueries.get());
        assertEquals(RuleWarmupState.FAILED,
                ((RuleWarmupStatus) ReflectionTestUtils.getField(service, "warmupStatus")).getState());
    }

    private RuleScriptPreparationService service(QLExpressEngine engine) {
        RuleScriptPreparationService service = new RuleScriptPreparationService();
        ReflectionTestUtils.setField(service, "engine", engine);
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(service, "runtimeInvoker", new RuleRuntimeInvoker());
        return service;
    }

    private <T> T mapper(Class<T> type, Object result) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> "selectList".equals(method.getName()) ? result : null));
    }
}
