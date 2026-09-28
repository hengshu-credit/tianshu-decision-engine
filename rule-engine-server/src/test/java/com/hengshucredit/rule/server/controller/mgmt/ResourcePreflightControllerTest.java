package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.server.security.RequirePermission;
import org.junit.Assert;
import org.junit.Test;

public class ResourcePreflightControllerTest {
    @Test
    public void checkRequiresRuleViewPermission() throws Exception {
        RequirePermission permission = ResourcePreflightController.class
                .getDeclaredMethod("check", String.class, Long.class)
                .getAnnotation(RequirePermission.class);
        Assert.assertNotNull(permission);
        Assert.assertEquals("rule:view", permission.value());
    }
}
