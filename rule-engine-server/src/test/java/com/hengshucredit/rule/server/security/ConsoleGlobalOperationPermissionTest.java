package com.hengshucredit.rule.server.security;

import org.junit.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class ConsoleGlobalOperationPermissionTest {
    private ConsoleProjectAccess access(boolean all, boolean editable, Map<Long, Map<String, String>> overrides) {
        var scope = new ConsoleProjectAccess.Scope(1L, Set.of(9L), Set.of("model:view", "model:edit"), all, editable, overrides);
        return new ConsoleProjectAccess() { @Override public Scope current() { return scope; } };
    }

    @Test public void globalWarmupRequiresAllProjectsReadAndWriteWithoutDenyOverrides() {
        assertThrows(ConsoleAccessDeniedException.class, () -> access(false, true, Map.of()).requireUnrestrictedPermissions("model:view", "model:edit"));
        assertThrows(ConsoleAccessDeniedException.class, () -> access(true, false, Map.of()).requireUnrestrictedPermissions("model:view", "model:edit"));
        assertThrows(ConsoleAccessDeniedException.class, () -> access(true, true, Map.of(9L, Map.of("model:view", "DENY"))).requireUnrestrictedPermissions("model:view", "model:edit"));
        access(true, true, Map.of()).requireUnrestrictedPermissions("model:view", "model:edit");
    }

    @Test public void restrictedViewerReceivesNodeCountsWithoutOtherProjectsResourceDetails() {
        Map<String, Object> details = Map.of("state", "FAILED", "targetCount", 5, "failureCount", 1,
                "failures", java.util.List.of(Map.of("modelId", 77L, "modelName", "other-project-model")), "lastErrorType", "InternalFailure");
        var summary = access(false, true, Map.of()).globalStatusSummary(details, "model:view");
        assertEquals("FAILED", summary.get("state"));
        assertEquals(1, summary.get("failureCount"));
        assertEquals(true, summary.get("detailsRestricted"));
        assertFalse(summary.toString().contains("other-project-model"));
        assertFalse(summary.containsKey("lastErrorType"));
        assertSame(details, access(true, true, Map.of()).globalStatusSummary(details, "model:view"));
    }
}
