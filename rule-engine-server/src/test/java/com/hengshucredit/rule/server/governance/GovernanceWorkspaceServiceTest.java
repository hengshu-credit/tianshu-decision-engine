package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.dto.GovernanceReviewRequest;
import com.hengshucredit.rule.model.dto.GovernanceSubmitRequest;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.Assert.*;

public class GovernanceWorkspaceServiceTest {
    @Test
    public void saveAndReeditRetainInactiveIdAndOnlyApprovalCreatesEffectiveVersion() {
        Fixture f = new Fixture();
        var created = f.createDraft(draft(null, "CREATE", "DraftCode"), "owner");
        Long id = created.getResourceId();
        assertNotEquals(Long.valueOf(0), id);
        assertEquals(Integer.valueOf(0), f.variables.get(id).getStatus());
        assertEquals("DRAFT", f.resources.get(id).getEffectiveStatus());
        assertTrue(f.versions.isEmpty());
        var edited = f.createDraft(draft(id, "UPDATE", "My_ExactCode"), "owner");
        assertEquals(created.getId(), edited.getId());
        assertEquals("CREATE", edited.getAction());
        assertEquals("My_ExactCode", f.variables.get(id).getVarCode());
        f.submit(edited.getId(), new GovernanceSubmitRequest(), "owner");
        assertEquals(Integer.valueOf(0), f.variables.get(id).getStatus());
        assertTrue(f.versions.isEmpty());
        f.approve(edited.getId(), new GovernanceReviewRequest(), "reviewer");
        assertEquals(1, f.variables.size());
        assertEquals(Integer.valueOf(1), f.variables.get(id).getStatus());
        assertEquals(1, f.versions.size());
        assertEquals("ACTIVE", f.resources.get(id).getEffectiveStatus());
        assertEquals(id, edited.getResourceId());
    }

    @Test
    public void editingPublishedContentDoesNotChangeItsEffectiveProjectionBeforeApproval() {
        Fixture f = new Fixture();
        var created = f.createDraft(draft(null, "CREATE", "PublishedCode"), "owner");
        f.submit(created.getId(), new GovernanceSubmitRequest(), "owner");
        f.approve(created.getId(), new GovernanceReviewRequest(), "reviewer");
        Long effectiveVersion = f.resources.get(created.getResourceId()).getEffectiveVersionId();
        var edited = f.createDraft(draft(created.getResourceId(), "UPDATE", "FutureCode"), "owner");
        assertEquals("UPDATE", edited.getAction());
        assertEquals("PublishedCode", f.variables.get(created.getResourceId()).getVarCode());
        assertEquals(effectiveVersion, f.resources.get(created.getResourceId()).getEffectiveVersionId());
        assertTrue(edited.getDraftSnapshotJson().contains("FutureCode"));
        assertEquals(1, f.versions.size());
    }

    @Test
    public void deletingUnpublishedWorkspaceCancelsItsRequestWithoutProducingAnEffectiveVersion() {
        Fixture f = new Fixture();
        var created = f.createDraft(draft(null, "CREATE", "DiscardCode"), "owner");
        var result = f.deleteResource("VARIABLE", created.getResourceId(), "owner");
        assertTrue(result.deleted());
        assertNull(result.approvalRequestId());
        assertEquals("CANCELLED", created.getStatus());
        assertEquals(Integer.valueOf(-1), f.variables.get(created.getResourceId()).getStatus());
        assertEquals("DELETED", f.resources.get(created.getResourceId()).getEffectiveStatus());
        assertTrue(f.versions.isEmpty());
    }

    private static GovernanceDraftRequest draft(Long id, String action, String code) {
        var draft = new GovernanceDraftRequest();
        draft.setResourceType("VARIABLE"); draft.setResourceId(id); draft.setAction(action);
        draft.setSnapshotJson("{\"varCode\":\"" + code + "\",\"varLabel\":\"字段\",\"scope\":\"GLOBAL\",\"varType\":\"NUMBER\",\"varSource\":\"INPUT\",\"status\":1}");
        return draft;
    }

