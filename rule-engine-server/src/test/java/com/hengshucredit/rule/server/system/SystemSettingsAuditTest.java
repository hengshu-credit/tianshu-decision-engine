package com.hengshucredit.rule.server.system;

import com.hengshucredit.rule.model.entity.ConsoleOperationLog;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.server.audit.OperationLogMetadataResolver;
import com.hengshucredit.rule.server.governance.GovernanceRequestView;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class SystemSettingsAuditTest {
    @Test public void approvalAuditUsesStableConfigurationGroupWithoutReadingItsSnapshot() {
        var resolver = new OperationLogMetadataResolver(Map.of());
        var request = new GovernanceApprovalRequest();
        request.setId(87L);
        request.setResourceType("SYSTEM_SETTING");
        request.setResourceId(4L);
        request.setSubmittedSnapshotJson("not-json-and-must-not-be-read");
        var log = new ConsoleOperationLog();
        resolver.describe(log, GovernanceRequestView.from(request));
        assertEquals("system", log.getModule());
        assertEquals("SYSTEM_SETTING", log.getTargetType());
        assertEquals("4", log.getTargetId());
        assertEquals("对外结果码", log.getTargetName());
        request.setResourceId(2L);
        resolver.describe(log, GovernanceRequestView.from(request));
        assertEquals("通知邮箱", log.getTargetName());
    }
}
