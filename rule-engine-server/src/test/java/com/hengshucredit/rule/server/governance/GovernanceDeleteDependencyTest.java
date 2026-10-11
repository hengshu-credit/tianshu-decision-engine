package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.dto.GovernanceReviewRequest;
import com.hengshucredit.rule.model.dto.GovernanceSubmitRequest;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.Assert.*;

public class GovernanceDeleteDependencyTest {
    @Test
    public void deletionOfBrokenResourceDoesNotRequireItsUpstreamToBeActive() {
        Fixture service = new Fixture();
        service.upstream.setEffectiveStatus("DELETED");

        service.submit(12L, new GovernanceSubmitRequest(), "owner");
        assertEquals("PENDING", service.request.getStatus());
        assertTrue(service.dependencies.isEmpty());
        service.approve(12L, new GovernanceReviewRequest(), "reviewer");

        assertEquals("APPROVED", service.request.getStatus());
        assertEquals("DELETED", service.resource.getEffectiveStatus());
        assertEquals(1, service.applied);
    }

    @Test
    public void pendingLegacyDeleteDoesNotRevalidateOrCompareObsoleteOutboundDependencies() {
        Fixture service = new Fixture();
        GovernancePreflightReport historical = service.dependenciesService.preflight("RULE", 7L, service.refs, List.of());
        service.dependencies = historical.dependencies();
        service.request.setDependencyDigest(historical.dependencyDigest());
        service.request.setSubmittedSnapshotJson(service.request.getDraftSnapshotJson());
        service.request.setStatus("PENDING");
        service.upstream.setEffectiveStatus("DISABLED");

        service.approve(12L, new GovernanceReviewRequest(), "reviewer");

        assertEquals("APPROVED", service.request.getStatus());
        assertEquals(1, service.applied);
    }

    @Test
    public void otherActionsStillRejectInactiveOutboundReferences() {
        for (String action : List.of("UPDATE", "DISABLE", "ENABLE", "RESTORE")) {
            Fixture service = new Fixture();
            service.request.setAction(action);
            service.upstream.setEffectiveStatus("DISABLED");
            var failure = assertThrows(GovernanceApprovalService.GovernanceStateException.class,
                    () -> service.submit(12L, new GovernanceSubmitRequest(), "owner"));
            assertEquals(action, "GOVERNANCE_PREFLIGHT_FAILED", failure.getCode());
            assertEquals(0, service.applied);
        }
    }

    @Test
    public void deletionStillHonorsAdapterRestrictionsAndRealConsumers() {
        for (boolean adapterRestriction : new boolean[]{true, false}) {
            Fixture service = new Fixture();
            service.adapterBlocked = adapterRestriction;
            service.consumerPresent = !adapterRestriction;
            var failure = assertThrows(GovernanceApprovalService.GovernanceStateException.class,
                    () -> service.submit(12L, new GovernanceSubmitRequest(), "owner"));
            assertEquals("GOVERNANCE_PREFLIGHT_FAILED", failure.getCode());
            assertTrue(failure.getMessage().contains(adapterRestriction ? "禁止删除" : "真实消费者"));
            assertEquals(0, service.applied);
        }
    }

    @Test
    public void approvalRechecksConsumersAfterDeletionWasSubmitted() {
        Fixture service = new Fixture();
        service.submit(12L, new GovernanceSubmitRequest(), "owner");
        service.consumerPresent = true;
        service.approve(12L, new GovernanceReviewRequest(), "reviewer");
        assertEquals("CONFLICT", service.request.getStatus());
        assertTrue(service.request.getReviewComment().contains("真实消费者"));
        assertEquals(0, service.applied);
    }

    @Test
    public void deletionStillRejectsBaseVersionConflictAndMissingReviewer() {
        Fixture changed = new Fixture();
        changed.submit(12L, new GovernanceSubmitRequest(), "owner");
        changed.resource.setEffectiveVersionId(4L);
        changed.approve(12L, new GovernanceReviewRequest(), "reviewer");
        assertEquals("CONFLICT", changed.request.getStatus());
        assertTrue(changed.request.getReviewComment().contains("生效版本"));
        assertEquals(0, changed.applied);

        Fixture denied = new Fixture();
        denied.submit(12L, new GovernanceSubmitRequest(), "owner");
        var failure = assertThrows(GovernanceApprovalService.GovernanceStateException.class,
                () -> denied.approve(12L, new GovernanceReviewRequest(), ""));
        assertEquals("APPROVAL_REVIEW_FORBIDDEN", failure.getCode());
        assertEquals(0, denied.applied);
    }

