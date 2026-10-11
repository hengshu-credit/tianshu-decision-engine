package com.hengshucredit.rule.server.transfer;

import com.hengshucredit.rule.model.dto.*;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.governance.GovernanceApprovalService;
import com.hengshucredit.rule.server.service.*;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.Assert.*;

public class OfflineRulePublicationGovernanceTest {
    @Test public void publishedImportsCreateUnifiedApprovalAndRequireItsSuccess() {
        verifyImport(true, true);
        verifyImport(false, false);
    }

    private void verifyImport(boolean publish, boolean expectedApproval) {
        var service = new OfflineResourceImportService(); var events = new ArrayList<String>();
        var revision = new RuleRevision(); revision.setId(30L); revision.setDefinitionId(20L); revision.setLockVersion(0);
        ReflectionTestUtils.setField(service, "definitionService", new RuleDefinitionService() {
            @Override public RuleDefinition createWithContent(RuleDefinition input) { input.setId(20L); return input; }
        });
        ReflectionTestUtils.setField(service, "lifecycleService", new RuleLifecycleService() {
            @Override public RuleRevision createDraft(Long id, Long base) { return revision; }
            @Override public RuleRevision submit(Long id, RuleLifecycleActionRequest action) { events.add("unmanaged-submit"); return revision; }
            @Override public RuleRevision approve(Long id, RuleLifecycleActionRequest action) { events.add("unmanaged-approve"); return revision; }
            @Override public RuleRevision publish(Long id, RuleLifecycleActionRequest action) { events.add("unmanaged-publish"); return revision; }
        });
        ReflectionTestUtils.setField(service, "draftService", new RuleDraftService() {
            @Override public RuleDraftSaveResponse save(RuleDraftSaveRequest request) { assertEquals("{\"script\":\"return 1;\"}", request.getModelJson()); var response = new RuleDraftSaveResponse(); response.setRevision(revision); return response; }
        });
        // The actual governance workflow is injected through its existing submission bridge.
        var submission = new RuleGovernanceSubmissionService(null, null, null, null, null) {
            @Override public RuleRevision submit(Long id, RuleLifecycleActionRequest action) { assertEquals(Long.valueOf(30), id); events.add("governed-submit"); revision.setGovernanceRequestId(40L); return revision; }
        };
        ReflectionTestUtils.setField(service, "ruleGovernanceSubmissionService", submission);
        ReflectionTestUtils.setField(service, "approvalService", new GovernanceApprovalService() {
            @Override public GovernanceApprovalRequest approve(Long id, GovernanceReviewRequest request, String actor) {
                assertEquals(Long.valueOf(40), id); assertEquals("reviewer", actor); events.add("governed-approve");
                var result = new GovernanceApprovalRequest(); result.setId(id); result.setStatus("APPROVED"); result.setResourceId(20L); return result;
            }
        });
        var options = new TransferImportOptions(null, "GLOBAL", "REUSE", "SUFFIX", "_copy", false, null, null, Map.of(), publish);
        Long id = ReflectionTestUtils.invokeMethod(service, "importRuleDraft", Map.of("ruleCode", "copy", "content", Map.of("modelJson", "{\"script\":\"return 1;\"}")), null, options, 0L, "reviewer");
        assertEquals(Long.valueOf(20), id);
        assertEquals(expectedApproval ? List.of("governed-submit", "governed-approve") : List.of(), events);
    }
}
