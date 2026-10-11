package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.server.security.ConsoleAccessGuard;
import com.hengshucredit.rule.server.security.ConsoleAccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.Assert;
import org.junit.Test;

public class ResourcePreflightControllerTest {
    @Test
    public void checkRequiresTheRequestedResourcePermissionBeforeLoadingIt() {
        ResourcePreflightController controller = new ResourcePreflightController();
        ReflectionTestUtils.setField(controller, "accessGuard", new ConsoleAccessGuard(null, null) {
            @Override public void requireResource(String type, String action) {
                Assert.assertEquals("MODEL", type);
                Assert.assertEquals("view", action);
                throw new ConsoleAccessDeniedException("model:view");
            }
        });
        Assert.assertThrows(ConsoleAccessDeniedException.class, () -> controller.check("MODEL", 1L));
    }
}
