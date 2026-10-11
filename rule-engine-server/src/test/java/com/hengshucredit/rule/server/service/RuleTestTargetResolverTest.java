package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.compiler.ScriptPassthroughCompiler;
import com.hengshucredit.rule.model.dto.RuleTestExecutionSource;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.artifact.*;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.hengshucredit.rule.server.mapper.DecisionArtifactMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import static org.junit.Assert.*;

public class RuleTestTargetResolverTest {
    @Test public void latestAndBusinessVersionResolveIndependentPublishedLines() {
        Fixture fixture = new Fixture();
        RuleDefinitionVersion first = fixture.version(81L, 201L, 601L, 1);
        fixture.addFrozen(204L, 602L, "99");
        RuleDefinitionVersion second = fixture.version(82L, 204L, 602L, 2);
        RulePublished latest = new RulePublished(); latest.setDefinitionId(101L); latest.setVersion(2);
        ReflectionTestUtils.setField(fixture.resolver, "publishedMapper", Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{com.hengshucredit.rule.server.mapper.RulePublishedMapper.class}, (proxy, method, args) -> latest));
        ReflectionTestUtils.setField(fixture.resolver, "versionBindings", new RuleVersionBindingService() {
            @Override public RuleVersionBinding requireBusinessVersion(Long id, Integer number) {
                assertEquals(Integer.valueOf(2), number); var binding = new RuleVersionBinding(); binding.setId(8L); return binding;
            }
            @Override public RuleDefinitionVersion resolveSnapshot(Long id, Long binding) { return binding == 7L ? first : second; }
        });
        var source = new RuleTestExecutionSource(); source.setSourceType("LATEST");
        var current = fixture.resolver.resolve(101L, source, null, null, null);
        assertEquals("99", current.compiledScript()); assertEquals("LATEST", current.identity().getSourceType());
        source.setSourceType("BUSINESS_VERSION"); source.setSourceId("7");
        var fixed = fixture.resolver.resolve(101L, source, null, null, null);
        assertEquals("5", fixed.compiledScript()); assertEquals("7", fixed.identity().getSourceId());
        assertEquals(Integer.valueOf(1), fixed.identity().getVersionNo()); assertEquals(0, fixture.workspaceReads);
    }
    @Test public void draftLockClaimCannotSilentlySwitchToFrozenExecutionAfterPublication() {
        Fixture fixture = new Fixture();
        RuleGovernanceException conflict = assertThrows(RuleGovernanceException.class,
                () -> fixture.resolve("REVISION", 201L, 0));
        assertEquals(409, conflict.getHttpStatus());
        assertEquals(0, fixture.compilations);
    }

    @Test public void frozenRevisionAndTwoDraftsResolveTheirOwnModelAndFields() {
        Fixture fixture = new Fixture();
        fixture.addDraft(202L, 4, "6"); fixture.addDraft(203L, 5, "9");
        var frozen = fixture.resolve("REVISION", 201L, null);
        var first = fixture.resolve("REVISION", 202L, 4);
        var second = fixture.resolve("REVISION", 203L, 5);
        assertEquals("5", frozen.compiledScript());
        assertTrue(first.compiledScript().contains("6")); assertTrue(second.compiledScript().contains("9"));
        assertEquals("Source_Amount", frozen.fields().getInputFields().get(0).getScriptName());
        assertEquals("6", first.fields().getInputFields().get(0).getScriptName());
        assertEquals("9", second.fields().getInputFields().get(0).getScriptName());
        assertEquals("SCRIPT", frozen.modelType());
        assertEquals("201", frozen.identity().getRevisionId());
        assertEquals(Integer.valueOf(5), second.identity().getLockVersion());
        assertEquals(0, fixture.workspaceReads);
    }

    @Test public void staleDraftOrWrongDefinitionIsRejectedBeforeCompiling() {
        Fixture fixture = new Fixture(); fixture.addDraft(202L, 4, "6");
        RuleGovernanceException stale = assertThrows(RuleGovernanceException.class, () -> fixture.resolve("REVISION", 202L, 3));
        assertEquals(409, stale.getHttpStatus());
        assertThrows(RuleGovernanceException.class, () -> fixture.resolve("REVISION", 202L, null));
        fixture.revisions.get(202L).setDefinitionId(999L);
        assertThrows(IllegalArgumentException.class, () -> fixture.resolve("REVISION", 202L, 4));
        assertEquals(0, fixture.compilations); assertEquals(0, fixture.workspaceReads);
    }

    @Test public void versionUsesImmutableSnapshotIdEvenWhenTheBusinessVersionIsOverwritten() {
        Fixture fixture = new Fixture();
        RuleDefinitionVersion original = fixture.version(81L, 201L, 601L, 1);
        fixture.addFrozen(204L, 602L, "99");
        fixture.version(82L, 204L, 602L, 1);
        fixture.definition.setPublishedVersion(1);
        fixture.definition.setCurrentVersion(99);
        var old = fixture.resolve("VERSION", 81L, null);
        var latest = fixture.resolve("VERSION", 82L, null);
        assertEquals("5", old.compiledScript()); assertEquals("99", latest.compiledScript());
        assertEquals("81", old.identity().getSourceId());
        assertEquals(original.getArtifactDigest(), old.identity().getArtifactDigest());
        assertEquals(Integer.valueOf(1), old.published().getVersion());
        assertEquals(0, fixture.workspaceReads);
    }

    @Test public void missingCorruptOrForeignArtifactNeverFallsBackToCurrentContent() {
        Fixture missing = new Fixture(); missing.revisions.get(201L).setArtifactId(null);
        assertThrows(IllegalArgumentException.class, () -> missing.resolve("REVISION", 201L, null));
        Fixture corrupt = new Fixture(); corrupt.artifacts.get(601L).setPackageContent(new byte[]{1, 2, 3});
        assertThrows(IllegalArgumentException.class, () -> corrupt.resolve("REVISION", 201L, null));
        Fixture foreign = new Fixture(); foreign.artifacts.get(601L).setDefinitionId(999L);
        assertThrows(IllegalArgumentException.class, () -> foreign.resolve("REVISION", 201L, null));
        assertEquals(0, missing.workspaceReads + corrupt.workspaceReads + foreign.workspaceReads);
    }

    @Test public void importedTargetKeepsSourceIdsAndFrozenNamespaceDespiteLocalCollision() {
        Fixture fixture = new Fixture();
        RuleRevision source = fixture.revisions.remove(201L);
        fixture.definition.setId(901L); source.setId(902L); source.setDefinitionId(901L); source.setState("OFFLINE");
        DecisionArtifact artifact = fixture.artifacts.get(601L);
        artifact.setDefinitionId(null); artifact.setRevisionId(null); source.setContentDigest(artifact.getArtifactDigest());
        fixture.revisions.put(902L, source);
        fixture.deployment = new ArtifactDeployment(); fixture.deployment.setId(701L);
        fixture.deployment.setArtifactId(601L); fixture.deployment.setTargetDefinitionId(901L); fixture.deployment.setStatus("DEPLOYED");
        var target = fixture.resolve("REVISION", 902L, null);
        assertEquals("901", target.identity().getDefinitionId()); assertEquals("902", target.identity().getRevisionId());
        assertEquals(Long.valueOf(700L), target.fields().getInputFields().get(0).getVarId());
        assertEquals("Source_Amount", target.snapshot().getVariables().get(0).getScriptName());
        assertTrue(target.snapshot().isImported());
        assertEquals(0, fixture.workspaceReads);
    }

    @Test public void frozenSchemaNeverConsultsACollidingCurrentVariable() {
        Fixture fixture = new Fixture();
        var target = fixture.resolve("REVISION", 201L, null);
        FieldDependencyResolver dependencies = new FieldDependencyResolver();
        ReflectionTestUtils.setField(dependencies, "variableService", new RuleVariableService() {
            @Override public RuleVariable getById(Serializable id) { throw new AssertionError("冻结Schema不得读取当前同ID变量"); }
        });
        RuleTestSchemaService schemas = new RuleTestSchemaService();
        ReflectionTestUtils.setField(schemas, "fieldDependencyResolver", dependencies);
        var schema = schemas.build(target);
        assertEquals("Source_Amount", schema.getInputs().get(0).getScriptName());
        assertEquals("冻结输入", schema.getInputs().get(0).getLabel());
        assertEquals(target.identity(), schema.getExecutionIdentity());
        target.snapshot().getVariables().clear();
        assertEquals(1, schemas.build(target).getInputs().size());
    }

    private static RuleTestExecutionSource source(String type, Long id, Integer lock) {
        RuleTestExecutionSource source = new RuleTestExecutionSource();
        source.setSourceType(type); source.setSourceId(id == null ? null : String.valueOf(id)); source.setLockVersion(lock == null ? null : String.valueOf(lock)); return source;
    }

    private static final class Fixture {
        final RuleTestTargetResolver resolver = new RuleTestTargetResolver();
        final RuleDefinition definition = new RuleDefinition();
        final Map<Long, RuleRevision> revisions = new LinkedHashMap<>();
        final Map<Long, DecisionArtifact> artifacts = new LinkedHashMap<>();
        final Map<Long, RuleDefinitionVersion> versions = new LinkedHashMap<>();
        ArtifactDeployment deployment;
        int workspaceReads, compilations;
        Fixture() {
            definition.setId(101L); definition.setProjectId(7L); definition.setModelType("SCORE"); definition.setStatus(1);
            addFrozen(201L, 601L, "5");
            ReflectionTestUtils.setField(resolver, "namespaceGuard", new RuleReferenceNamespaceGuard() {
                @Override public void requireLocalNamespace(RuleDefinition definition, RuleRevision revision) { }
                @Override public void requireLocalNamespace(RuleDefinition definition, String type, Long id) { }
            });
            ReflectionTestUtils.setField(resolver, "definitionService", new RuleDefinitionService() {
                @Override public RuleDefinition getById(Serializable id) { return Objects.equals(definition.getId(), id) ? definition : null; }
                @Override public RuleDefinitionContent getContent(Long id) { workspaceReads++; throw new AssertionError("指定版本不能读取当前工作区"); }
                @Override public RuleDefinitionVersion getVersionById(Long id, Long snapshot) {
                    RuleDefinitionVersion version = versions.get(snapshot);
                    return version != null && Objects.equals(id, version.getDefinitionId()) ? version : null;
                }
            });
            ReflectionTestUtils.setField(resolver, "revisionMapper", mapper(RuleRevisionMapper.class, revisions::get));
            ReflectionTestUtils.setField(resolver, "artifactMapper", mapper(DecisionArtifactMapper.class, artifacts::get));
            ReflectionTestUtils.setField(resolver, "fieldSnapshotResolver", new PublishedRuleFieldSnapshotResolver() {
                @Override protected ArtifactDeployment loadDeployment(Long artifactId, Long definitionId) { return deployment; }
                @Override protected RuleRevision loadTargetRevision(Long id) { return revisions.get(id); }
            });
            ReflectionTestUtils.setField(resolver, "runtimeSnapshots", new ArtifactRuntimeSnapshotService() {
                @Override protected DecisionArtifact loadArtifact(Long id) { return artifacts.get(id); }
                @Override protected ArtifactDeployment loadDeployment(Long artifactId, Long definitionId) { return deployment; }
                @Override protected List<ArtifactResourceBinding> loadBindings(Long id) { return List.of(); }
            });
            ReflectionTestUtils.setField(resolver, "compileService", new RuleCompileService() {
                @Override public com.hengshucredit.rule.core.compiler.CompileResult compilePreview(Long id, String model, String type) {
                    compilations++; return new ScriptPassthroughCompiler().compile(model);
                }
            });
            ReflectionTestUtils.setField(resolver, "fieldAnalyzer", new RuleFieldAnalyzer() {
                @Override public ResolvedFields resolveFields(Long id, String model, String type, Long projectId) {
                    RuleDefinitionInputField input = new RuleDefinitionInputField();
                    input.setScriptName(String.valueOf(CanonicalJson.readMap(model).get("script")));
                    return new ResolvedFields(List.of(input), List.of());
                }
            });
        }
        RuleTestTargetResolver.Target resolve(String type, Long id, Integer lock) {
            return resolver.resolve(definition.getId(), source(type, id, lock), null, null, null);
        }
        void addDraft(long id, int lock, String script) {
            RuleRevision revision = new RuleRevision(); revision.setId(id); revision.setDefinitionId(definition.getId());
            revision.setState("DRAFT"); revision.setLockVersion(lock); revision.setModelJson(CanonicalJson.write(Map.of("script", script))); revisions.put(id, revision);
        }
        void addFrozen(long revisionId, long artifactId, String script) {
            DecisionArtifactPackage pack = new DecisionArtifactPackage();
            pack.putMetadata("definitionId", 101L); pack.putMetadata("revisionId", revisionId); pack.putMetadata("modelType", "SCRIPT"); pack.putMetadata("contentDigest", "content-" + revisionId);
            pack.addComponent("rule/model.json", "application/json", CanonicalJson.writeBytes(Map.of("script", script)));
            pack.addComponent("runtime/compiled.ql", "text/plain", script.getBytes(StandardCharsets.UTF_8));
            pack.addComponent("rule/input-fields.json", "application/json", CanonicalJson.writeBytes(List.of(Map.of("varId", 700, "refType", "VARIABLE", "scriptName", "Source_Amount", "fieldLabel", "冻结输入", "fieldType", "NUMBER"))));
            pack.addComponent("rule/output-fields.json", "application/json", "[]".getBytes(StandardCharsets.UTF_8));
            var variable = new RuleVariable(); variable.setId(700L); variable.setScriptName("Source_Amount"); variable.setVarCode("Source_Amount"); variable.setVarSource("INPUT"); variable.setVarType("NUMBER"); variable.setStatus(1);
            pack.addComponent("variables/700.json", "application/json", CanonicalJson.writeBytes(variable), Map.of("resourceType", "VARIABLE"));
            byte[] bytes = new DecisionArtifactPackageCodec().encode(pack);
            var decoded = new DecisionArtifactPackageCodec().decode(bytes);
            var artifact = new DecisionArtifact(); artifact.setId(artifactId); artifact.setDefinitionId(101L); artifact.setRevisionId(revisionId);
            artifact.setPackageContent(bytes); artifact.setArtifactDigest(decoded.getArtifactDigest()); artifact.setPackageDigest(decoded.getPackageDigest()); artifacts.put(artifactId, artifact);
            var revision = new RuleRevision(); revision.setId(revisionId); revision.setDefinitionId(101L); revision.setState("PUBLISHED"); revision.setArtifactId(artifactId); revision.setContentDigest("content-" + revisionId); revisions.put(revisionId, revision);
        }
        RuleDefinitionVersion version(long id, long revisionId, long artifactId, int businessVersion) {
            var version = new RuleDefinitionVersion(); version.setId(id); version.setDefinitionId(definition.getId()); version.setRevisionId(revisionId);
            version.setArtifactId(artifactId); version.setArtifactDigest(artifacts.get(artifactId).getArtifactDigest()); version.setBusinessVersion(businessVersion);
            versions.put(id, version); return version;
        }
    }

    @SuppressWarnings("unchecked") private static <T> T mapper(Class<T> type, Function<Long, Object> lookup) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return lookup.apply(Long.valueOf(args[0].toString()));
            if ("toString".equals(method.getName())) return type.getSimpleName();
            throw new AssertionError("测试期间禁止未声明的数据访问: " + method.getName());
        });
    }
}
