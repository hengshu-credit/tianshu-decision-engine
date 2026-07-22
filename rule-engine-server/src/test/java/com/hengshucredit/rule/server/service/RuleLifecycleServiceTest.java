package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.RuleLifecycleActionRequest;
import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleLifecycleEvent;
import com.hengshucredit.rule.model.entity.RuleRevision;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuleLifecycleServiceTest {

    @Test
    public void createsDraftAndRejectsDirectPublish() {
        FixtureService service = new FixtureService();

        RuleRevision draft = service.ensureDraft(100L);

        Assert.assertEquals("DRAFT", draft.getState());
        Assert.assertEquals(Integer.valueOf(1), draft.getRevisionNo());
        IllegalStateException error = Assert.assertThrows(IllegalStateException.class,
                () -> service.publish(draft.getId(), new RuleLifecycleActionRequest()));
        Assert.assertTrue(error.getMessage().contains("APPROVED"));
    }

    @Test
    public void governedPathPublishesAndSecondRevisionOfflinesFirst() {
        FixtureService service = new FixtureService();
        RuleRevision first = service.ensureDraft(100L);
        service.submit(first.getId(), request("submit", null));
        service.approve(first.getId(), request("approve", null));
        service.publish(first.getId(), request("publish", null));
        Assert.assertEquals("PUBLISHED", first.getState());

        RuleRevision second = service.ensureDraft(100L);
        Assert.assertEquals("DRAFT", second.getState());
        Assert.assertEquals("PUBLISHED", first.getState());
        service.submit(second.getId(), request("submit 2", null));
        service.approve(second.getId(), request("approve 2", null));
        service.publish(second.getId(), request("publish 2", null));

        Assert.assertEquals("OFFLINE", first.getState());
        Assert.assertEquals("PUBLISHED", second.getState());
        Assert.assertEquals(second.getId(), service.activeRevisionId);
    }

    @Test
    public void reviewReturnRequiresCommentAndBreakingApprovalRequiresReason() {
        FixtureService service = new FixtureService();
        RuleRevision draft = service.ensureDraft(100L);
        service.submit(draft.getId(), request("submit", null));

        Assert.assertThrows(IllegalArgumentException.class,
                () -> service.returnToDraft(draft.getId(), new RuleLifecycleActionRequest()));
        service.preflight.setBreakingSchemaChange(true);
        service.preflight.setBreakingChangeReasonRequired(true);
        Assert.assertThrows(IllegalArgumentException.class,
                () -> service.approve(draft.getId(), request("approve", null)));

        service.approve(draft.getId(), request("approve", "business accepted"));
        Assert.assertEquals("APPROVED", draft.getState());
        Assert.assertEquals("business accepted", draft.getForcePublishReason());
    }

    @Test
    public void approveRejectsContentOrDependencyChangesAfterSubmit() {
        FixtureService service = new FixtureService();
        RuleRevision draft = service.ensureDraft(100L);
        service.submit(draft.getId(), request("submit", null));
        service.preflight.setContentDigest(String.valueOf('e').repeat(64));

        IllegalStateException error = Assert.assertThrows(IllegalStateException.class,
                () -> service.approve(draft.getId(), request("approve", null)));

        Assert.assertTrue(error.getMessage().contains("重新提交"));
        Assert.assertEquals("REVIEW", draft.getState());
    }

    private static RuleLifecycleActionRequest request(String comment, String forceReason) {
        RuleLifecycleActionRequest request = new RuleLifecycleActionRequest();
        request.setComment(comment);
        request.setForcePublishReason(forceReason);
        return request;
    }

    private static final class FixtureService extends RuleLifecycleService {
        private final RuleDefinition definition = new RuleDefinition();
        private final RuleDefinitionContent content = new RuleDefinitionContent();
        private final Map<Long, RuleRevision> revisions = new HashMap<>();
        private final List<RuleLifecycleEvent> events = new ArrayList<>();
        private long nextId = 1L;
        private Long activeRevisionId;
        private final RulePreflightReport preflight = new RulePreflightReport();

        private FixtureService() {
            definition.setId(100L);
            definition.setModelType("TABLE");
            definition.setCurrentVersion(1);
            content.setDefinitionId(100L);
            content.setModelJson("{}");
            preflight.setValid(true);
            preflight.setCompiledScript("return true;");
            preflight.setCompiledType("QLEXPRESS");
            preflight.setInputSchemaJson("{\"type\":\"object\"}");
            preflight.setOutputSchemaJson("{\"type\":\"object\"}");
            preflight.setContentDigest(String.valueOf('c').repeat(64));
            preflight.setDependencyDigest(String.valueOf('d').repeat(64));
        }

        @Override
        protected String actor() {
            return "alice";
        }

        @Override
        protected RuleDefinition loadDefinition(Long definitionId) {
            return definition;
        }

        @Override
        protected RuleDefinitionContent loadContent(Long definitionId) {
            return content;
        }

        @Override
        protected RuleRevision loadRevision(Long revisionId) {
            return revisions.get(revisionId);
        }

        @Override
        protected RuleRevision findDraft(Long definitionId) {
            return revisions.values().stream().filter(revision -> "DRAFT".equals(revision.getState()))
                    .max(Comparator.comparing(RuleRevision::getRevisionNo)).orElse(null);
        }

        @Override
        protected RuleRevision findPublishedRevision(Long definitionId) {
            return activeRevisionId == null ? null : revisions.get(activeRevisionId);
        }

        @Override
        protected int nextRevisionNo(Long definitionId) {
            return revisions.values().stream().map(RuleRevision::getRevisionNo)
                    .max(Integer::compareTo).orElse(0) + 1;
        }

        @Override
        protected void insertRevision(RuleRevision revision) {
            revision.setId(nextId++);
            revisions.put(revision.getId(), revision);
        }

        @Override
        protected void persistRevisionSnapshot(RuleRevision revision) {
        }

        @Override
        protected boolean compareAndSetState(RuleRevision revision, String expected, String target) {
            if (!expected.equals(revision.getState())) return false;
            revision.setState(target);
            revision.setLockVersion((revision.getLockVersion() == null ? 0 : revision.getLockVersion()) + 1);
            return true;
        }

        @Override
        protected RulePreflightReport preflight(Long revisionId) {
            return preflight;
        }

        @Override
        protected DecisionArtifact buildArtifact(Long revisionId, String actor) {
            DecisionArtifact artifact = new DecisionArtifact();
            artifact.setId(revisionId + 1000);
            artifact.setArtifactDigest(String.valueOf('a').repeat(64));
            return artifact;
        }

        @Override
        protected void activateArtifact(RuleRevision revision, String actor) {
            activeRevisionId = revision.getId();
        }

        @Override
        protected void deactivateArtifact(RuleRevision revision, String actor) {
            if (revision.getId().equals(activeRevisionId)) activeRevisionId = null;
        }

        @Override
        protected void insertEvent(RuleLifecycleEvent event) {
            events.add(event);
        }
    }
}
