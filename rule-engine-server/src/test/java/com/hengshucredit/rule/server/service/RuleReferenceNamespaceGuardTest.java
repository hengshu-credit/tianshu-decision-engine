package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.artifact.*;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

public class RuleReferenceNamespaceGuardTest {
    @Test public void sourceLessDraftOnImportedDefinitionCannotInventLocalProvenance() {
        Fixture guard = new Fixture(); guard.artifactRevision(7L, true);
        RuleRevision unknown = guard.revision(8L, "DRAFT");
        assertRejected("REFERENCE_NAMESPACE_UNVERIFIABLE", () -> guard.requireLocalNamespace(guard.definition, unknown));
        assertRejected("REFERENCE_NAMESPACE_UNVERIFIABLE", () -> guard.inspectNamespace(guard.definition, "REVISION", 8L));
        Fixture nativeGuard = new Fixture();
        nativeGuard.requireLocalNamespace(nativeGuard.definition, nativeGuard.revision(8L, "DRAFT"));
    }

    @Test public void workspaceMatchingOnlyAnUnattributedDraftCannotHideImportedHistory() {
        Fixture guard = new Fixture(); guard.artifactRevision(7L, true);
        guard.workspace = List.of(guard.revision(8L, "DRAFT"));
        assertRejected("REFERENCE_NAMESPACE_UNVERIFIABLE", () -> guard.requireLocalNamespace(guard.definition, "WORKSPACE", 30L));
    }

    @Test public void explicitVerifiedNativeArtifactStillProvesLocalDraftDespiteOtherImportedHistory() {
        Fixture guard = new Fixture(); guard.artifactRevision(7L, true);
        RuleRevision local = guard.artifactRevision(9L, false);
        RuleRevision draft = guard.revision(8L, "DRAFT"); draft.setBaseRevisionId(local.getId()); draft.setBaseArtifactId(local.getArtifactId());
        guard.requireLocalNamespace(guard.definition, draft);
        guard.workspace = List.of(draft);
        guard.requireLocalNamespace(guard.definition, "WORKSPACE", 30L);
    }

    @Test public void readOnlyDraftProofReturnsVerifiedForeignAncestorWithoutCompilingTheDraft() {
        Fixture guard = new Fixture(); RuleRevision imported = guard.artifactRevision(7L, true);
        RuleRevision draft = guard.revision(8L, "DRAFT"); draft.setSourceType("REVISION"); draft.setSourceId(7L);
        draft.setModelJson("{\"unsavedDerivedShape\":true}");
        var proof = guard.inspectNamespace(guard.definition, "REVISION", 8L);
        assertTrue(proof.imported()); assertSame(imported, proof.frozenSource());
        assertEquals("{\"unsavedDerivedShape\":true}", draft.getModelJson());
        guard.deployments.clear();
        assertRejected("REFERENCE_NAMESPACE_UNVERIFIABLE", () -> guard.inspectNamespace(guard.definition, "REVISION", 8L));
    }

    @Test public void conflictingForeignAncestorsCannotProduceAnArbitraryDisplayCatalog() {
        Fixture guard = new Fixture(); guard.artifactRevision(7L, true); guard.artifactRevision(8L, true);
        RuleRevision draft = guard.revision(9L, "DRAFT"); draft.setSourceType("REVISION"); draft.setSourceId(7L); draft.setBaseRevisionId(8L);
        assertRejected("REFERENCE_NAMESPACE_UNVERIFIABLE", () -> guard.inspectNamespace(guard.definition, "REVISION", 9L));
    }

    @Test public void importedIdsEqualToLocalIdsStillCannotBeRecompiledAgainstLocalVariables() {
        Fixture guard = new Fixture();
        RuleRevision imported = guard.artifactRevision(7L, true);
        assertTrue(guard.verifyRevision(imported, guard.artifacts.get(107L)).getDiagnostics().isEmpty());
        assertRejected("REFERENCE_NAMESPACE_MIGRATION_REQUIRED", () -> guard.requireLocalNamespace(guard.definition, imported));
        assertEquals("Frozen_A", guard.verifyRevision(imported, guard.artifacts.get(107L)).getInputFields().get(0).getScriptName());
    }

