package com.hengshucredit.rule.server.system;

import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import org.junit.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class SystemSettingsSecurityTest {
    @Test public void projectOverridesNeverGrantSystemConfiguration() {
        var scope = new ConsoleProjectAccess.Scope(1L, Set.of(10L), Set.of(), false, true,
                Map.of(10L, Map.of("system:edit", "ALLOW", "system:view", "ALLOW")));
        assertFalse(ConsoleProjectAccess.isProjectPermission("system:edit"));
        assertFalse(scope.can("system:edit", 10L)); assertFalse(scope.can("system:view"));
        assertFalse(scope.availablePermissions().contains("system:edit"));
    }
    @Test public void globalReadOnlyPreventsWritesAndMailSending() {
        var scope = new ConsoleProjectAccess.Scope(1L, Set.of(), Set.of("system:view", "system:edit", "system:mail-test", "system:audit"), true, false, Map.of());
        assertTrue(scope.can("system:view")); assertTrue(scope.can("system:audit"));
        assertFalse(scope.can("system:edit")); assertFalse(scope.can("system:mail-test"));
    }
}
