package com.hengshucredit.rule.server.security;

import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ConsolePermissionServiceTest {

    @Test
    public void userDenyOverridesRoleGrantAndUserAllowAddsPermission() {
        ConsolePermissionService service = new ConsolePermissionService();
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("rule:edit", "DENY");
        overrides.put("approval:approve", "ALLOW");

        Set<String> effective = service.merge(
                Set.of("rule:view", "rule:edit"), overrides);

        Assert.assertEquals(
                Set.of("rule:view", "approval:approve"), effective);
    }

    @Test
    public void denyWinsWhenDuplicateOverridesArePresented() {
        ConsolePermissionService service = new ConsolePermissionService();

        Set<String> effective = service.merge(
                Set.of("rule:view"),
                java.util.List.of(
                        new ConsolePermissionService.PermissionOverride(
                                "rule:view", "ALLOW"),
                        new ConsolePermissionService.PermissionOverride(
                                "rule:view", "DENY")));

        Assert.assertFalse(effective.contains("rule:view"));
    }

    @Test
    public void permissionCatalogCoversGovernanceAndAccountOperations() {
        Set<String> codes = ConsolePermissionCodes.allCodes();

        Assert.assertTrue(codes.contains("approval:view"));
        Assert.assertTrue(codes.contains("approval:submit"));
        Assert.assertTrue(codes.contains("approval:approve"));
        Assert.assertTrue(codes.contains("account:manage"));
        Assert.assertTrue(codes.contains("account:approve"));
        Assert.assertTrue(codes.contains("account:role-assign"));
        Assert.assertTrue(codes.contains("account:password-reset"));
        Assert.assertTrue(codes.contains("role:manage"));
        Assert.assertTrue(codes.contains("field:submit"));
        Assert.assertTrue(codes.contains("project:submit"));
    }
}