    @Test public void nativeVerifiedArtifactAndNativeLegacyDraftRemainEditable() {
        Fixture guard = new Fixture();
        RuleRevision local = guard.artifactRevision(7L, false);
        guard.requireLocalNamespace(guard.definition, local);
        RuleRevision draft = guard.revision(8L, "DRAFT");
        draft.setBaseRevisionId(7L); draft.setBaseArtifactId(107L);
        guard.requireLocalNamespace(guard.definition, draft);
        guard.requireLocalNamespace(guard.definition, guard.revision(9L, "DRAFT"));
    }

    @Test public void reusedLocalPackageDeployedToAnotherDefinitionIsNotItsLocalNamespace() {
        Fixture guard = new Fixture();
        RuleRevision imported = guard.artifactRevision(7L, true);
        DecisionArtifact artifact = guard.artifacts.get(107L);
        artifact.setDefinitionId(30L); artifact.setRevisionId(7L);
        guard.definition.setId(31L); imported.setDefinitionId(31L);
        guard.deployments.get(107L).setTargetDefinitionId(31L);
        assertRejected("REFERENCE_NAMESPACE_MIGRATION_REQUIRED", () -> guard.requireLocalNamespace(guard.definition, imported));
    }

    @Test public void validLookingMetadataCannotBypassPackageOrDeploymentVerification() {
        Fixture corrupt = new Fixture(); RuleRevision local = corrupt.artifactRevision(7L, false);
        corrupt.artifacts.get(107L).getPackageContent()[0] ^= 1;
        assertRejected("REFERENCE_NAMESPACE_UNVERIFIABLE", () -> corrupt.requireLocalNamespace(corrupt.definition, local));
        Fixture missing = new Fixture(); RuleRevision imported = missing.artifactRevision(7L, true);
        missing.deployments.clear();
        assertRejected("REFERENCE_NAMESPACE_UNVERIFIABLE", () -> missing.requireLocalNamespace(missing.definition, imported));
    }

    @Test public void oldUnsafeDraftWithOnlyVersionSourceRetainsForeignNamespace() {
        Fixture guard = new Fixture(); RuleRevision imported = guard.artifactRevision(7L, true);
        guard.version(70L, imported);
        RuleRevision draft = guard.revision(8L, "DRAFT");
        draft.setSourceType("VERSION"); draft.setSourceId(70L);
        assertNull(draft.getBaseArtifactId());
        assertRejected("REFERENCE_NAMESPACE_MIGRATION_REQUIRED", () -> guard.requireLocalNamespace(guard.definition, draft));
        assertRejected("REFERENCE_NAMESPACE_MIGRATION_REQUIRED", () -> guard.requireLocalNamespace(guard.definition, "VERSION", 70L));
    }

    @Test public void oldUnsafeDraftIsDetectedThroughRevisionAndBaseArtifactPaths() {
        for (int mode = 0; mode < 3; mode++) {
            Fixture guard = new Fixture(); guard.artifactRevision(7L, true);
            RuleRevision draft = guard.revision(8L, "DRAFT");
            if (mode == 0) { draft.setSourceType("REVISION"); draft.setSourceId(7L); }
            if (mode == 1) draft.setBaseRevisionId(7L);
            if (mode == 2) draft.setBaseArtifactId(107L);
            assertRejected("REFERENCE_NAMESPACE_MIGRATION_REQUIRED", () -> guard.requireLocalNamespace(guard.definition, draft));
        }
    }

    @Test public void localArtifactBuiltByOldUnsafeDraftDoesNotEraseItsForeignAncestry() {
        Fixture guard = new Fixture(); guard.artifactRevision(7L, true);
        RuleRevision unsafePublished = guard.artifactRevision(8L, false);
        unsafePublished.setSourceType("REVISION"); unsafePublished.setSourceId(7L);
        assertRejected("REFERENCE_NAMESPACE_MIGRATION_REQUIRED", () -> guard.requireLocalNamespace(guard.definition, unsafePublished));
    }