    private static final class Fixture extends GovernanceApprovalService {
        final GovernanceApprovalRequest request = new GovernanceApprovalRequest();
        final GovernedResource resource = new GovernedResource();
        final GovernedResource upstream = new GovernedResource();
        final GovernedResourceVersion upstreamVersion = new GovernedResourceVersion();
        final GovernedResourceVersion currentVersion = new GovernedResourceVersion();
        final List<ResourceDependencyRef> refs = List.of(new ResourceDependencyRef(
                "VARIABLE", 319L, "VARIABLE", "$.input.refId", "REFERENCES", true));
        List<GovernancePreflightReport.ResolvedDependency> dependencies = List.of();
        boolean adapterBlocked;
        boolean consumerPresent;
        int applied;
        final GovernanceDependencyService dependenciesService = new GovernanceDependencyService() {
            @Override protected GovernedResource findTarget(String type, Long id) { return upstream; }
            @Override protected GovernedResourceVersion findVersion(Long id) { return upstreamVersion; }
        };

        Fixture() {
            resource.setId(70L); resource.setResourceId(7L); resource.setResourceType("RULE");
            resource.setEffectiveVersionId(3L); resource.setEffectiveVersionNo(1); resource.setEffectiveStatus("ACTIVE");
            upstream.setId(3190L); upstream.setResourceId(319L); upstream.setResourceType("VARIABLE");
            upstream.setEffectiveVersionId(15L); upstream.setEffectiveVersionNo(1); upstream.setEffectiveStatus("ACTIVE");
            upstreamVersion.setId(15L); upstreamVersion.setSnapshotDigest("upstream-v1");
            currentVersion.setId(3L); currentVersion.setSnapshotJson("{\"id\":7}"); currentVersion.setEffectiveStatus("ACTIVE");
            request.setId(12L); request.setResourceType("RULE"); request.setResourceId(7L); request.setAction("DELETE");
            request.setStatus("EDITING"); request.setApplicant("owner"); request.setBaseVersionId(3L);
            request.setDraftSnapshotJson("{\"id\":7,\"input\":{\"refId\":319,\"refType\":\"VARIABLE\"}}");
            ReflectionTestUtils.setField(this, "dependencyService", dependenciesService);
            ReflectionTestUtils.setField(this, "impactService", new GovernanceImpactService(null) {
                @Override public List<GovernanceIssue> analyze(String type, Long id, String action, ResourceSnapshot snapshot) {
                    return consumerPresent ? List.of(GovernanceIssue.error("DOWNSTREAM_DEPENDENCY_ACTIVE",
                            "仍有真实消费者", type, id, "$")) : List.of();
                }
            });
        }

        @Override protected GovernanceApprovalRequest requireRequest(Long id) { assertEquals(Long.valueOf(12), id); return request; }
        @Override protected GovernedResource findResource(String type, Long id) { return resource; }
        @Override protected GovernedResourceVersion findVersion(Long id) { return currentVersion; }
        @Override protected ResourceSnapshot normalize(GovernanceApprovalRequest value, String json) { return ResourceSnapshot.ofJson(json); }
        @Override protected void persistRequest(GovernanceApprovalRequest value) { }
        @Override protected void persistResource(GovernedResource value) { }
        @Override protected GovernedResourceVersion persistVersion(GovernedResourceVersion value) { value.setId(4L); return value; }
        @Override protected void appendEvent(GovernanceApprovalRequest value, String action, String from, String actor, String comment, String details) { }
        @Override protected void persistDependencies(GovernanceApprovalRequest value, GovernancePreflightReport report, Long versionId) { dependencies = report.dependencies(); }
        @Override protected List<GovernancePreflightReport.ResolvedDependency> loadDependencies(Long id) { return dependencies; }
        @Override protected GovernancePreflightReport revalidateDependencies(List<GovernancePreflightReport.ResolvedDependency> values) { return dependenciesService.revalidate(values); }
        @Override protected void bindDependencyVersion(Long id, Long versionId) { }
        @Override protected GovernedResourceAdapter requireAdapter(String type) {
            return new GovernedResourceAdapter() {
                @Override public String resourceType() { return "RULE"; }
                @Override public ResourceSnapshot loadEffective(Long id) { return ResourceSnapshot.ofJson("{\"id\":7}"); }
                @Override public ResourceSnapshot normalizeDraft(ResourceSnapshot snapshot) { return snapshot; }
                @Override public List<GovernanceIssue> validate(ResourceSnapshot snapshot) { return List.of(); }
                @Override public List<GovernanceIssue> validate(ResourceSnapshot snapshot, String action) {
                    return adapterBlocked ? List.of(GovernanceIssue.error("DELETE_PROTECTED", "适配器禁止删除", type, 7L, "$")) : List.of();
                }
                @Override public List<ResourceDependencyRef> collectDependencies(ResourceSnapshot snapshot) { return refs; }
                @Override public ResourceDiff diff(ResourceSnapshot left, ResourceSnapshot right) { return JsonResourceDiff.compare(left, right); }
                @Override public AppliedResource apply(ApprovalApplyContext context) {
                    applied++; return new AppliedResource(7L, context.nextVersionNo(), "DELETED", null);
                }
            };
        }
    }
}
