package com.hengshucredit.rule.server.security;

import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.governance.GovernanceApprovalService;
import com.hengshucredit.rule.server.transfer.OfflineResourceTransferService;
import com.hengshucredit.rule.server.transfer.TransferRootRequest;
import org.junit.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.List;
import java.util.Set;

public class ConsoleCrossModulePermissionTest {
    @After public void cleanup() { RequestContextHolder.resetRequestAttributes(); }

    @Test public void approvalSubmitAloneCannotCreateModelDraft() {
        GovernanceApprovalService service = new GovernanceApprovalService();
        ReflectionTestUtils.setField(service, "accessGuard", guard(Set.of("approval:submit")));
        GovernanceDraftRequest draft = new GovernanceDraftRequest();
        draft.setResourceType("MODEL"); draft.setAction("CREATE"); draft.setSnapshotJson("{}");
        Assert.assertThrows(ConsoleAccessDeniedException.class, () -> service.createDraft(draft, "editor"));
    }

    @Test public void transferExportCannotReadUnauthorizedResource() {
        OfflineResourceTransferService service = new OfflineResourceTransferService();
        ReflectionTestUtils.setField(service, "accessGuard", guard(Set.of("transfer:export", "rule:view")));
        Assert.assertThrows(ConsoleAccessDeniedException.class,
                () -> service.export(List.of(new TransferRootRequest("MODEL", 3L)), false));
    }

    private ConsoleAccessGuard guard(Set<String> codes) {
        RuleEngineConsoleLoginProperties properties = new RuleEngineConsoleLoginProperties();
        properties.setEnabled(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(properties.getSessionUserIdAttribute(), 7L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return new ConsoleAccessGuard(properties, new ConsolePermissionService() {
            @Override public boolean hasPermission(Long id, String code) { return codes.contains(code); }
        });
    }
}
