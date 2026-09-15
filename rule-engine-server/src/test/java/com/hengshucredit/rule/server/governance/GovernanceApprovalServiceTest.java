package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.dto.GovernanceReviewRequest;
import com.hengshucredit.rule.model.dto.GovernanceSubmitRequest;
import com.hengshucredit.rule.model.entity.GovernanceApprovalEvent;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import com.hengshucredit.rule.server.mapper.GovernanceApprovalRequestMapper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class GovernanceApprovalServiceTest {

    @Test
    public void preflightPassesLifecycleActionToAdapterValidation() {
        ActionAwareService service = new ActionAwareService();
        GovernanceApprovalRequest request = new GovernanceApprovalRequest();
        request.setResourceType("FIELD_VALIDATION");
        request.setResourceId(17L);
        request.setAction("DELETE");

        GovernancePreflightReport report = service.evaluateRequest(
                request, ResourceSnapshot.ofJson("{\"id\":17}"));

        Assert.assertEquals("DELETE", service.validationAction);
        Assert.assertFalse(report.valid());
        Assert.assertEquals("ACTION_BLOCKED",
                report.errors().get(0).code());
    }

    @Test
    public void rejectLeavesEffectiveVersionAndTerminatesDraft() {
        TestService service = new TestService();
        service.resource.setId(7L);
        service.resource.setEffectiveVersionId(3L);
        service.resource.setEffectiveVersionNo(3);
        service.request.setId(12L);
        service.request.setResourceType("RULE");
        service.request.setResourceId(7L);
        service.request.setAction("UPDATE");
        service.request.setStatus("PENDING");
        service.request.setActiveResourceKey("RULE:7");
        service.request.setApplicant("same-user");
        service.request.setBaseVersionId(3L);
        service.oldVersion.setId(3L);
        service.oldVersion.setResourceType("RULE");
        service.oldVersion.setResourceId(7L);
        service.oldVersion.setSnapshotJson("{\"name\":\"effective\"}");
        service.oldVersion.setEffectiveStatus("ACTIVE");

        GovernanceReviewRequest review = new GovernanceReviewRequest();
        review.setComment("字段配置不符合要求");
        service.reject(12L, review, "same-user");

        Assert.assertEquals("REJECTED", service.request.getStatus());
        Assert.assertNull(service.request.getActiveResourceKey());
        Assert.assertEquals(Long.valueOf(3L),
                service.resource.getEffectiveVersionId());
        Assert.assertEquals(Integer.valueOf(3),
                service.resource.getEffectiveVersionNo());
        Assert.assertEquals(1, service.events.size());
        Assert.assertEquals("REJECT", service.events.get(0).getAction());
        Assert.assertEquals("same-user", service.request.getReviewer());
        Assert.assertEquals("REJECTED", service.terminatedStatus);
        Assert.assertEquals(Long.valueOf(7L), service.terminatedResourceId);
        Assert.assertNotNull(service.terminatedEffectiveSnapshot);
        Assert.assertEquals("{\"name\":\"effective\"}",
                service.terminatedEffectiveSnapshot.snapshotJson());
    }

    @Test
    public void rejectedCreateTerminatesAdapterStagingFromRequestSnapshot() {
        TestService service = new TestService();
        service.request.setId(13L);
        service.request.setResourceType("LIST_RECORD_BATCH");
        service.request.setResourceId(0L);
        service.request.setAction("CREATE");
        service.request.setStatus("PENDING");
        service.request.setApplicant("owner");
        service.request.setSubmittedSnapshotJson("{\"batchId\":101}");

        service.reject(13L, new GovernanceReviewRequest(), "owner");

        Assert.assertEquals("REJECTED", service.terminatedStatus);
        Assert.assertNull(service.terminatedResourceId);
        Assert.assertNotNull(service.terminatedCreateSnapshot);
        Assert.assertEquals("{\"batchId\":101}",
                service.terminatedCreateSnapshot.snapshotJson());
    }

    @Test
    public void rejectedRequestIsImmutable() {
        TestService service = new TestService();
        service.request.setId(12L);
        service.request.setStatus("REJECTED");

        try {
            service.saveDraftSnapshot(12L, "{\"name\":\"changed\"}", "user");
            Assert.fail("rejected request must be immutable");
        } catch (GovernanceApprovalService.GovernanceStateException expected) {
            Assert.assertEquals("APPROVAL_STATE_CONFLICT",
                    expected.getCode());
        }
    }

    @Test
    public void anotherUserCannotOverwriteExistingDraft() {
        TestService service = new TestService();
        service.request.setId(12L);
        service.request.setStatus("EDITING");
        service.request.setApplicant("owner");
        service.request.setDraftSnapshotJson("{\"name\":\"owned\"}");

        try {
            service.saveDraftSnapshot(
                    12L, "{\"name\":\"stolen\"}", "other-user");
            Assert.fail("another user must not overwrite an owned draft");
        } catch (GovernanceApprovalService.GovernanceStateException expected) {
            Assert.assertEquals("GOVERNANCE_DRAFT_OWNED_BY_ANOTHER",
                    expected.getCode());
            Assert.assertEquals("{\"name\":\"owned\"}",
                    service.request.getDraftSnapshotJson());
        }
    }

    @Test
    public void anotherUserCannotPreflightSubmitOrCancelOwnedDraft() {
        TestService service = new TestService();
        service.request.setId(12L);
        service.request.setResourceType("RULE");
        service.request.setResourceId(7L);
        service.request.setAction("UPDATE");
        service.request.setStatus("EDITING");
        service.request.setApplicant("owner");
        service.request.setDraftSnapshotJson("{\"name\":\"owned\"}");

        assertOwnedByAnother(() ->
                service.preflight(12L, "other-user"));
        assertOwnedByAnother(() ->
                service.submit(12L, new GovernanceSubmitRequest(),
                        "other-user"));
        assertOwnedByAnother(() ->
                service.cancel(12L, new GovernanceReviewRequest(),
                        "other-user"));
        Assert.assertEquals("EDITING", service.request.getStatus());
        Assert.assertEquals("owner", service.request.getApplicant());
        Assert.assertTrue(service.events.isEmpty());
    }

    @Test
    public void anotherUserCannotReuseDraftForExistingResource() {
        TestService service = new TestService();
        service.resource.setId(5L);
        service.resource.setResourceType("RULE");
        service.resource.setResourceId(7L);
        service.resource.setEffectiveVersionId(3L);
        service.resource.setEffectiveVersionNo(3);
        service.request.setId(12L);
        service.request.setResourceType("RULE");
        service.request.setResourceId(7L);
        service.request.setAction("UPDATE");
        service.request.setStatus("EDITING");
        service.request.setApplicant("owner");
        service.request.setRequestNo("GOV-TEST-12");
        service.request.setActiveResourceKey("RULE:7");
        service.request.setDraftSnapshotJson("{\"name\":\"owned\"}");

        GovernanceDraftRequest draft = new GovernanceDraftRequest();
        draft.setResourceType("RULE");
        draft.setResourceId(7L);
        draft.setAction("UPDATE");
        draft.setSnapshotJson("{\"name\":\"stolen\"}");

        GovernanceApprovalService.GovernanceStateException conflict =
                assertOwnedByAnother(() ->
                        service.createDraft(draft, "other-user"));
        Assert.assertTrue(conflict.getMessage().contains("owner"));
        Assert.assertTrue(conflict.getMessage().contains("GOV-TEST-12"));
        Assert.assertTrue(conflict.getMessage().contains("提交或撤回"));
        Assert.assertEquals("{\"name\":\"owned\"}",
                service.request.getDraftSnapshotJson());
    }

    @Test
    public void equivalentCreateDraftsReuseStableBusinessIdentity() {
        TestService service = new TestService();
        GovernanceDraftRequest firstDraft = createRuleDraft(
                "risk-rule", "初始名称");
        GovernanceApprovalRequest first =
                service.createDraft(firstDraft, "owner");
        Long firstId = first.getId();
        String firstKey = first.getActiveResourceKey();

        GovernanceDraftRequest renamedDraft = createRuleDraft(
                "risk-rule", "修改后的名称");
        GovernanceApprovalRequest reused =
                service.createDraft(renamedDraft, "owner");

        Assert.assertEquals(firstId, reused.getId());
        Assert.assertEquals(firstKey, reused.getActiveResourceKey());
        Assert.assertEquals(1, service.insertRequestCalls);
        Assert.assertTrue(reused.getDraftSnapshotJson()
                .contains("修改后的名称"));
    }

    @Test
    public void equivalentCreateDraftCannotBeClaimedByAnotherUser() {
        TestService service = new TestService();
        service.createDraft(
                createRuleDraft("risk-rule", "初始名称"), "owner");

        assertOwnedByAnother(() -> service.createDraft(
                createRuleDraft("risk-rule", "恶意覆盖"), "other-user"));
        Assert.assertEquals(1, service.insertRequestCalls);
        Assert.assertTrue(service.request.getDraftSnapshotJson()
                .contains("初始名称"));
    }

    @Test
    public void equivalentProjectRuleBindingsReuseTheSameActiveDraft() {
        TestService service = new TestService();
        GovernanceApprovalRequest first = service.createDraft(
                createBindingDraft(30L, 9L), "owner");
        Long firstId = first.getId();
        String firstKey = first.getActiveResourceKey();

        GovernanceApprovalRequest reused = service.createDraft(
                createBindingDraft(30L, 9L), "owner");

        Assert.assertEquals(firstId, reused.getId());
        Assert.assertEquals(
                "RULE_PROJECT_BINDING:CREATE:30:9", firstKey);
        Assert.assertEquals(firstKey, reused.getActiveResourceKey());
        Assert.assertEquals(1, service.insertRequestCalls);
    }

    @Test
    public void differentProjectRuleBindingsUseDifferentActiveKeys() {
        TestService service = new TestService();
        GovernanceApprovalRequest first = service.createDraft(
                createBindingDraft(30L, 9L), "owner");
        String firstKey = first.getActiveResourceKey();
        service.request.setId(null);
        service.request.setActiveResourceKey(null);

        GovernanceApprovalRequest second = service.createDraft(
                createBindingDraft(30L, 10L), "owner");

        Assert.assertNotEquals(firstKey, second.getActiveResourceKey());
        Assert.assertEquals(
                "RULE_PROJECT_BINDING:CREATE:30:10",
                second.getActiveResourceKey());
    }

    @Test
    public void listLibraryCreateDraftUsesScopedCodeIdentity() {
        TestService service = new TestService();
        GovernanceApprovalRequest first = service.createDraft(
                createListLibraryDraft("Mobile_Black", "原名称", 7L),
                "owner");
        String firstKey = first.getActiveResourceKey();

        GovernanceApprovalRequest reused = service.createDraft(
                createListLibraryDraft("Mobile_Black", "修改名称", 7L),
                "owner");

        Assert.assertEquals(first.getId(), reused.getId());
        Assert.assertEquals(firstKey, reused.getActiveResourceKey());
        Assert.assertEquals(1, service.insertRequestCalls);
    }

    @Test
    public void fieldValidationCreateDraftUsesScopedCodeIdentity() {
        TestService service = new TestService();
        GovernanceApprovalRequest first = service.createDraft(
                createFieldValidationDraft(
                        "Mobile_Check", "初始名称", 7L), "owner");
        String firstKey = first.getActiveResourceKey();

        GovernanceApprovalRequest reused = service.createDraft(
                createFieldValidationDraft(
                        "Mobile_Check", "修改名称", 7L), "owner");

        Assert.assertTrue(firstKey.startsWith(
                "FIELD_VALIDATION:CREATE:"));
        Assert.assertEquals(firstKey, reused.getActiveResourceKey());
        Assert.assertEquals(1, service.insertRequestCalls);
    }

    @Test
    public void billingConfigCreateDraftUsesScopedCodeIdentity() {
        TestService service = new TestService();
        GovernanceApprovalRequest first = service.createDraft(
                createBillingConfigDraft(
                        "Engine_Count", "初始名称", 7L), "owner");
        String firstKey = first.getActiveResourceKey();

        GovernanceApprovalRequest reused = service.createDraft(
                createBillingConfigDraft(
                        "Engine_Count", "修改名称", 7L), "owner");

        Assert.assertTrue(firstKey.startsWith(
                "BILLING_CONFIG:CREATE:"));
        Assert.assertEquals(firstKey, reused.getActiveResourceKey());
        Assert.assertEquals(1, service.insertRequestCalls);
    }

    @Test(expected = IllegalArgumentException.class)
    public void projectRuleBindingCreateRequiresPositiveIdReferences() {
        TestService service = new TestService();

        service.createDraft(createBindingDraft(0L, 9L), "owner");
    }

    @Test
    public void concurrentCreateCollisionReturnsStableBusinessConflict() {
        InsertService service = new InsertService();
        GovernanceApprovalRequestMapper mapper =
                (GovernanceApprovalRequestMapper) Proxy.newProxyInstance(
                        GovernanceApprovalRequestMapper.class
                                .getClassLoader(),
                        new Class<?>[]{
                                GovernanceApprovalRequestMapper.class},
                        (proxy, method, args) -> {
                            if ("insert".equals(method.getName())) {
                                throw new DuplicateKeyException(
                                        "duplicate active_resource_key");
                            }
                            return null;
                        });
        ReflectionTestUtils.setField(service, "requestMapper", mapper);

        try {
            service.insert(new GovernanceApprovalRequest());
            Assert.fail("concurrent duplicate draft must be a conflict");
        } catch (GovernanceApprovalService.GovernanceStateException expected) {
            Assert.assertEquals("GOVERNANCE_REQUEST_ALREADY_ACTIVE",
                    expected.getCode());
        }
    }

    @Test
    public void failedApplyPropagatesOriginalCollisionWithoutCommittingConflictState() {
        TestService service = new TestService();
        service.applyCollision = true;
        service.request.setId(12L);
        service.request.setResourceType("RULE");
        service.request.setResourceId(0L);
        service.request.setAction("CREATE");
        service.request.setStatus("PENDING");
        service.request.setSubmittedSnapshotJson(
                "{\"ruleCode\":\"existing\","
                        + "\"ruleName\":\"重复规则\","
                        + "\"modelType\":\"SCRIPT\"}");
        service.request.setSnapshotDigest("snapshot");
        service.request.setDependencyDigest("dep-digest");
        service.request.setActiveResourceKey("RULE:CREATE:key");

        Assert.assertThrows(DuplicateKeyException.class,
                () -> service.approve(12L, new GovernanceReviewRequest(), "reviewer"));
        Assert.assertEquals("PENDING", service.request.getStatus());
        Assert.assertEquals("RULE:CREATE:key", service.request.getActiveResourceKey());
        Assert.assertTrue(service.events.isEmpty());
    }

    @Test
    public void selfApprovalIsNotBlockedByApplicantIdentity() {
        TestService service = new TestService();
        service.request.setId(12L);
        service.request.setStatus("PENDING");
        service.request.setApplicant("same-user");

        Assert.assertTrue(service.canReview(service.request, "same-user"));
    }

    @Test
    public void submitFreezesSnapshotAndDependencyVersion() {
        TestService service = new TestService();
        service.request.setId(12L);
        service.request.setResourceType("RULE");
        service.request.setResourceId(7L);
        service.request.setStatus("EDITING");
        service.request.setApplicant("same-user");
        service.request.setDraftSnapshotJson("{\"name\":\"v4\"}");
        GovernanceSubmitRequest submit = new GovernanceSubmitRequest();
        submit.setComment("申请发布");

        service.submit(12L, submit, "same-user");

        Assert.assertEquals("PENDING", service.request.getStatus());
        Assert.assertEquals("{\"name\":\"v4\"}",
                service.request.getSubmittedSnapshotJson());
        Assert.assertEquals("dep-digest",
                service.request.getDependencyDigest());
        Assert.assertEquals("SUBMIT",
                service.events.get(0).getAction());
    }

    @Test
    public void pendingRequestCannotRecomputeFrozenPreflight() {
        TestService service = new TestService();
        service.request.setId(12L);
        service.request.setStatus("PENDING");
        service.request.setApplicant("same-user");

        try {
            service.preflight(12L, "same-user");
            Assert.fail("pending request preflight must stay frozen");
        } catch (GovernanceApprovalService.GovernanceStateException expected) {
            Assert.assertEquals("APPROVAL_STATE_CONFLICT",
                    expected.getCode());
        }
    }

    @Test
    public void updateDraftStartsWithEffectiveSecretPayload() {
        TestService service = new TestService();
        service.resource.setId(5L);
        service.resource.setResourceType("RULE");
        service.resource.setResourceId(7L);
        service.resource.setEffectiveVersionId(3L);
        service.resource.setEffectiveVersionNo(3);
        service.effectiveSnapshot = new ResourceSnapshot(
                "{\"name\":\"old\"}", "ACTIVE",
                "encrypted-secret", "secret-digest");
        GovernanceDraftRequest draft = new GovernanceDraftRequest();
        draft.setResourceType("RULE");
        draft.setResourceId(7L);
        draft.setAction("UPDATE");
        draft.setSnapshotJson("{\"name\":\"new\"}");

        GovernanceApprovalRequest created =
                service.createDraft(draft, "same-user");

        Assert.assertEquals("encrypted-secret",
                created.getSecretPayloadCiphertext());
        Assert.assertEquals("secret-digest",
                created.getSecretDigest());
    }

    @Test
    public void terminalActiveKeyIsReleasedBeforeNewDraft() {
        TestService service = new TestService();
        service.resource.setId(5L);
        service.resource.setResourceType("RULE");
        service.resource.setResourceId(7L);
        service.resource.setEffectiveVersionId(3L);
        service.resource.setEffectiveVersionNo(3);
        service.staleActiveRequest.setId(11L);
        service.staleActiveRequest.setStatus("REJECTED");
        service.staleActiveRequest.setActiveResourceKey("RULE:7");

        GovernanceDraftRequest draft = new GovernanceDraftRequest();
        draft.setResourceType("RULE");
        draft.setResourceId(7L);
        draft.setAction("DELETE");

        GovernanceApprovalRequest created =
                service.createDraft(draft, "same-user");

        Assert.assertNull(service.staleActiveRequest
                .getActiveResourceKey());
        Assert.assertEquals("DELETE", created.getAction());
        Assert.assertEquals("EDITING", created.getStatus());
    }

    @Test
    public void approvalCreatesNewVersionWithoutChangingHistory() {
        TestService service = new TestService();
        service.resource.setId(5L);
        service.resource.setResourceType("RULE");
        service.resource.setResourceId(7L);
        service.resource.setEffectiveVersionId(3L);
        service.resource.setEffectiveVersionNo(3);
        service.resource.setEffectiveStatus("ACTIVE");
        service.request.setId(12L);
        service.request.setResourceType("RULE");
        service.request.setResourceId(7L);
        service.request.setAction("UPDATE");
        service.request.setStatus("PENDING");
        service.request.setBaseVersionId(3L);
        service.request.setSubmittedSnapshotJson("{\"name\":\"v4\"}");
        service.request.setSnapshotDigest("snapshot-v4");
        service.request.setDependencyDigest("dep-digest");
        service.request.setActiveResourceKey("RULE:7");

        GovernanceReviewRequest review = new GovernanceReviewRequest();
        review.setComment("通过");
        service.approve(12L, review, "same-user");

        Assert.assertEquals("APPROVED", service.request.getStatus());
        Assert.assertEquals(Integer.valueOf(4),
                service.resource.getEffectiveVersionNo());
        Assert.assertEquals(Long.valueOf(44L),
                service.resource.getEffectiveVersionId());
        Assert.assertEquals(1, service.versions.size());
        Assert.assertEquals(Integer.valueOf(4),
                service.versions.get(0).getVersionNo());
        Assert.assertEquals(Long.valueOf(3L),
                service.originalEffectiveVersionId);
    }

    @Test
    public void conflictRestoresLatestEffectiveProjectionNotStaleBase() {
        TestService service = new TestService();
        service.resource.setId(5L);
        service.resource.setResourceType("RULE");
        service.resource.setResourceId(7L);
        service.resource.setEffectiveVersionId(4L);
        service.resource.setEffectiveVersionNo(4);
        service.request.setId(12L);
        service.request.setResourceType("RULE");
        service.request.setResourceId(7L);
        service.request.setAction("UPDATE");
        service.request.setStatus("PENDING");
        service.request.setBaseVersionId(3L);
        service.request.setSubmittedSnapshotJson("{\"name\":\"draft\"}");
        service.request.setActiveResourceKey("RULE:7");
        service.latestVersion.setId(4L);
        service.latestVersion.setResourceType("RULE");
        service.latestVersion.setResourceId(7L);
        service.latestVersion.setSnapshotJson("{\"name\":\"effective-v4\"}");
        service.latestVersion.setEffectiveStatus("ACTIVE");

        GovernanceApprovalRequest conflict = service.approve(
                12L, new GovernanceReviewRequest(), "reviewer");

        Assert.assertEquals("CONFLICT", conflict.getStatus());
        Assert.assertNotNull(service.terminatedEffectiveSnapshot);
        Assert.assertEquals("{\"name\":\"effective-v4\"}",
                service.terminatedEffectiveSnapshot.snapshotJson());
    }

    @Test
    public void restoreCopiesOldContentIntoNewDraft() {
        TestService service = new TestService();
        service.resource.setId(5L);
        service.resource.setResourceType("RULE");
        service.resource.setResourceId(7L);
        service.resource.setEffectiveVersionId(3L);
        service.resource.setEffectiveVersionNo(3);
        service.oldVersion.setId(1L);
        service.oldVersion.setResourceType("RULE");
        service.oldVersion.setResourceId(7L);
        service.oldVersion.setVersionNo(1);
        service.oldVersion.setSnapshotJson("{\"name\":\"v1\"}");

        GovernanceApprovalRequest restore = service.createRestoreDraft(
                "RULE", 7L, 1L, "same-user");

        Assert.assertEquals("RESTORE", restore.getAction());
        Assert.assertEquals(Long.valueOf(1L), restore.getSourceVersionId());
        Assert.assertEquals("{\"name\":\"v1\"}",
                restore.getDraftSnapshotJson());
        Assert.assertEquals("EDITING", restore.getStatus());
        Assert.assertEquals(Long.valueOf(3L), restore.getBaseVersionId());
    }

    @Test
    public void updateUsesStatusFromFrozenSnapshot() {
        TestService service = new TestService();
        service.resource.setEffectiveStatus("ACTIVE");
        service.request.setResourceType("FUNCTION");
        service.request.setResourceId(7L);
        service.request.setAction("UPDATE");
        service.request.setStatus("PENDING");
        service.request.setSubmittedSnapshotJson(
                "{\"funcCode\":\"f\",\"status\":0}");

        Assert.assertEquals("DISABLED",
                service.effectiveStatus(service.request));
    }

    private static GovernanceDraftRequest createRuleDraft(
            String ruleCode, String ruleName) {
        GovernanceDraftRequest draft = new GovernanceDraftRequest();
        draft.setResourceType("RULE");
        draft.setProjectId(9L);
        draft.setAction("CREATE");
        draft.setSnapshotJson("{\"ruleCode\":\"" + ruleCode
                + "\",\"ruleName\":\"" + ruleName
                + "\",\"modelType\":\"SCRIPT\"}");
        return draft;
    }

    private static GovernanceDraftRequest createBindingDraft(
            Long definitionId, Long projectId) {
        GovernanceDraftRequest draft = new GovernanceDraftRequest();
        draft.setResourceType(
                GovernanceResourceTypes.RULE_PROJECT_BINDING);
        draft.setProjectId(projectId);
        draft.setAction("CREATE");
        draft.setSnapshotJson("{\"definitionId\":" + definitionId
                + ",\"projectId\":" + projectId + "}");
        return draft;
    }

    private static GovernanceDraftRequest createListLibraryDraft(
            String listCode, String listName, Long projectId) {
        GovernanceDraftRequest draft = new GovernanceDraftRequest();
        draft.setResourceType("LIST_LIBRARY");
        draft.setProjectId(projectId);
        draft.setAction("CREATE");
        draft.setSnapshotJson("{\"scope\":\"PROJECT\","
                + "\"projectId\":" + projectId + ","
                + "\"listCode\":\"" + listCode + "\","
                + "\"listName\":\"" + listName + "\","
                + "\"listType\":\"BLACK\"}");
        return draft;
    }

    private static GovernanceDraftRequest createFieldValidationDraft(
            String code, String name, Long projectId) {
        GovernanceDraftRequest draft = new GovernanceDraftRequest();
        draft.setResourceType("FIELD_VALIDATION");
        draft.setProjectId(projectId);
        draft.setAction("CREATE");
        draft.setSnapshotJson("{\"projectId\":" + projectId
                + ",\"scope\":\"PROJECT\","
                + "\"validationCode\":\"" + code + "\","
                + "\"validationName\":\"" + name + "\","
                + "\"validationType\":\"REQUIRED\","
                + "\"errorMessage\":\"必填\"}");
        return draft;
    }

    private static GovernanceDraftRequest createBillingConfigDraft(
            String code, String name, Long projectId) {
        GovernanceDraftRequest draft = new GovernanceDraftRequest();
        draft.setResourceType("BILLING_CONFIG");
        draft.setProjectId(projectId);
        draft.setAction("CREATE");
        draft.setSnapshotJson("{\"projectId\":" + projectId
                + ",\"scope\":\"PROJECT\","
                + "\"billingCode\":\"" + code + "\","
                + "\"billingName\":\"" + name + "\","
                + "\"billingTarget\":\"ENGINE\","
                + "\"chargeType\":\"COUNT\","
                + "\"unitPrice\":0,\"currency\":\"CNY\"}");
        return draft;
    }

    private static GovernanceApprovalService.GovernanceStateException
    assertOwnedByAnother(Runnable operation) {
        try {
            operation.run();
            Assert.fail("another user must not mutate an owned draft");
        } catch (GovernanceApprovalService.GovernanceStateException expected) {
            Assert.assertEquals("GOVERNANCE_DRAFT_OWNED_BY_ANOTHER",
                    expected.getCode());
            return expected;
        }
        throw new AssertionError("unreachable");
    }

    private static class InsertService extends GovernanceApprovalService {
        private GovernanceApprovalRequest insert(
                GovernanceApprovalRequest request) {
            return super.insertRequest(request);
        }
    }

    private static class ActionAwareService
            extends GovernanceApprovalService {
        private String validationAction;

        private ActionAwareService() {
            ReflectionTestUtils.setField(this, "dependencyService",
                    new GovernanceDependencyService());
        }

        private GovernancePreflightReport evaluateRequest(
                GovernanceApprovalRequest request,
                ResourceSnapshot snapshot) {
            return super.evaluate(request, snapshot);
        }

        @Override
        protected GovernedResourceAdapter requireAdapter(
                String resourceType) {
            return new GovernedResourceAdapter() {
                @Override
                public String resourceType() {
                    return resourceType;
                }

                @Override
                public ResourceSnapshot loadEffective(Long resourceId) {
                    return null;
                }

                @Override
                public ResourceSnapshot normalizeDraft(
                        ResourceSnapshot draft) {
                    return draft;
                }

                @Override
                public List<GovernanceIssue> validate(
                        ResourceSnapshot draft) {
                    return List.of();
                }

                @Override
                public List<GovernanceIssue> validate(
                        ResourceSnapshot draft, String action) {
                    validationAction = action;
                    return List.of(GovernanceIssue.error(
                            "ACTION_BLOCKED", "当前动作不允许",
                            resourceType, 17L, "$"));
                }

                @Override
                public List<ResourceDependencyRef> collectDependencies(
                        ResourceSnapshot draft) {
                    return List.of();
                }

                @Override
                public ResourceDiff diff(ResourceSnapshot left,
                                         ResourceSnapshot right) {
                    return JsonResourceDiff.compare(left, right);
                }

                @Override
                public AppliedResource apply(
                        ApprovalApplyContext context) {
                    return null;
                }
            };
        }
    }

    private static class TestService extends GovernanceApprovalService {
        private final GovernanceApprovalRequest request =
                new GovernanceApprovalRequest();
        private final GovernedResource resource = new GovernedResource();
        private final List<GovernanceApprovalEvent> events = new ArrayList<>();
        private final List<GovernedResourceVersion> versions =
                new ArrayList<>();
        private final GovernedResourceVersion oldVersion =
                new GovernedResourceVersion();
        private final GovernedResourceVersion latestVersion =
                new GovernedResourceVersion();
        private final GovernanceApprovalRequest staleActiveRequest =
                new GovernanceApprovalRequest();
        private Long originalEffectiveVersionId;
        private ResourceSnapshot effectiveSnapshot =
                ResourceSnapshot.ofJson("{\"name\":\"v3\"}");
        private String terminatedStatus;
        private Long terminatedResourceId;
        private ResourceSnapshot terminatedEffectiveSnapshot;
        private ResourceSnapshot terminatedCreateSnapshot;
        private int insertRequestCalls;
        private boolean applyCollision;

        private String effectiveStatus(
                GovernanceApprovalRequest value) {
            return requestedEffectiveStatus(value);
        }

        @Override
        protected GovernanceApprovalRequest requireRequest(Long requestId) {
            if (!requestId.equals(request.getId())) {
                throw new IllegalArgumentException("not found");
            }
            return request;
        }

        @Override
        protected void persistRequest(GovernanceApprovalRequest value) {
        }

        @Override
        protected void appendEvent(GovernanceApprovalRequest value,
                                   String action,
                                   String fromStatus,
                                   String actor,
                                   String comment,
                                   String detailsJson) {
            GovernanceApprovalEvent event = new GovernanceApprovalEvent();
            event.setRequestId(value.getId());
            event.setAction(action);
            event.setFromStatus(fromStatus);
            event.setToStatus(value.getStatus());
            event.setActor(actor);
            event.setComment(comment);
            events.add(event);
        }

        @Override
        protected ResourceSnapshot normalize(
                GovernanceApprovalRequest request, String snapshotJson) {
            return ResourceSnapshot.ofJson(snapshotJson);
        }

        @Override
        protected GovernancePreflightReport evaluate(
                GovernanceApprovalRequest request,
                ResourceSnapshot snapshot) {
            return new GovernancePreflightReport(true, List.of(),
                    List.of(), List.of(), "dep-digest");
        }

        @Override
        protected List<GovernancePreflightReport.ResolvedDependency>
        loadDependencies(Long requestId) {
            return List.of();
        }

        @Override
        protected GovernedResource findResource(String resourceType,
                                                Long resourceId) {
            if (originalEffectiveVersionId == null) {
                originalEffectiveVersionId =
                        resource.getEffectiveVersionId();
            }
            if (Long.valueOf(0L).equals(resourceId)
                    && resource.getId() == null) {
                return null;
            }
            return resource;
        }

        @Override
        protected GovernanceApprovalRequest findActiveRequest(
                String activeResourceKey) {
            if (request.getId() != null
                    && activeResourceKey != null
                    && activeResourceKey.equals(
                    request.getActiveResourceKey())) {
                return request;
            }
            return activeResourceKey != null
                    && activeResourceKey.equals(
                    staleActiveRequest.getActiveResourceKey())
                    ? staleActiveRequest : null;
        }

        @Override
        protected GovernedResourceVersion findVersion(Long versionId) {
            if (latestVersion.getId() != null
                    && latestVersion.getId().equals(versionId)) {
                return latestVersion;
            }
            return oldVersion.getId() != null
                    && oldVersion.getId().equals(versionId)
                    ? oldVersion : null;
        }

        @Override
        protected GovernedResourceVersion persistVersion(
                GovernedResourceVersion version) {
            version.setId(44L);
            versions.add(version);
            return version;
        }

        @Override
        protected void persistResource(GovernedResource value) {
        }

        @Override
        protected void persistDependencies(
                GovernanceApprovalRequest request,
                GovernancePreflightReport report,
                Long versionId) {
        }

        @Override
        protected GovernancePreflightReport revalidateDependencies(
                List<GovernancePreflightReport.ResolvedDependency>
                        submitted) {
            return new GovernancePreflightReport(true, List.of(),
                    List.of(), submitted, "dep-digest");
        }

        @Override
        protected void bindDependencyVersion(Long requestId,
                                             Long versionId) {
        }

        @Override
        protected GovernedResourceAdapter requireAdapter(String resourceType) {
            return new GovernedResourceAdapter() {
                @Override
                public String resourceType() {
                    return "RULE";
                }

                @Override
                public ResourceSnapshot loadEffective(Long resourceId) {
                    return effectiveSnapshot;
                }

                @Override
                public ResourceSnapshot normalizeDraft(
                        ResourceSnapshot draft) {
                    return draft;
                }

                @Override
                public List<GovernanceIssue> validate(
                        ResourceSnapshot draft) {
                    return List.of();
                }

                @Override
                public List<ResourceDependencyRef> collectDependencies(
                        ResourceSnapshot draft) {
                    return List.of();
                }

                @Override
                public ResourceDiff diff(ResourceSnapshot left,
                                         ResourceSnapshot right) {
                    return new ResourceDiff("", List.of(), null);
                }

                @Override
                public AppliedResource apply(ApprovalApplyContext context) {
                    if (applyCollision) {
                        throw new DuplicateKeyException(
                                "duplicate business identity");
                    }
                    return new AppliedResource(7L,
                            context.nextVersionNo(), "ACTIVE", null);
                }

                @Override
                public void onApprovalTerminated(
                        Long resourceId,
                        ResourceSnapshot effectiveSnapshot,
                        String actor,
                        String comment,
                        String terminalStatus) {
                    terminatedResourceId = resourceId;
                    terminatedEffectiveSnapshot = effectiveSnapshot;
                    terminatedStatus = terminalStatus;
                }

                @Override
                public void onCreateApprovalTerminated(
                        ResourceSnapshot requestSnapshot,
                        String actor,
                        String comment,
                        String terminalStatus) {
                    terminatedCreateSnapshot = requestSnapshot;
                    terminatedStatus = terminalStatus;
                }
            };
        }

        @Override
        protected GovernanceApprovalRequest insertRequest(
                GovernanceApprovalRequest value) {
            insertRequestCalls++;
            if (value.getId() == null) {
                value.setId(98L + insertRequestCalls);
            }
            request.setId(value.getId());
            request.setRequestNo(value.getRequestNo());
            request.setResourceType(value.getResourceType());
            request.setResourceId(value.getResourceId());
            request.setProjectId(value.getProjectId());
            request.setAction(value.getAction());
            request.setStatus(value.getStatus());
            request.setActiveResourceKey(value.getActiveResourceKey());
            request.setBaseVersionId(value.getBaseVersionId());
            request.setBaseVersionNo(value.getBaseVersionNo());
            request.setSourceVersionId(value.getSourceVersionId());
            request.setDraftSnapshotJson(value.getDraftSnapshotJson());
            request.setSnapshotDigest(value.getSnapshotDigest());
            request.setSecretPayloadCiphertext(
                    value.getSecretPayloadCiphertext());
            request.setSecretDigest(value.getSecretDigest());
            request.setApplicant(value.getApplicant());
            return request;
        }
    }
}