    @Test
    public void pendingNewWorkspaceMustBeRejectedBeforeDeletion() {
        Fixture f = new Fixture();
        var created = f.createDraft(draft(null, "CREATE", "PendingCode"), "owner");
        f.submit(created.getId(), new GovernanceSubmitRequest(), "owner");
        assertEquals("PENDING", created.getStatus());
        var blocked = assertThrows(GovernanceApprovalService.GovernanceStateException.class,
                () -> f.deleteResource("VARIABLE", created.getResourceId(), "owner"));
        assertEquals("NEW_CONTENT_REJECTION_REQUIRED", blocked.getCode());
        assertEquals("PENDING", created.getStatus());
        assertEquals(Integer.valueOf(0), f.variables.get(created.getResourceId()).getStatus());
        f.reject(created.getId(), new GovernanceReviewRequest(), "reviewer");
        assertEquals("REJECTED", created.getStatus());
        assertTrue(f.deleteResource("VARIABLE", created.getResourceId(), "owner").deleted());
        assertThrows(GovernanceApprovalService.GovernanceStateException.class,
                () -> f.approve(created.getId(), new GovernanceReviewRequest(), "reviewer"));
        assertEquals(Integer.valueOf(-1), f.variables.get(created.getResourceId()).getStatus());
        assertTrue(f.versions.isEmpty());
    }

    @Test
    public void approvedWorkspaceDeletionOnlyCreatesApprovalAndPreservesEffectiveContent() {
        Fixture f = new Fixture();
        var created = f.createDraft(draft(null, "CREATE", "PublishedCode"), "owner");
        f.submit(created.getId(), new GovernanceSubmitRequest(), "owner");
        f.approve(created.getId(), new GovernanceReviewRequest(), "reviewer");
        var deleted = f.deleteResource("VARIABLE", created.getResourceId(), "owner");
        assertFalse(deleted.deleted());
        var approval = f.requests.get(deleted.approvalRequestId());
        assertEquals("EDITING", approval.getStatus());
        assertEquals("DELETE", approval.getAction());
        assertEquals(Integer.valueOf(1), f.variables.get(created.getResourceId()).getStatus());
        assertEquals(1, f.versions.size());
        f.submit(approval.getId(), new GovernanceSubmitRequest(), "owner");
        f.approve(approval.getId(), new GovernanceReviewRequest(), "reviewer");
        assertEquals(Integer.valueOf(-1), f.variables.get(created.getResourceId()).getStatus());
        assertEquals(2, f.versions.size());
    }

    @Test
    public void withdrawingCannotBypassRequiredRejectionOfFirstPublication() {
        Fixture f = new Fixture();
        var created = f.createDraft(draft(null, "CREATE", "WithdrawCode"), "owner");
        f.submit(created.getId(), new GovernanceSubmitRequest(), "owner");
        var blocked = assertThrows(GovernanceApprovalService.GovernanceStateException.class,
                () -> f.cancel(created.getId(), new GovernanceReviewRequest(), "owner"));
        assertEquals("NEW_CONTENT_REJECTION_REQUIRED", blocked.getCode());
        assertEquals("PENDING", created.getStatus());
        assertEquals(Integer.valueOf(0), f.variables.get(created.getResourceId()).getStatus());
        assertTrue(f.versions.isEmpty());
        assertEquals(1, f.requests.size());
    }

    @Test
    public void submissionCannotSilentlyApproveContentChangedSinceUserOpenedPublishDialog() {
        Fixture f = new Fixture();
        var created = f.createDraft(draft(null, "CREATE", "FirstCode"), "owner");
        var submit = new GovernanceSubmitRequest();
        submit.setExpectedSnapshotDigest(created.getSnapshotDigest());
        var changed = f.createDraft(draft(created.getResourceId(), "UPDATE", "ChangedCode"), "owner");
        assertThrows(GovernanceApprovalService.GovernanceStateException.class, () -> f.submit(created.getId(), submit, "owner"));
        assertEquals("EDITING", changed.getStatus());
        assertTrue(f.versions.isEmpty());
        submit.setExpectedSnapshotDigest(changed.getSnapshotDigest());
        f.submit(created.getId(), submit, "owner");
        assertEquals("PENDING", changed.getStatus());
    }

