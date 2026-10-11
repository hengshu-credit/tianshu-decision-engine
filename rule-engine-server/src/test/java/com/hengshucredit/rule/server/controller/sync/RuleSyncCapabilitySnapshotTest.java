package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleVariableService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RuleSyncCapabilitySnapshotTest {
    @Test public void frozenApiSourceRemainsServerRequiredAfterCurrentVariableBecomesInput() {
        var snapshot = snapshot("API");
        Fixture fixture = new Fixture(snapshot, List.of(variable("INPUT")), true);
        RulePublished rule = fixture.mark();
        assertTrue("旧制品API来源不能因当前变量改为INPUT而放行本地", rule.isRequiresServerExecution());
        assertTrue(rule.getServerExecutionReason().contains("API"));
        fixture.assertSingleSnapshotOnly();
    }

    @Test public void unrelatedLiveApiCannotMakePureFrozenArtifactServerRequired() {
        for (String source : new String[]{null, "INPUT"}) {
            Fixture fixture = new Fixture(snapshot(source), List.of(variable("API")), true);
            RulePublished rule = fixture.mark();
            assertFalse("能力只能来自所选制品，不能扫描整个项目的当前来源", rule.isRequiresServerExecution());
            assertNull(rule.getServerExecutionReason());
            fixture.assertSingleSnapshotOnly();
        }
    }

    @Test public void sourceObjectsNeedServerEvenWithoutStandaloneSourceVariables() {
        for (String source : List.of("API", "DB", "DATABASE", "INPUT")) {
            var snapshot = snapshot(null);
            var field = new RuleDataObjectField(); field.setId(81L); field.setObjectId(80L); field.setObjectSourceType(source);
            snapshot.getDataObjectFields().add(field);
            Fixture fixture = new Fixture(snapshot, List.of(), true);
            RulePublished rule = fixture.mark();
            assertEquals(source, !"INPUT".equals(source), rule.isRequiresServerExecution());
            fixture.assertSingleSnapshotOnly();
        }
    }

    @Test public void idempotencyCapabilityComesFromSameFrozenContractIncludingAbsentComponent() {
        for (String frozen : new String[]{"{\"idempotency\":{\"enabled\":true}}", "{\"idempotency\":{\"enabled\":false}}", null}) {
            var snapshot = snapshot(null);
            ReflectionTestUtils.setField(snapshot, "openApiConfigJson", frozen);
            Fixture fixture = new Fixture(snapshot, List.of(), true);
            boolean enabled = frozen != null && frozen.contains("true");
            fixture.published.setOpenApiConfigJson("{\"idempotency\":{\"enabled\":" + !enabled + "}}");
            RulePublished rule = fixture.mark();
            assertEquals("不能借用其它发布行/版本的幂等配置", enabled, rule.isRequiresServerExecution());
            assertEquals(frozen, rule.getOpenApiConfigJson());
            fixture.assertSingleSnapshotOnly();
        }
    }

    @Test public void modelCapabilityReusesTheAlreadyVerifiedSnapshot() {
        var snapshot = snapshot(null); snapshot.getModels().add(new RuleModel());
        Fixture fixture = new Fixture(snapshot, List.of(), true);
        assertTrue(fixture.mark().isRequiresServerExecution());
        fixture.assertSingleSnapshotOnly();
    }

    @Test public void legacyRuleWithoutArtifactKeepsExplicitCurrentProjectCompatibility() {
        Fixture fixture = new Fixture(snapshot(null), List.of(variable("API")), false);
        assertTrue(fixture.mark().isRequiresServerExecution());
        assertEquals(1, fixture.liveReads.get()); assertEquals(0, fixture.snapshotLoads.get());
    }

    private static ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot(String source) {
        var snapshot = new ArtifactRuntimeSnapshotService.RuntimeSnapshot();
        if (source != null) snapshot.getVariables().add(variable(source));
        return snapshot;
    }

    private static RuleVariable variable(String source) {
        RuleVariable variable = new RuleVariable(); variable.setId(31L); variable.setVarSource(source); variable.setStatus(1);
        return variable;
    }

    private static final class Fixture {
        final RuleSyncController controller = new RuleSyncController();
        final RulePublished published = new RulePublished();
        final AtomicInteger liveReads = new AtomicInteger(), snapshotLoads = new AtomicInteger();
        Fixture(ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot, List<RuleVariable> live, boolean artifact) {
            var definition = new RuleDefinition(); definition.setId(10L); definition.setProjectId(1L);
            ReflectionTestUtils.setField(controller, "definitionService", new RuleDefinitionService() {
                @Override public RuleDefinition getById(java.io.Serializable id) { return definition; }
                @Override public List<RuleDefinitionOutputField> listOutputFields(Long id) { return List.of(); }
            });
            ReflectionTestUtils.setField(controller, "variableService", new RuleVariableService() {
                @Override public List<RuleVariable> listByProject(Long id, String source) { liveReads.incrementAndGet(); return live; }
            });
            ReflectionTestUtils.setField(controller, "artifactSnapshotService", new ArtifactRuntimeSnapshotService() {
                @Override public RuntimeSnapshot load(Long artifactId, Long definitionId, Long projectId) {
                    snapshotLoads.incrementAndGet(); return snapshot;
                }
            });
            published.setDefinitionId(10L); published.setRevisionId(20L);
            if (artifact) published.setArtifactId(30L);
        }
        RulePublished mark() { return ReflectionTestUtils.invokeMethod(controller, "withOutputScriptNames", published); }
        void assertSingleSnapshotOnly() { assertEquals(1, snapshotLoads.get()); assertEquals(0, liveReads.get()); }
    }
}