    @Test public void missingSourceAndCrossDefinitionAndCyclesAreNotAssumedLocal() {
        for (int mode = 0; mode < 4; mode++) {
            Fixture guard = new Fixture(); RuleRevision draft = guard.revision(8L, "DRAFT");
            draft.setSourceType("REVISION"); draft.setSourceId(7L);
            if (mode == 1) guard.revision(7L, "DRAFT").setDefinitionId(999L);
            if (mode == 2) { RuleRevision prior = guard.revision(7L, "DRAFT"); prior.setSourceType("REVISION"); prior.setSourceId(8L); }
            if (mode == 3) draft.setSourceId(null);
            assertRejected("REFERENCE_NAMESPACE_UNVERIFIABLE", () -> guard.requireLocalNamespace(guard.definition, draft));
        }
    }

    @Test public void versionCannotPointToDifferentArtifactOrForgetFrozenRevision() {
        for (int mode = 0; mode < 3; mode++) {
            Fixture guard = new Fixture(); RuleRevision local = guard.artifactRevision(7L, false);
            RuleDefinitionVersion version = guard.version(70L, local);
            if (mode == 0) version.setArtifactId(999L);
            if (mode == 1) version.setArtifactDigest("different");
            if (mode == 2) version.setRevisionId(null);
            assertRejected("REFERENCE_NAMESPACE_UNVERIFIABLE", () -> guard.requireLocalNamespace(guard.definition, "VERSION", 70L));
        }
    }

    @Test public void legacyLocalVersionWithoutAnyArtifactStillWorks() {
        Fixture guard = new Fixture();
        RuleDefinitionVersion legacy = new RuleDefinitionVersion(); legacy.setId(70L); legacy.setDefinitionId(30L);
        guard.versions.put(70L, legacy);
        guard.requireLocalNamespace(guard.definition, "VERSION", 70L);
    }

    @Test public void omittedSourceWorkspaceCannotChooseLocalTwinOverImportedTwin() {
        Fixture guard = new Fixture();
        RuleRevision local = guard.artifactRevision(8L, false);
        RuleRevision imported = guard.artifactRevision(7L, true);
        local.setModelJson(imported.getModelJson());
        guard.workspace = List.of(local, imported);
        assertRejected("REFERENCE_NAMESPACE_MIGRATION_REQUIRED", () -> guard.requireLocalNamespace(guard.definition, "WORKSPACE", 30L));
        guard.workspace = List.of(imported, local);
        assertRejected("REFERENCE_NAMESPACE_MIGRATION_REQUIRED", () -> guard.requireLocalNamespace(guard.definition, "WORKSPACE", 30L));
    }

    @Test public void workspaceWithImportedHistoryAndUnattributedProjectionCannotBypassGuard() {
        Fixture guard = new Fixture(); guard.artifactRevision(7L, true);
        guard.workspace = List.of();
        assertRejected("REFERENCE_NAMESPACE_UNVERIFIABLE", () -> guard.requireLocalNamespace(guard.definition, "WORKSPACE", 30L));
        Fixture nativeWorkspace = new Fixture();
        nativeWorkspace.requireLocalNamespace(nativeWorkspace.definition, "WORKSPACE", 30L);
        assertRejected("REFERENCE_NAMESPACE_UNVERIFIABLE", () -> nativeWorkspace.requireLocalNamespace(nativeWorkspace.definition, "WORKSPACE", 999L));
    }

    private static void assertRejected(String code, org.junit.function.ThrowingRunnable work) {
        RuleGovernanceException failure = assertThrows(RuleGovernanceException.class, work);
        assertEquals(code, failure.getCode());
        assertTrue(failure.getMessage(), failure.getMessage().contains("离线配置迁移"));
    }

