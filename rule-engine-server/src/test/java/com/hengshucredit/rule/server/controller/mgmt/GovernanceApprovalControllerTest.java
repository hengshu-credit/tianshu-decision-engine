package com.hengshucredit.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.dto.GovernanceApprovalQuery;
import com.hengshucredit.rule.server.governance.GovernanceApprovalService;
import com.hengshucredit.rule.server.governance.GovernanceApprovalSummary;
import com.hengshucredit.rule.server.governance.GovernanceRequestView;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import com.hengshucredit.rule.server.security.RequirePermission;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class GovernanceApprovalControllerTest {
    @Test
    public void workspaceCanInspectRejectedAndApprovedRequestsWithoutChangingTheirStatusFilter() {
        TrackingApprovalService service = new TrackingApprovalService();
        GovernanceApprovalController controller = new GovernanceApprovalController();
        ReflectionTestUtils.setField(controller, "approvalService", service);
        ReflectionTestUtils.setField(controller, "operatorResolver", new ConsoleOperatorResolver() {
            @Override public String resolve() { return "owner"; }
        });
        for (String status : java.util.List.of("REJECTED", "APPROVED", "PENDING", "EDITING", "CONFLICT", "CANCELLED")) {
            GovernanceApprovalQuery query = new GovernanceApprovalQuery();
            query.setStatus(status); query.setTaskScope("ALL"); query.setResourceType("BILLING_CONFIG");
            controller.workspaces(query);
            Assert.assertEquals(status, service.query.getStatus());
            Assert.assertEquals("MINE", service.query.getTaskScope());
            Assert.assertEquals("BILLING_CONFIG", service.query.getResourceType());
        }
    }

    @Test
    public void listAndSummaryUseResolvedOperatorForTaskViews() {
        TrackingApprovalService service = new TrackingApprovalService();
        ConsoleOperatorResolver resolver = new ConsoleOperatorResolver() {
            @Override
            public String resolve() {
                return "reviewer-a";
            }
        };
        GovernanceApprovalController controller =
                new GovernanceApprovalController();
        ReflectionTestUtils.setField(controller,
                "approvalService", service);
        ReflectionTestUtils.setField(controller,
                "operatorResolver", resolver);
        GovernanceApprovalQuery query = new GovernanceApprovalQuery();
        query.setTaskScope("MINE");
        controller.requests(query);
        controller.summary(9L);

        Assert.assertSame(query, service.query);
        Assert.assertEquals("reviewer-a", service.pageActor);
        Assert.assertEquals(Long.valueOf(9L), service.summaryProjectId);
        Assert.assertEquals("reviewer-a", service.summaryActor);
    }

    @Test
    public void reviewEndpointsRequireApprovePermission() throws Exception {
        RequirePermission approve = GovernanceApprovalController.class
                .getMethod("approve", Long.class,
                        com.hengshucredit.rule.model.dto
                                .GovernanceReviewRequest.class)
                .getAnnotation(RequirePermission.class);
        RequirePermission reject = GovernanceApprovalController.class
                .getMethod("reject", Long.class,
                        com.hengshucredit.rule.model.dto
                                .GovernanceReviewRequest.class)
                .getAnnotation(RequirePermission.class);

        Assert.assertEquals("approval:approve", approve.value());
        Assert.assertEquals("approval:approve", reject.value());
    }

    @Test
    public void listAndDetailRequireViewPermission() throws Exception {
        RequirePermission list = GovernanceApprovalController.class
                .getMethod("requests",
                        com.hengshucredit.rule.model.dto
                                .GovernanceApprovalQuery.class)
                .getAnnotation(RequirePermission.class);
        RequirePermission detail = GovernanceApprovalController.class
                .getMethod("detail", Long.class)
                .getAnnotation(RequirePermission.class);
        RequirePermission summary = GovernanceApprovalController.class
                .getMethod("summary", Long.class)
                .getAnnotation(RequirePermission.class);

        Assert.assertEquals("approval:view", list.value());
        Assert.assertEquals("approval:view", detail.value());
        Assert.assertEquals("approval:view", summary.value());
    }

    private static class TrackingApprovalService
            extends GovernanceApprovalService {
        private GovernanceApprovalQuery query;
        private String pageActor;
        private Long summaryProjectId;
        private String summaryActor;

        @Override
        public Page<GovernanceRequestView> page(
                GovernanceApprovalQuery value, String actor) {
            query = value;
            pageActor = actor;
            return new Page<>();
        }

        @Override
        public GovernanceApprovalSummary summary(
                Long projectId, String actor) {
            summaryProjectId = projectId;
            summaryActor = actor;
            return new GovernanceApprovalSummary(0, 0, 0, 0);
        }
    }
}
