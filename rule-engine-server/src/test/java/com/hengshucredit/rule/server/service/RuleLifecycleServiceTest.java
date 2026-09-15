package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.RuleLifecycleActionRequest;
import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveResponse;
import com.hengshucredit.rule.model.dto.RuleDraftSourceRequest;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionVersion;
import com.hengshucredit.rule.model.entity.RuleLifecycleEvent;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.enums.RuleDraftSourceType;
import com.hengshucredit.rule.model.enums.RuleRevisionState;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuleLifecycleServiceTest {
    @Test
    public void deletingLastDraftClearsInitialProjectionWithoutDeletingAudit() {
        FixtureService service = new FixtureService();
        RuleRevision first = service.createDraft(100L, null);
        RuleRevision second = service.createDraft(100L, null);
        service.deleteDraft(100L, first.getId(), 0);
        Assert.assertFalse(service.emptyProjection);
        service.deleteDraft(100L, second.getId(), 0);
        Assert.assertTrue(service.emptyProjection);
        Assert.assertEquals(2, service.revisions.size());
    }

    @Test
    public void offliningHistoricalOverwriteCannotDisableLatestProduction() {
        FixtureService service = new FixtureService();
        RuleRevision latest = revision(9L, 100L, "PUBLISHED", 2);
        RuleRevision historical = revision(10L, 100L, "PUBLISHED", 3);
        service.revisions.put(9L, latest);
        service.revisions.put(10L, historical);
        service.activeRevisionId = 9L;
        Assert.assertThrows(IllegalStateException.class,
                () -> service.offline(10L, request("offline historical", null)));
        Assert.assertEquals("PUBLISHED", historical.getState());
        Assert.assertEquals(Long.valueOf(9), service.activeRevisionId);
    }

    @Test
    public void explicitCreationAlwaysCreatesIndependentDrafts() {
        FixtureService service = new FixtureService();
        RuleRevision first = service.createDraft(100L, null);
        RuleRevision second = service.createDraft(100L, null);
        Assert.assertNotEquals(first.getId(), second.getId());
        Assert.assertEquals(2, service.insertCount);
    }

    @Test
    public void deletionPreservesSourceAndRejectsFrozenOrStaleDraft() {
        FixtureService service = new FixtureService();
        RuleRevision source = service.createDraft(100L, null);
        RuleRevision other = service.createDraft(100L, null);
        Assert.assertThrows(RuleGovernanceException.class,
                () -> service.deleteDraft(100L, source.getId(), 99));
        service.deleteDraft(100L, source.getId(), 0);
        Assert.assertEquals("DELETED", source.getState());
        Assert.assertEquals("DRAFT", other.getState());
        Assert.assertNotNull(service.revisions.get(source.getId()));
        Assert.assertEquals("DELETE_DRAFT", service.events.get(service.events.size() - 1).getAction());
        for (String state : new String[]{"REVIEW", "APPROVED", "PUBLISHED", "DELETED"}) {
            other.setState(state);
            Assert.assertThrows(RuleGovernanceException.class,
                    () -> service.deleteDraft(100L, other.getId(), 0));
        }
    }

    @Test
    public void secondReviewIsRejectedWhileOtherDraftRemainsEditable() {
        FixtureService service = new FixtureService();
        RuleRevision draft = service.createDraft(30L, null);
        service.pending = revision(77L, 30L, "REVIEW", 4);
        Assert.assertEquals("REVIEW_ALREADY_EXISTS", Assert.assertThrows(RuleGovernanceException.class,
                () -> service.submit(draft.getId(), request("submit", null))).getCode());
        Assert.assertEquals("DRAFT", service.requireEditableDraft(30L, draft.getId()).getState());
    }

    @Test
    public void draftSourceCopyPreservesContentAndSourceEvidenceAfterOriginalChanges() {
        FixtureService service = new FixtureService();
        RuleRevision source = revision(6L, 30L, "DRAFT", 2);
        source.setModelJson("{\"script\":\"return 1;\"}");
        source.setBaseRevisionId(5L);
        source.setBaseArtifactId(105L);
        service.revisions.put(6L, source);
        RuleRevision copy = service.prepareDesignerDraft(30L, RuleDraftSourceType.REVISION, 6L);
        Assert.assertEquals(0, service.insertCount);
        Assert.assertEquals(Long.valueOf(6), copy.getSourceId());
        Assert.assertEquals(Long.valueOf(5), copy.getBaseRevisionId());
        source.setModelJson("{\"script\":\"return 2;\"}");
        source.setState("DELETED");
        Assert.assertEquals("{\"script\":\"return 1;\"}", copy.getModelJson());
        Assert.assertEquals(Long.valueOf(105), copy.getBaseArtifactId());
        Assert.assertThrows(RuleGovernanceException.class,
                () -> service.prepareDesignerDraft(30L, RuleDraftSourceType.REVISION, 6L));
    }

    @Test
    public void explicitTemporarySaveStoresEditedVersionCopyAndKeepsSourceUnchanged() {
        FixtureService service = new FixtureService();
        RuleRevision source = revision(6L, 30L, "PUBLISHED", 2);
        source.setModelJson("{\"original\":true}");
        service.revisions.put(6L, source);
        RuleDraftSourceRequest request = sourceRequest(RuleDraftSourceType.REVISION, 6L);
        request.setModelJson("{\"edited\":true}");
        Assert.assertEquals(0, service.insertCount);
        RuleDraftSaveResponse result = service.saveTemporaryDraft(30L, request);
        Assert.assertEquals(1, service.insertCount);
        Assert.assertEquals("{\"edited\":true}", service.savedRequest.getModelJson());
        Assert.assertEquals("{\"edited\":true}", result.getRevision().getModelJson());
        Assert.assertEquals("{\"original\":true}", source.getModelJson());
    }

    @Test
    public void newRuleTemporarySaveRequiresPayloadAndDoesNotReuseAnUnrelatedDraft() {
        FixtureService service = new FixtureService();
        Assert.assertThrows(RuleGovernanceException.class, () -> service.saveTemporaryDraft(100L, new RuleDraftSourceRequest()));
        Assert.assertEquals(0, service.insertCount);
        RuleDraftSourceRequest request = new RuleDraftSourceRequest();
        request.setModelJson("{\"rules\":[]}");
        RuleDraftSaveResponse saved = service.saveTemporaryDraft(100L, request);
        Assert.assertEquals("{\"rules\":[]}", saved.getRevision().getModelJson());
        RuleDraftSaveResponse second = service.saveTemporaryDraft(100L, request);
        Assert.assertNotEquals(saved.getRevision().getId(), second.getRevision().getId());
        Assert.assertEquals(2, service.insertCount);
    }

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
    public void returnToDraftLocksDefinitionAndReloadsStateBeforeTransition() {
        FixtureService service = new FixtureService();
        service.revision = revision(6L, 30L, "REVIEW", 2);
        service.stateAfterDefinitionLock = "DRAFT";

        IllegalStateException error = Assert.assertThrows(
                IllegalStateException.class,
                () -> service.returnToDraft(6L,
                        request("重新编辑", null)));

        Assert.assertTrue(error.getMessage().contains("实际为 DRAFT"));
        Assert.assertEquals("load-revision",
                service.callOrder.get(0));
        Assert.assertEquals("lock-definition",
                service.callOrder.get(1));
        Assert.assertEquals("load-revision",
                service.callOrder.get(2));
        Assert.assertFalse(service.callOrder.contains("compare-state"));
        Assert.assertTrue(service.events.isEmpty());
    }

    @Test
    public void rejectionTerminatesReviewAndKeepsExistingDraft() {
        FixtureService service = new FixtureService();
        service.revision = revision(6L, 30L, "REVIEW", 2);
        service.pending = revision(7L, 30L, "DRAFT", 3);

        RuleRevision rejected = service.returnToDraft(6L,
                request("配置不满足要求", null));

        Assert.assertEquals("REJECTED", rejected.getState());
        Assert.assertEquals("DRAFT", service.pending.getState());
        Assert.assertTrue(service.callOrder.contains("compare-state"));
        Assert.assertTrue(service.callOrder.contains("restore-effective-projection"));
        Assert.assertEquals(1, service.events.size());
        Assert.assertEquals("REJECT",
                service.events.get(0).getAction());
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

    @Test
    public void approvedRevisionAllowsCreatingNextDraft() {
        FixtureService service = new FixtureService();
        service.pending = revision(6L, 30L, "APPROVED", 1);

        RuleRevision draft = service.createDraft(30L, 6L);

        Assert.assertEquals("DRAFT", draft.getState());
        Assert.assertEquals(Long.valueOf(6L), draft.getBaseRevisionId());
    }

    @Test
    public void reviewRevisionAllowsAnotherDraftButCannotBeItsPublicationBase() {
        FixtureService service = new FixtureService();
        service.pending = revision(6L, 30L, "REVIEW", 1);

        RuleGovernanceException error = Assert.assertThrows(
                RuleGovernanceException.class,
                () -> service.createDraft(30L, 6L));

        Assert.assertEquals(400, error.getHttpStatus());
        Assert.assertEquals("BASE_REVISION_INVALID",
                error.getCode());
        Assert.assertFalse(error.getIssues().isEmpty());
        Assert.assertEquals(0, service.insertCount);
        Assert.assertEquals("DRAFT", service.createDraft(30L, null).getState());
    }

    @Test
    public void createDraftLocksDefinitionBeforeReadingOrInsertingRevision() {
        FixtureService service = new FixtureService();

        service.createDraft(30L, null);

        Assert.assertEquals("lock-definition",
                service.callOrder.get(0));
        Assert.assertTrue(service.callOrder.indexOf("lock-definition")
                < service.callOrder.indexOf("insert-revision"));
    }

    @Test
    public void duplicateDraftInsertNeverReturnsAnUnrelatedConcurrentDraft() {
        FixtureService service = new FixtureService();
        service.revisions.put(6L,
                revision(6L, 30L, "APPROVED", 1));
        service.duplicateInsertBase = 6L;

        RuleGovernanceException error = Assert.assertThrows(RuleGovernanceException.class,
                () -> service.createDraft(30L, 6L));
        Assert.assertEquals("DRAFT_LOCK_CONFLICT", error.getCode());
    }

    @Test
    public void duplicateDraftInsertWithDifferentBaseReturnsConflict() {
        FixtureService service = new FixtureService();
        service.revisions.put(6L,
                revision(6L, 30L, "APPROVED", 1));
        service.duplicateInsertBase = 5L;

        RuleGovernanceException error = Assert.assertThrows(
                RuleGovernanceException.class,
                () -> service.createDraft(30L, 6L));

        Assert.assertEquals(409, error.getHttpStatus());
        Assert.assertEquals("DRAFT_LOCK_CONFLICT",
                error.getCode());
    }

    @Test
    public void existingDraftFromDifferentBaseIsNotReturnedAsRequestedDraft() {
        FixtureService service = new FixtureService();
        service.pending = revision(7L, 30L, "DRAFT", 2);
        service.pending.setBaseRevisionId(5L);
        service.revisions.put(6L, revision(6L, 30L, "PUBLISHED", 1));
        RuleRevision created = service.createDraft(30L, 6L);
        Assert.assertEquals(Long.valueOf(6L), created.getBaseRevisionId());
        Assert.assertNotEquals(service.pending.getId(), created.getId());
        Assert.assertEquals(Long.valueOf(7L), service.pending.getId());
    }

    @Test
    public void requireEditableDraftNeverCreatesMissingDraft() {
        FixtureService service = new FixtureService();

        RuleGovernanceException error = Assert.assertThrows(
                RuleGovernanceException.class,
                () -> service.requireEditableDraft(30L, 99L));

        Assert.assertEquals(409, error.getHttpStatus());
        Assert.assertEquals("FROZEN_REVISION_WRITE_REJECTED", error.getCode());
        Assert.assertEquals(0, service.insertCount);
    }

    @Test
    public void requireEditableDraftRejectsRevisionFromAnotherDefinitionAsBadRequest() {
        FixtureService service = new FixtureService();
        service.revision = revision(6L, 31L, "DRAFT", 1);

        RuleGovernanceException error = Assert.assertThrows(
                RuleGovernanceException.class,
                () -> service.requireEditableDraft(30L, 6L));

        Assert.assertEquals(400, error.getHttpStatus());
        Assert.assertEquals("REVISION_DEFINITION_MISMATCH",
                error.getCode());
        Assert.assertEquals(0, service.insertCount);
    }

    @Test
    public void submitDoesNotOverwriteRevisionFromCompatibilityContent() {
        FixtureService service = new FixtureService();
        service.revision = revision(6L, 30L, "DRAFT", 1);
        service.revision.setModelJson("{\"script\":\"revision = 1\"}");
        service.content.setModelJson("{\"script\":\"stale = 1\"}");

        service.submit(6L, new RuleLifecycleActionRequest());

        Assert.assertEquals("{\"script\":\"revision = 1\"}",
                service.revision.getModelJson());
    }

    @Test
    public void compatibilityPublishRejectsMissingApprovedRevisionAsGovernanceConflict() {
        FixtureService service = new FixtureService();

        RuleGovernanceException error = Assert.assertThrows(
                RuleGovernanceException.class,
                () -> service.publishApproved(
                        30L, new RuleLifecycleActionRequest()));

        Assert.assertEquals(409, error.getHttpStatus());
        Assert.assertEquals("FROZEN_REVISION_WRITE_REJECTED",
                error.getCode());
    }

    @Test
    public void compatibilityPublishUsesLatestApprovedRevision() {
        FixtureService service = new FixtureService();
        service.pending = revision(8L, 30L, "APPROVED", 2);
        service.pending.setArtifactId(1008L);

        RuleRevision published = service.publishApproved(
                30L, request("兼容入口发布", null));

        Assert.assertEquals(Long.valueOf(8L), published.getId());
        Assert.assertEquals("PUBLISHED", published.getState());
        Assert.assertEquals(Long.valueOf(8L),
                service.activeRevisionId);
        Assert.assertEquals("兼容入口发布",
                service.events.get(service.events.size() - 1)
                        .getComment());
    }

    @Test
    public void compatibilityOfflineRejectsMissingPublishedRevisionAsGovernanceConflict() {
        FixtureService service = new FixtureService();

        RuleGovernanceException error = Assert.assertThrows(
                RuleGovernanceException.class,
                () -> service.offlinePublished(
                        30L, new RuleLifecycleActionRequest()));

        Assert.assertEquals(409, error.getHttpStatus());
        Assert.assertEquals("FROZEN_REVISION_WRITE_REJECTED",
                error.getCode());
    }

    @Test
    public void compatibilityOfflineUsesCurrentPublishedRevision() {
        FixtureService service = new FixtureService();
        RuleRevision revision =
                revision(9L, 30L, "PUBLISHED", 3);
        service.revisions.put(revision.getId(), revision);
        service.activeRevisionId = revision.getId();

        RuleRevision offline = service.offlinePublished(
                30L, request("兼容入口下线", null));

        Assert.assertEquals(Long.valueOf(9L), offline.getId());
        Assert.assertEquals("OFFLINE", offline.getState());
        Assert.assertNull(service.activeRevisionId);
        Assert.assertEquals("兼容入口下线",
                service.events.get(service.events.size() - 1)
                        .getComment());
    }

    @Test
    public void createsDraftFromApprovedRevisionAndSavesServerLoadedContent() {
        FixtureService service = new FixtureService();
        RuleRevision source = revision(6L, 30L, "APPROVED", 2);
        source.setModelJson("{\"source\":\"revision\"}");
        source.setOpenApiConfigJson("{\"version\":\"revision\"}");
        source.setArtifactId(106L);
        service.revisions.put(source.getId(), source);

        RuleDraftSaveResponse response = service.createDraftFromSource(
                30L, sourceRequest(RuleDraftSourceType.REVISION, 6L));

        Assert.assertSame(service.savedResponse, response);
        Assert.assertEquals("DRAFT", response.getRevision().getState());
        Assert.assertEquals(Integer.valueOf(1),
                response.getRevision().getLockVersion());
        Assert.assertEquals(Long.valueOf(6L),
                response.getRevision().getBaseRevisionId());
        Assert.assertEquals(Long.valueOf(106L),
                response.getRevision().getBaseArtifactId());
        Assert.assertEquals("{\"source\":\"revision\"}",
                service.savedRequest.getModelJson());
        Assert.assertEquals("{\"version\":\"revision\"}",
                service.savedRequest.getOpenApiConfigJson());
        Assert.assertEquals(Boolean.TRUE,
                service.savedRequest.getUpdateOpenApiConfig());
        Assert.assertEquals(Integer.valueOf(0),
                service.savedRequest.getLockVersion());
        Assert.assertTrue(service.events.get(0).getDetailsJson()
                .contains("\"sourceType\":\"REVISION\""));
        Assert.assertTrue(service.events.get(0).getDetailsJson()
                .contains("\"sourceId\":6"));
        Assert.assertTrue(service.events.get(0).getDetailsJson()
                .contains("\"baseRevisionId\":6"));
    }

    @Test
    public void sourceDraftCreationLocksDefinitionBeforeConflictChecks() {
        FixtureService service = new FixtureService();
        RuleRevision source = revision(6L, 30L, "APPROVED", 2);
        source.setModelJson("{\"source\":\"revision\"}");
        service.revisions.put(source.getId(), source);

        service.createDraftFromSource(30L,
                sourceRequest(RuleDraftSourceType.REVISION, 6L));

        Assert.assertEquals("lock-definition", service.callOrder.get(0));
        Assert.assertTrue(service.callOrder.indexOf("lock-definition")
                < service.callOrder.indexOf("insert-revision"));
    }

    @Test
    public void createsDraftFromPublishedAndOfflineRevisions() {
        for (String state : new String[]{"PUBLISHED", "OFFLINE"}) {
            FixtureService service = new FixtureService();
            RuleRevision source = revision(6L, 30L, state, 2);
            source.setModelJson("{\"source\":\"" + state + "\"}");
            service.revisions.put(source.getId(), source);

            RuleDraftSaveResponse response = service.createDraftFromSource(
                    30L, sourceRequest(RuleDraftSourceType.REVISION, 6L));

            Assert.assertSame(state, service.savedResponse, response);
            Assert.assertEquals(Long.valueOf(6L),
                    response.getRevision().getBaseRevisionId());
        }
    }

    @Test
    public void createsDraftFromRejectedRevisionUsingOriginalBase() {
        FixtureService service = new FixtureService();
        RuleRevision source = revision(6L, 30L, "REJECTED", 2);
        source.setBaseRevisionId(5L);
        source.setBaseArtifactId(105L);
        source.setArtifactId(106L);
        source.setModelJson("{\"source\":\"rejected\"}");
        source.setOpenApiConfigJson("{\"version\":\"rejected\"}");
        service.revisions.put(source.getId(), source);

        RuleDraftSaveResponse response = service.createDraftFromSource(
                30L, sourceRequest(RuleDraftSourceType.REVISION, 6L));

        Assert.assertSame(service.savedResponse, response);
        Assert.assertEquals("DRAFT", response.getRevision().getState());
        Assert.assertEquals(Long.valueOf(5L),
                response.getRevision().getBaseRevisionId());
        Assert.assertEquals(Long.valueOf(105L),
                response.getRevision().getBaseArtifactId());
        Assert.assertEquals("{\"source\":\"rejected\"}",
                service.savedRequest.getModelJson());
        Assert.assertEquals("{\"version\":\"rejected\"}",
                service.savedRequest.getOpenApiConfigJson());
    }

    @Test
    public void createsDraftFromStableVersionSnapshotAndSavesSnapshotOpenApi() {
        FixtureService service = new FixtureService();
        RuleDefinitionVersion source = new RuleDefinitionVersion();
        source.setId(81L);
        source.setDefinitionId(30L);
        source.setModelJson("{\"source\":\"version\"}");
        source.setOpenApiConfigJson("{\"version\":\"snapshot\"}");
        service.versions.put(source.getId(), source);

        RuleDraftSaveResponse response = service.createDraftFromSource(
                30L, sourceRequest(RuleDraftSourceType.VERSION, 81L));

        Assert.assertSame(service.savedResponse, response);
        Assert.assertNull(response.getRevision().getBaseRevisionId());
        Assert.assertNull(response.getRevision().getBaseArtifactId());
        Assert.assertEquals("{\"source\":\"version\"}",
                service.savedRequest.getModelJson());
        Assert.assertEquals("{\"version\":\"snapshot\"}",
                service.savedRequest.getOpenApiConfigJson());
        Assert.assertTrue(service.events.get(0).getDetailsJson()
                .contains("\"sourceType\":\"VERSION\""));
        Assert.assertTrue(service.events.get(0).getDetailsJson()
                .contains("\"sourceId\":81"));
    }

    @Test
    public void existingDraftOrReviewAllowsIndependentSourceDraftCreation() {
        FixtureService service = new FixtureService();
        service.pending = revision(7L, 30L, "DRAFT", 3);

        service.revisions.put(6L, revision(6L, 30L, "PUBLISHED", 2));
        RuleRevision first = service.createDraftFromSource(30L,
                sourceRequest(RuleDraftSourceType.REVISION, 6L)).getRevision();
        Assert.assertNotEquals(service.pending.getId(), first.getId());

        service.pending.setState("REVIEW");
        RuleRevision second = service.createDraftFromSource(30L,
                sourceRequest(RuleDraftSourceType.REVISION, 6L)).getRevision();
        Assert.assertNotEquals(first.getId(), second.getId());
        Assert.assertEquals("REVIEW", service.pending.getState());
        Assert.assertEquals(2, service.insertCount);
    }

    @Test
    public void sourceDraftCanBeCopiedButReviewAndForeignRevisionAreRejected() {
        FixtureService service = new FixtureService();
        RuleRevision source = revision(6L, 30L, "DRAFT", 2);
        service.revisions.put(source.getId(), source);

        RuleDraftSaveResponse saved = service.createDraftFromSource(30L,
                sourceRequest(RuleDraftSourceType.REVISION, 6L));
        Assert.assertNotEquals(source.getId(), saved.getRevision().getId());
        Assert.assertEquals(source.getId(), saved.getRevision().getSourceId());

        source.setState("REVIEW");
        RuleGovernanceException reviewError = Assert.assertThrows(
                RuleGovernanceException.class,
                () -> service.createDraftFromSource(30L,
                        sourceRequest(RuleDraftSourceType.REVISION, 6L)));
        Assert.assertEquals("SOURCE_REVIEW_REQUIRES_RETURN",
                reviewError.getCode());

        source.setState("APPROVED");
        source.setDefinitionId(31L);
        RuleGovernanceException foreignError = Assert.assertThrows(
                RuleGovernanceException.class,
                () -> service.createDraftFromSource(30L,
                        sourceRequest(RuleDraftSourceType.REVISION, 6L)));
        Assert.assertEquals("SOURCE_NOT_FOUND", foreignError.getCode());

        RuleGovernanceException missingError = Assert.assertThrows(
                RuleGovernanceException.class,
                () -> service.createDraftFromSource(30L,
                        sourceRequest(RuleDraftSourceType.REVISION, 99L)));
        Assert.assertEquals("SOURCE_NOT_FOUND", missingError.getCode());
    }

    @Test
    public void duplicateSourceDraftInsertReturnsStableGovernanceConflict() {
        FixtureService service = new FixtureService();
        RuleRevision source = revision(6L, 30L, "OFFLINE", 2);
        service.revisions.put(source.getId(), source);
        service.duplicateInsertBase = 6L;

        RuleGovernanceException error = Assert.assertThrows(
                RuleGovernanceException.class,
                () -> service.createDraftFromSource(30L,
                        sourceRequest(RuleDraftSourceType.REVISION, 6L)));

        Assert.assertEquals(409, error.getHttpStatus());
        Assert.assertEquals("DRAFT_LOCK_CONFLICT", error.getCode());
    }

    private static RuleLifecycleActionRequest request(String comment, String forceReason) {
        RuleLifecycleActionRequest request = new RuleLifecycleActionRequest();
        request.setComment(comment);
        request.setForcePublishReason(forceReason);
        return request;
    }

    private static RuleDraftSourceRequest sourceRequest(
            RuleDraftSourceType sourceType, Long sourceId) {
        RuleDraftSourceRequest request = new RuleDraftSourceRequest();
        request.setSourceType(sourceType);
        request.setSourceId(sourceId);
        return request;
    }

    private static RuleRevision revision(Long id, Long definitionId,
                                         String state, int revisionNo) {
        RuleRevision revision = new RuleRevision();
        revision.setId(id);
        revision.setDefinitionId(definitionId);
        revision.setState(state);
        revision.setRevisionNo(revisionNo);
        revision.setLockVersion(0);
        return revision;
    }

    private static final class FixtureService extends RuleLifecycleService {
        private final RuleDefinition definition = new RuleDefinition();
        private final RuleDefinitionContent content = new RuleDefinitionContent();
        private final Map<Long, RuleRevision> revisions = new HashMap<>();
        private final Map<Long, RuleDefinitionVersion> versions = new HashMap<>();
        private final List<RuleLifecycleEvent> events = new ArrayList<>();
        private long nextId = 1L;
        private Long activeRevisionId;
        private boolean emptyProjection;

        @Override
        protected void clearDesignerProjectionIfUnused(Long definitionId) {
            emptyProjection = versions.isEmpty() && revisions.values().stream().allMatch(value -> "DELETED".equals(value.getState()));
        }
        private final RulePreflightReport preflight = new RulePreflightReport();
        private RuleRevision pending;
        private RuleRevision revision;
        private int insertCount;
        private Long duplicateInsertBase;
        private final List<String> callOrder = new ArrayList<>();
        private String stateAfterDefinitionLock;
        private RuleDraftSaveRequest savedRequest;
        private RuleDraftSaveResponse savedResponse;

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
        protected RuleDefinition lockDefinition(Long definitionId) {
            callOrder.add("lock-definition");
            if (stateAfterDefinitionLock != null && revision != null) {
                revision.setState(stateAfterDefinitionLock);
            }
            return definition;
        }

        @Override
        protected RuleDefinitionContent loadContent(Long definitionId) {
            return content;
        }

        @Override
        protected RuleRevision loadRevision(Long revisionId) {
            callOrder.add("load-revision");
            if (revision != null && revisionId.equals(revision.getId())) {
                return revision;
            }
            if (pending != null && revisionId.equals(pending.getId())) {
                return pending;
            }
            return revisions.get(revisionId);
        }

        @Override
        protected RuleDefinitionVersion loadVersion(
                Long definitionId, Long versionId) {
            RuleDefinitionVersion version = versions.get(versionId);
            return version != null && definitionId.equals(
                    version.getDefinitionId()) ? version : null;
        }

        @Override
        protected RuleRevision findDraft(Long definitionId) {
            callOrder.add("find-draft");
            if (pending != null && "DRAFT".equals(pending.getState())
                    && definitionId.equals(pending.getDefinitionId())) {
                return pending;
            }
            return revisions.values().stream().filter(revision -> "DRAFT".equals(revision.getState()))
                    .max(Comparator.comparing(RuleRevision::getRevisionNo)).orElse(null);
        }

        @Override
        protected RuleRevision findPendingRevision(Long definitionId) {
            callOrder.add("find-pending");
            return pending != null && "REVIEW".equals(pending.getState())
                    && definitionId.equals(pending.getDefinitionId()) ? pending : null;
        }

        @Override
        protected RuleRevision findLatestRevision(Long definitionId,
                                                  RuleRevisionState state) {
            if (pending != null && state.name().equals(pending.getState())
                    && definitionId.equals(pending.getDefinitionId())) {
                return pending;
            }
            return revisions.values().stream()
                    .filter(item -> state.name().equals(item.getState())
                            && definitionId.equals(item.getDefinitionId()))
                    .max(Comparator.comparing(RuleRevision::getRevisionNo))
                    .orElse(null);
        }

        @Override
        protected RuleRevision findPublishedRevision(Long definitionId) {
            return activeRevisionId == null ? null : revisions.get(activeRevisionId);
        }

        @Override
        protected int nextRevisionNo(Long definitionId) {
            callOrder.add("next-revision-no");
            return revisions.values().stream().map(RuleRevision::getRevisionNo)
                    .max(Integer::compareTo).orElse(0) + 1;
        }

        @Override
        protected void insertRevision(RuleRevision revision) {
            callOrder.add("insert-revision");
            if (duplicateInsertBase != null) {
                pending = revision(7L, revision.getDefinitionId(),
                        "DRAFT", revision.getRevisionNo());
                pending.setBaseRevisionId(duplicateInsertBase);
                throw new DuplicateKeyException(
                        "duplicate active draft");
            }
            revision.setId(nextId++);
            revisions.put(revision.getId(), revision);
            insertCount++;
        }

        @Override
        protected RuleDraftSaveResponse saveDraft(
                RuleDraftSaveRequest request) {
            savedRequest = request;
            RuleRevision draft = revisions.get(request.getRevisionId());
            draft.setModelJson(request.getModelJson());
            draft.setLockVersion(request.getLockVersion() + 1);
            savedResponse = new RuleDraftSaveResponse();
            savedResponse.setRevision(draft);
            savedResponse.setCompileSuccess(true);
            return savedResponse;
        }

        @Override
        protected void persistRevisionSnapshot(RuleRevision revision) {
        }

        @Override
        protected boolean compareAndSetState(RuleRevision revision, String expected, String target) {
            callOrder.add("compare-state");
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
        protected void restoreEffectiveProjection(RuleRevision rejected) {
            callOrder.add("restore-effective-projection");
        }

        @Override
        protected void insertEvent(RuleLifecycleEvent event) {
            events.add(event);
        }
    }
}
