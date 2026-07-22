package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.artifact.DecisionArtifactService;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RuleArtifactMigrationServiceTest {

    @Test
    public void bootstrapsDraftAndResolvablePublishedRuleIdempotently() {
        Fixture service = new Fixture();
        service.addDefinition(1L, false);
        service.addDefinition(2L, true);

        RuleArtifactMigrationService.MigrationReport first = service.migrateAll();
        RuleArtifactMigrationService.MigrationReport second = service.migrateAll();

        Assert.assertEquals(1, first.getDraftCreated());
        Assert.assertEquals(1, first.getPublishedArtifactCreated());
        Assert.assertEquals("DRAFT", service.revisions.get(1L).getState());
        Assert.assertEquals("PUBLISHED", service.revisions.get(2L).getState());
        Assert.assertEquals(Long.valueOf(900L), service.published.get(2L).getArtifactId());
        Assert.assertEquals(2, second.getSkipped());
    }

    @Test
    public void unresolvedPublishedRuleKeepsExistingRuntimeProjectionActive() {
        Fixture service = new Fixture();
        service.addDefinition(3L, true);
        service.failArtifactFor = 3L;

        RuleArtifactMigrationService.MigrationReport report = service.migrateAll();

        Assert.assertEquals(1, report.getUnresolved());
        Assert.assertEquals("DRAFT", service.revisions.get(3L).getState());
        Assert.assertEquals(Integer.valueOf(1), service.published.get(3L).getStatus());
        Assert.assertNull(service.published.get(3L).getArtifactId());
    }

    @Test
    public void doesNotAttachArtifactWhoseRuntimeProjectionDiffersFromLegacyPublishedRule() {
        Fixture service = new Fixture();
        service.addDefinition(4L, true);
        service.projectionScriptOverride = "score = 999;";

        RuleArtifactMigrationService.MigrationReport report = service.migrateAll();

        Assert.assertEquals(1, report.getUnresolved());
        Assert.assertEquals("DRAFT", service.revisions.get(4L).getState());
        Assert.assertNull(service.published.get(4L).getArtifactId());
        Assert.assertEquals("score = 4;", service.published.get(4L).getCompiledScript());
    }

    @Test
    public void resumesSystemMigrationRevisionAfterInterruptedArtifactBuild() {
        Fixture service = new Fixture();
        service.addDefinition(5L, true);
        service.failArtifactFor = 5L;

        RuleArtifactMigrationService.MigrationReport first = service.migrateAll();
        service.failArtifactFor = null;
        RuleArtifactMigrationService.MigrationReport second = service.migrateAll();

        Assert.assertEquals(1, first.getUnresolved());
        Assert.assertEquals(1, second.getPublishedArtifactCreated());
        Assert.assertEquals("PUBLISHED", service.revisions.get(5L).getState());
        Assert.assertEquals(Long.valueOf(900L), service.published.get(5L).getArtifactId());
    }

    private static final class Fixture extends RuleArtifactMigrationService {
        private final List<RuleDefinition> definitions = new ArrayList<>();
        private final Map<Long, RuleDefinitionContent> contents = new LinkedHashMap<>();
        private final Map<Long, RulePublished> published = new LinkedHashMap<>();
        private final Map<Long, RuleRevision> revisions = new LinkedHashMap<>();
        private Long failArtifactFor;
        private String projectionScriptOverride;
        private long revisionId = 100L;

        private void addDefinition(Long id, boolean online) {
            RuleDefinition definition = new RuleDefinition();
            definition.setId(id);
            definition.setCurrentVersion(online ? 4 : 1);
            definitions.add(definition);
            RuleDefinitionContent content = new RuleDefinitionContent();
            content.setDefinitionId(id);
            content.setModelJson("{\"definitionId\":" + id + "}");
            content.setCompiledScript("score = " + id + ";");
            contents.put(id, content);
            if (online) {
                RulePublished row = new RulePublished();
                row.setDefinitionId(id);
                row.setVersion(4);
                row.setStatus(1);
                row.setModelJson(content.getModelJson());
                row.setCompiledScript(content.getCompiledScript());
                published.put(id, row);
            }
        }

        @Override
        protected List<RuleDefinition> loadDefinitions() { return definitions; }

        @Override
        protected RuleRevision loadExistingRevision(Long definitionId) {
            return revisions.get(definitionId);
        }

        @Override
        protected RuleDefinitionContent loadContent(Long definitionId) {
            return contents.get(definitionId);
        }

        @Override
        protected RulePublished loadPublished(Long definitionId) {
            return published.get(definitionId);
        }

        @Override
        protected void insertRevision(RuleRevision revision) {
            revision.setId(revisionId++);
            revisions.put(revision.getDefinitionId(), revision);
        }

        @Override
        protected void updateRevision(RuleRevision revision) {
            revisions.put(revision.getDefinitionId(), revision);
        }

        @Override
        protected DecisionArtifact buildArtifact(Long revisionId, Long definitionId) {
            if (definitionId.equals(failArtifactFor)) {
                throw new IllegalStateException("unresolved reference");
            }
            DecisionArtifact artifact = new DecisionArtifact();
            artifact.setId(900L);
            artifact.setArtifactDigest("artifact-digest-" + definitionId);
            return artifact;
        }

        @Override
        protected DecisionArtifactService.RuntimeProjection loadRuntimeProjection(
                DecisionArtifact artifact, RulePublished row) {
            return new DecisionArtifactService.RuntimeProjection(row.getModelJson(),
                    projectionScriptOverride == null ? row.getCompiledScript() : projectionScriptOverride,
                    null, null, null);
        }

        @Override
        protected void updatePublished(RulePublished row) {
            published.put(row.getDefinitionId(), row);
        }

        @Override
        protected void insertMigrationEvent(RuleRevision revision, String action,
                                            String details) {
        }
    }
}
