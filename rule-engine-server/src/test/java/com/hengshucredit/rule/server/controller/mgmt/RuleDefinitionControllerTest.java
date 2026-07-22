package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.RuleLifecycleActionRequest;
import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleLifecycleService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;

public class RuleDefinitionControllerTest {

    @Test
    public void normalizeModelJsonUnquotesJsonStringRequestBody() {
        RuleDefinitionController controller = new RuleDefinitionController();

        String normalized = ReflectionTestUtils.invokeMethod(
                controller, "normalizeModelJson", "\"{\\\"nodes\\\":[],\\\"edges\\\":[]}\"");

        assertEquals("{\"nodes\":[],\"edges\":[]}", normalized);
    }

    @Test
    public void lifecycleEndpointsValidateDefinitionScopeAndReturnBusinessCodes() {
        RuleDefinitionController controller = new RuleDefinitionController();
        ReflectionTestUtils.setField(controller, "lifecycleService", new RuleLifecycleService() {
            @Override
            public RuleRevision getRevision(Long definitionId, Long revisionId) {
                if (!Long.valueOf(10L).equals(definitionId)) {
                    throw new IllegalArgumentException("规则修订不存在");
                }
                RuleRevision revision = new RuleRevision();
                revision.setId(revisionId);
                revision.setDefinitionId(definitionId);
                revision.setState("DRAFT");
                return revision;
            }

            @Override
            public RuleRevision submit(Long revisionId, RuleLifecycleActionRequest request) {
                RuleRevision revision = new RuleRevision();
                revision.setId(revisionId);
                revision.setDefinitionId(10L);
                revision.setState("REVIEW");
                return revision;
            }

            @Override
            public RuleRevision approve(Long revisionId, RuleLifecycleActionRequest request) {
                throw new IllegalStateException("发布前验证未通过: unresolved model");
            }
        });

        R<RuleRevision> submitted = controller.submitRevision(10L, 3L, null);
        R<RuleRevision> wrongDefinition = controller.submitRevision(11L, 3L, null);
        R<RuleRevision> invalidApproval = controller.approveRevision(10L, 3L, null);

        assertEquals(200, submitted.getCode());
        assertEquals("REVIEW", submitted.getData().getState());
        assertEquals(400, wrongDefinition.getCode());
        assertEquals(422, invalidApproval.getCode());
    }

    @Test
    public void preflightReturnsReportWith422WhenHardErrorsExist() {
        RuleDefinitionController controller = new RuleDefinitionController();
        ReflectionTestUtils.setField(controller, "lifecycleService", new RuleLifecycleService() {
            @Override
            public RulePreflightReport preflightReport(Long definitionId, Long revisionId) {
                RulePreflightReport report = new RulePreflightReport();
                report.setRevisionId(revisionId);
                report.setValid(false);
                return report;
            }
        });

        R<RulePreflightReport> response = controller.preflight(10L, 3L);

        assertEquals(422, response.getCode());
        assertEquals(Long.valueOf(3L), response.getData().getRevisionId());
    }
}
