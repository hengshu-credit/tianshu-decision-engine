package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.GovernedResourceMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.*;

public class GovernanceDraftDeletionTest {
    @Test
    public void directDeletionDoesNotCreateAnApprovalOrRequireCompleteDraftConfiguration() {
        Fixture f = new Fixture();
        GovernanceDeletionResult result = f.deleteRule(7L, "owner");
        assertTrue(result.deleted());
        assertNull(result.approvalRequestId());
        assertTrue(f.discarded);
        assertEquals(0, f.created);
    }

    @Test
    public void editingOwnedRequestIsCancelledBeforeDiscard() {
        for (String status : List.of("EDITING")) {
            Fixture f = new Fixture();
            f.active = new GovernanceApprovalRequest();
            f.active.setStatus(status); f.active.setApplicant("owner"); f.active.setActiveResourceKey("RULE:7");
            f.deleteRule(7L, "owner");
            assertEquals("CANCELLED", f.active.getStatus());
            assertNull(f.active.getActiveResourceKey());
            assertEquals(1, f.cancelEvents);
            assertTrue(f.discarded);
        }
    }

    @Test
    public void pendingNewRuleMustBeRejectedBeforeDeletion() {
        Fixture f = new Fixture();
        f.active = new GovernanceApprovalRequest();
        f.active.setStatus("PENDING"); f.active.setApplicant("owner"); f.active.setActiveResourceKey("RULE:7");
        var error = assertThrows(GovernanceApprovalService.GovernanceStateException.class, () -> f.deleteRule(7L, "owner"));
        assertEquals("NEW_CONTENT_REJECTION_REQUIRED", error.getCode());
        assertFalse(f.discarded); assertEquals(0, f.cancelEvents); assertEquals("PENDING", f.active.getStatus());
        f.active.setStatus("REJECTED"); f.active.setActiveResourceKey(null);
        assertTrue(f.deleteRule(7L, "owner").deleted());
        assertTrue(f.discarded); assertEquals("REJECTED", f.active.getStatus());
    }

    @Test
    public void deletionCannotSilentlyCancelSomeoneElsesPendingReview() {
        Fixture f = new Fixture();
        f.active = new GovernanceApprovalRequest();
        f.active.setStatus("PENDING"); f.active.setApplicant("other-user");
        var error = assertThrows(GovernanceApprovalService.GovernanceStateException.class, () -> f.deleteRule(7L, "owner"));
        assertEquals("GOVERNANCE_DRAFT_OWNED_BY_ANOTHER", error.getCode());
        assertFalse(f.discarded);
        assertEquals("PENDING", f.active.getStatus());
    }

    @Test
    public void existingConsumersStillPreventDiscard() {
        Fixture f = new Fixture(); f.referenced = true;
        assertThrows(GovernanceApprovalService.GovernanceStateException.class, () -> f.deleteRule(7L, "owner"));
        assertFalse(f.discarded);
    }

    @Test
    public void previouslyApprovedRuleCreatesDeletionApprovalWithoutDeletingAnything() {
        Fixture f = new Fixture(); f.unapproved = false;
        GovernanceDeletionResult result = f.deleteRule(7L, "owner");
        assertFalse(result.deleted());
        assertEquals(Long.valueOf(18), result.approvalRequestId());
        assertEquals(1, f.created);
        assertFalse(f.discarded);
    }

    private static class Fixture extends GovernanceApprovalService {
        boolean unapproved = true, discarded, referenced;
        int created, cancelEvents;
        GovernanceApprovalRequest active;

        Fixture() {
            ReflectionTestUtils.setField(this, "ruleDraftDeletionService", new RuleDraftDeletionService() {
                @Override public RuleDefinition lock(Long id) { RuleDefinition d = new RuleDefinition(); d.setId(id); return d; }
                @Override public boolean isUnapproved(RuleDefinition d) { return unapproved; }
                @Override public void discard(RuleDefinition d, String actor) {
                    if (active != null) assertTrue(List.of("CANCELLED", "REJECTED").contains(active.getStatus()));
                    discarded = true;
                }
            });
            ReflectionTestUtils.setField(this, "impactService", new GovernanceImpactService(null) {
                @Override public List<GovernanceIssue> analyze(String type, Long id, String action, ResourceSnapshot snapshot) {
                    return referenced ? List.of(GovernanceIssue.error("REFERENCED", "仍有资源引用", type, id, "$")) : List.of();
                }
            });
            ReflectionTestUtils.setField(this, "resourceMapper", Proxy.newProxyInstance(GovernedResourceMapper.class.getClassLoader(),
                    new Class<?>[]{GovernedResourceMapper.class}, (p, method, args) -> null));
        }
        @Override public GovernanceApprovalRequest createDraft(GovernanceDraftRequest draft, String actor) {
            assertEquals("DELETE", draft.getAction()); assertEquals(Long.valueOf(7), draft.getResourceId());
            created++; GovernanceApprovalRequest request = new GovernanceApprovalRequest(); request.setId(18L); return request;
        }
        @Override protected GovernanceApprovalRequest findActiveRequest(String key) { return active; }
        @Override protected void persistRequest(GovernanceApprovalRequest request) { }
        @Override protected void appendEvent(GovernanceApprovalRequest request, String action, String from, String actor, String comment, String details) {
            assertEquals("CANCEL", action); cancelEvents++;
        }
        @Override protected GovernedResourceAdapter requireAdapter(String type) {
            return (GovernedResourceAdapter) Proxy.newProxyInstance(GovernedResourceAdapter.class.getClassLoader(),
                    new Class<?>[]{GovernedResourceAdapter.class}, (p, method, args) -> {
                        if ("loadEffective".equals(method.getName())) return ResourceSnapshot.ofJson("{\"id\":7,\"content\":{}}");
                        throw new AssertionError("Discard must not invoke compilation, validation or publication: " + method.getName());
                    });
        }
    }
}
