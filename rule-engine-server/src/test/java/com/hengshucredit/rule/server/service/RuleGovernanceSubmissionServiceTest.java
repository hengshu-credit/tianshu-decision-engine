package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.dto.GovernanceSubmitRequest;
import com.hengshucredit.rule.model.dto.RuleLifecycleActionRequest;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.governance.AppliedResource;
import com.hengshucredit.rule.server.governance.ApprovalApplyContext;
import com.hengshucredit.rule.server.governance.GovernanceApprovalService;
import com.hengshucredit.rule.server.governance.GovernanceIssue;
import com.hengshucredit.rule.server.governance.GovernedResourceAdapter;
import com.hengshucredit.rule.server.governance.GovernedResourceAdapterRegistry;
import com.hengshucredit.rule.server.governance.ResourceDependencyRef;
import com.hengshucredit.rule.server.governance.ResourceDiff;
import com.hengshucredit.rule.server.governance.ResourceSnapshot;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;

public class RuleGovernanceSubmissionServiceTest {

    @Test
    public void submitCreatesAndFreezesUnifiedApprovalInSameWorkflow() {
        RuleRevision revision = new RuleRevision();
        revision.setId(6L);
        revision.setDefinitionId(30L);
        revision.setState("DRAFT");
        FixtureLifecycleService lifecycle =
                new FixtureLifecycleService(revision);
        FixtureApprovalService approval =
                new FixtureApprovalService();
        GovernedResourceAdapterRegistry registry =
                new GovernedResourceAdapterRegistry(
                        List.of(ruleAdapter()));
        RuleGovernanceSubmissionService service =
                new RuleGovernanceSubmissionService(
                        lifecycle, approval, registry,
                        operatorResolver(), successfulMapper());

        RuleLifecycleActionRequest action =
                new RuleLifecycleActionRequest();
        action.setComment("提交规则审批");
        RuleRevision submitted = service.submit(6L, action);

        Assert.assertEquals("REVIEW", submitted.getState());
        Assert.assertEquals(Long.valueOf(41L),
                submitted.getGovernanceRequestId());
        Assert.assertEquals("RULE",
                approval.createdDraft.getResourceType());
        Assert.assertEquals(Long.valueOf(30L),
                approval.createdDraft.getResourceId());
        Assert.assertEquals(Long.valueOf(9L),
                approval.createdDraft.getProjectId());
        Assert.assertEquals("UPDATE",
                approval.createdDraft.getAction());
        Assert.assertEquals("NEW", approval.createdDraft.getPublishMode());
        Assert.assertEquals(Long.valueOf(6), com.alibaba.fastjson.JSON.parseObject(approval.createdDraft.getSnapshotJson()).getLong("_ruleRevisionId"));
        Assert.assertEquals("alice", approval.submitActor);
        Assert.assertEquals("提交规则审批",
                approval.submittedComment);
    }

    private static GovernedResourceAdapter ruleAdapter() {
        return new GovernedResourceAdapter() {
            @Override
            public String resourceType() {
                return "RULE";
            }

            @Override
            public ResourceSnapshot loadEffective(Long resourceId) {
                return ResourceSnapshot.ofJson(
                        "{\"id\":30,\"projectId\":9,"
                                + "\"ruleCode\":\"risk-rule\"}");
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
            public AppliedResource apply(
                    ApprovalApplyContext context) {
                return new AppliedResource(30L,
                        context.nextVersionNo(), "ACTIVE", null);
            }
        };
    }

    private static ConsoleOperatorResolver operatorResolver() {
        return new ConsoleOperatorResolver() {
            @Override
            public String resolve() {
                return "alice";
            }
        };
    }

    private static RuleRevisionMapper successfulMapper() {
        return (RuleRevisionMapper) Proxy.newProxyInstance(
                RuleRevisionMapper.class.getClassLoader(),
                new Class<?>[]{RuleRevisionMapper.class},
                (proxy, method, args) -> {
                    if ("updateById".equals(method.getName())) {
                        return 1;
                    }
                    Class<?> type = method.getReturnType();
                    if (type == int.class || type == Integer.class) {
                        return 0;
                    }
                    if (type == boolean.class
                            || type == Boolean.class) {
                        return false;
                    }
                    return null;
                });
    }

    private static final class FixtureLifecycleService
            extends RuleLifecycleService {
        private final RuleRevision revision;

        private FixtureLifecycleService(RuleRevision revision) {
            this.revision = revision;
        }

        @Override
        public RuleRevision submit(
                Long revisionId,
                RuleLifecycleActionRequest request) {
            revision.setState("REVIEW");
            return revision;
        }
    }

    private static final class FixtureApprovalService
            extends GovernanceApprovalService {
        private GovernanceDraftRequest createdDraft;
        private String submitActor;
        private String submittedComment;

        @Override
        public GovernanceApprovalRequest createDraft(
                GovernanceDraftRequest draft,
                String actor) {
            createdDraft = draft;
            GovernanceApprovalRequest request =
                    new GovernanceApprovalRequest();
            request.setId(41L);
            request.setStatus("EDITING");
            return request;
        }

        @Override
        public GovernanceApprovalRequest submit(
                Long requestId,
                GovernanceSubmitRequest submit,
                String actor) {
            submitActor = actor;
            submittedComment =
                    submit == null ? null : submit.getComment();
            GovernanceApprovalRequest request =
                    new GovernanceApprovalRequest();
            request.setId(requestId);
            request.setStatus("PENDING");
            return request;
        }
    }
}