    private static class Fixture extends GovernanceApprovalService {
        final Map<Long, RuleVariable> variables = new HashMap<>();
        final Map<Long, GovernedResource> resources = new HashMap<>();
        final Map<Long, GovernanceApprovalRequest> requests = new HashMap<>();
        final List<GovernedResourceVersion> versions = new ArrayList<>();
        final GovernedResourceAdapter adapter;
        Fixture() {
            var keys = new ProjectAuthProperties(); keys.setActiveKeyId("test");
            keys.setMasterKeys(Map.of("test", "test-governance-master-key-32-characters-long"));
            adapter = new SimpleEntityGovernedResourceAdapter<>("VARIABLE", RuleVariable.class,
                    new SimpleEntityGovernedResourceAdapter.EntityStore<>() {
                        public RuleVariable load(Long id) { return variables.get(id); }
                        public void insert(RuleVariable variable) { variable.setId(8L + variables.size()); variables.put(variable.getId(), variable); }
                        public void update(RuleVariable variable) { assertTrue(variables.containsKey(variable.getId())); variables.put(variable.getId(), variable); }
                    }, RuleVariable::getId, RuleVariable::setId, RuleVariable::getStatus, RuleVariable::setStatus,
                    Set.of("varCode", "varLabel"), Set.of(), new GovernanceSecretCodec(new CredentialCipher(keys)));
            ReflectionTestUtils.setField(this, "impactService", new GovernanceImpactService(null) {
                @Override public List<GovernanceIssue> analyze(String type, Long id, String action, ResourceSnapshot snapshot) { return List.of(); }
            });
        }
        @Override protected GovernedResourceAdapter requireAdapter(String type) { return adapter; }
        @Override protected GovernedResource findResource(String type, Long id) { return resources.get(id); }
        @Override protected void insertResource(GovernedResource value) { value.setId(50L + resources.size()); resources.put(value.getResourceId(), value); }
        @Override protected void persistResource(GovernedResource value) { resources.put(value.getResourceId(), value); }
        @Override protected GovernanceApprovalRequest insertRequest(GovernanceApprovalRequest value) { value.setId(100L + requests.size()); requests.put(value.getId(), value); return value; }
        @Override protected GovernanceApprovalRequest requireRequest(Long id) { return Objects.requireNonNull(requests.get(id)); }
        @Override protected GovernanceApprovalRequest findActiveRequest(String key) { return requests.values().stream().filter(r -> key.equals(r.getActiveResourceKey())).findFirst().orElse(null); }
        @Override protected void persistRequest(GovernanceApprovalRequest value) { requests.put(value.getId(), value); }
        @Override protected GovernedResourceVersion persistVersion(GovernedResourceVersion value) { value.setId(200L + versions.size()); versions.add(value); return value; }
        @Override protected GovernancePreflightReport evaluate(GovernanceApprovalRequest request, ResourceSnapshot snapshot) { return report(); }
        @Override protected List<GovernancePreflightReport.ResolvedDependency> loadDependencies(Long id) { return List.of(); }
        @Override protected GovernancePreflightReport revalidateDependencies(List<GovernancePreflightReport.ResolvedDependency> values) { return report(); }
        @Override protected void persistDependencies(GovernanceApprovalRequest request, GovernancePreflightReport report, Long id) { }
        @Override protected void bindDependencyVersion(Long requestId, Long versionId) { }
        @Override protected void appendEvent(GovernanceApprovalRequest request, String action, String from, String actor, String comment, String details) { }
        private GovernancePreflightReport report() { return new GovernancePreflightReport(true, List.of(), List.of(), List.of(), "empty"); }
    }
}