    static class Fixture extends RuleReferenceNamespaceGuard {
        final RuleDefinition definition = new RuleDefinition();
        final Map<Long, RuleRevision> revisions = new LinkedHashMap<>();
        final Map<Long, RuleDefinitionVersion> versions = new HashMap<>();
        final Map<Long, DecisionArtifact> artifacts = new HashMap<>();
        final Map<Long, ArtifactDeployment> deployments = new HashMap<>();
        List<RuleRevision> workspace = List.of();
        Fixture() { definition.setId(30L); definition.setModelType("TABLE"); }
        RuleRevision revision(long id, String state) {
            RuleRevision value = new RuleRevision(); value.setId(id); value.setDefinitionId(30L);
            value.setState(state); value.setLockVersion(0); value.setModelJson("{\"_varId\":7,\"varCode\":\"Frozen_A\"}");
            revisions.put(id, value); return value;
        }
        RuleRevision artifactRevision(long id, boolean imported) {
            RuleRevision revision = revision(id, "PUBLISHED");
            DecisionArtifactPackage pack = new DecisionArtifactPackage();
            pack.putMetadata("definitionId", 30L); pack.putMetadata("revisionId", id);
            pack.putMetadata("modelType", "TABLE"); pack.putMetadata("contentDigest", "c".repeat(64));
            pack.addComponent("rule/input-fields.json", "application/json", "[{\"varId\":7,\"refType\":\"VARIABLE\",\"scriptName\":\"Frozen_A\"}]".getBytes(StandardCharsets.UTF_8));
            pack.addComponent("rule/output-fields.json", "application/json", "[]".getBytes(StandardCharsets.UTF_8));
            DecisionArtifactPackageCodec codec = new DecisionArtifactPackageCodec();
            byte[] bytes = codec.encode(pack); var decoded = codec.decode(bytes);
            DecisionArtifact artifact = new DecisionArtifact(); artifact.setId(id + 100);
            artifact.setPackageContent(bytes); artifact.setArtifactDigest(decoded.getArtifactDigest()); artifact.setPackageDigest(decoded.getPackageDigest());
            if (!imported) { artifact.setDefinitionId(30L); artifact.setRevisionId(id); }
            revision.setArtifactId(artifact.getId()); revision.setContentDigest(imported ? artifact.getArtifactDigest() : "c".repeat(64));
            artifacts.put(artifact.getId(), artifact);
            if (imported) {
                ArtifactDeployment deployment = new ArtifactDeployment(); deployment.setId(id + 200);
                deployment.setArtifactId(artifact.getId()); deployment.setTargetDefinitionId(30L); deployment.setStatus("DEPLOYED");
                deployments.put(artifact.getId(), deployment);
            }
            return revision;
        }
        RuleDefinitionVersion version(long id, RuleRevision revision) {
            RuleDefinitionVersion version = new RuleDefinitionVersion(); version.setId(id); version.setDefinitionId(revision.getDefinitionId());
            version.setRevisionId(revision.getId()); version.setArtifactId(revision.getArtifactId());
            version.setArtifactDigest(artifacts.get(revision.getArtifactId()).getArtifactDigest()); versions.put(id, version); return version;
        }
        @Override protected RuleRevision loadRevision(Long id) { return revisions.get(id); }
        @Override protected RuleDefinitionVersion loadVersion(Long id) { return versions.get(id); }
        @Override protected DecisionArtifact loadArtifact(Long id) { return artifacts.get(id); }
        @Override protected RuleDefinitionContent loadContent(Long id) {
            RuleDefinitionContent content = new RuleDefinitionContent(); content.setDefinitionId(id);
            content.setModelJson(workspace.isEmpty() ? "{}" : workspace.get(0).getModelJson()); return content;
        }
        @Override protected List<RuleRevision> matchingWorkspaceRevisions(Long id, String json) { return workspace; }
        @Override protected RuleRevision findArtifactRevision(Long id, Long artifactId) {
            return revisions.values().stream().filter(value -> Objects.equals(id, value.getDefinitionId()) && Objects.equals(artifactId, value.getArtifactId())).findFirst().orElse(null);
        }
        @Override protected boolean hasImportedDeployment(Long id) { return deployments.values().stream().anyMatch(value -> Objects.equals(id, value.getTargetDefinitionId())); }
        @Override protected RuleFieldAnalyzer.ResolvedFields verifyRevision(RuleRevision revision, DecisionArtifact artifact) {
            return new PublishedRuleFieldSnapshotResolver() {
                @Override protected ArtifactDeployment loadDeployment(Long artifactId, Long definitionId) { return deployments.get(artifactId); }
                @Override protected RuleRevision loadTargetRevision(Long revisionId) { return revisions.get(revisionId); }
            }.resolveRevision(revision, artifact);
        }
    }
}
