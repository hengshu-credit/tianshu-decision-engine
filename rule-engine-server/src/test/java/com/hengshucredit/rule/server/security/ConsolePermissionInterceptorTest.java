package com.hengshucredit.rule.server.security;

import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

public class ConsolePermissionInterceptorTest {

    @Test
    public void independentModulesDoNotInheritUnrelatedViewPermissions() throws Exception {
        for (String[] sample : new String[][] {
                {"/api/rule/list/library", "field:view", "list:view"},
                {"/api/rule/log/list", "rule:view", "log:view"},
                {"/api/rule/billing/config", "project:view", "billing:view"},
                {"/api/rule/lineage/options", "approval:view", "lineage:view"}
        }) {
            Assert.assertFalse(sample[0], allowed("GET", sample[0], sample[1]));
            Assert.assertTrue(sample[0], allowed("GET", sample[0], sample[2]));
        }
    }

    @Test
    public void viewingProjectDoesNotAllowReadingFullCredentials() throws Exception {
        Assert.assertFalse(allowed("GET", "/api/rule/project/2/token/full", "project:view"));
        Assert.assertFalse(allowed("GET", "/api/rule/project/2/auth/3/full", "project:view"));
        Assert.assertTrue(allowed("GET", "/api/rule/project/2/token/full", "project:edit"));
    }

    @Test
    public void billingRefreshSubmissionRequiresEditWhileTaskProgressRequiresView() throws Exception {
        String path = "/api/rule/billing/summary/refresh";
        Assert.assertFalse(allowed("POST", path, "billing:view"));
        Assert.assertTrue(allowed("POST", path, "billing:edit"));
        Assert.assertTrue(allowed("GET", path, "billing:view"));
        Assert.assertTrue(allowed("GET", path + "/task-id", "billing:view"));
        Assert.assertFalse(allowed("GET", path + "/task-id", "project:view"));
    }

    @Test
    public void moduleViewsAndMutationsAreSeparatedAcrossManagementEndpoints() throws Exception {
        for (String[] sample : new String[][] {
                {"project", "project"}, {"definition", "rule"}, {"variable", "field"},
                {"dataobject", "field"}, {"list", "list"}, {"datasource", "datasource"},
                {"database", "database"}, {"model", "model"}, {"function", "function"},
                {"experiment", "experiment"}, {"billing", "billing"}
        }) {
            String path = "/api/rule/" + sample[0] + "/1";
            Assert.assertTrue(path, allowed("GET", path, sample[1] + ":view"));
            Assert.assertFalse(path, allowed("PUT", path, sample[1] + ":view"));
            Assert.assertFalse(path, allowed("DELETE", path, sample[1] + ":view"));
            Assert.assertTrue(path, allowed("PUT", path, sample[1] + ":edit"));
            Assert.assertFalse(path, allowed("GET", path, "account:view"));
        }
    }

    @Test
    public void modulePrefixCannotAuthorizeAnUnknownManagementNamespace() throws Exception {
        Assert.assertFalse(allowed("GET", "/api/rule/project-secret/1", "project:view"));
        Assert.assertFalse(allowed("GET", "/api/rule/dashboard-secret/1", "project:view"));
    }

    private boolean allowed(String method, String path, String permission) throws Exception {
        RuleEngineConsoleLoginProperties properties = new RuleEngineConsoleLoginProperties();
        ConsolePermissionService service = new ConsolePermissionService() {
            @Override
            public boolean hasPermission(Long userId, String code) { return permission.equals(code); }
        };
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.getSession(true).setAttribute(properties.getSessionUserIdAttribute(), 9L);
        return new ConsolePermissionInterceptor(properties, service).preHandle(
                request, new MockHttpServletResponse(), handler("viewPublic"));
    }

    @Test
    public void missingPermissionReturnsStableForbiddenResponse() throws Exception {
        RuleEngineConsoleLoginProperties properties = new RuleEngineConsoleLoginProperties();
        ConsolePermissionService permissions = new FixedPermissionService(false);
        ConsolePermissionInterceptor interceptor =
                new ConsolePermissionInterceptor(properties, permissions);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/rule/approval/7/approve");
        request.getSession(true).setAttribute(
                properties.getSessionUserIdAttribute(), 9L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(
                request, response, handler("approve"));

        Assert.assertFalse(allowed);
        Assert.assertEquals(403, response.getStatus());
        Assert.assertTrue(response.getContentAsString()
                .contains("PERMISSION_DENIED"));
    }

    @Test
    public void matchingPermissionAllowsRequest() throws Exception {
        RuleEngineConsoleLoginProperties properties = new RuleEngineConsoleLoginProperties();
        ConsolePermissionInterceptor interceptor =
                new ConsolePermissionInterceptor(
                        properties, new FixedPermissionService(true));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/rule/approval/7/approve");
        request.getSession(true).setAttribute(
                properties.getSessionUserIdAttribute(), 9L);

        boolean allowed = interceptor.preHandle(
                request, new MockHttpServletResponse(), handler("approve"));

        Assert.assertTrue(allowed);
    }

    @Test
    public void unannotatedEndpointIsNotRestricted() throws Exception {
        ConsolePermissionInterceptor interceptor =
                new ConsolePermissionInterceptor(
                        new RuleEngineConsoleLoginProperties(),
                        new FixedPermissionService(false));

        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                handler("viewPublic"));

        Assert.assertTrue(allowed);
    }

    @Test
    public void unannotatedBusinessMutationStillRequiresModuleEditPermission()
            throws Exception {
        RuleEngineConsoleLoginProperties properties =
                new RuleEngineConsoleLoginProperties();
        ConsolePermissionInterceptor interceptor =
                new ConsolePermissionInterceptor(
                        properties, new FixedPermissionService(false));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PUT", "/api/rule/model/7");
        request.getSession(true).setAttribute(
                properties.getSessionUserIdAttribute(), 9L);
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(
                request, response, handler("viewPublic"));

        Assert.assertFalse(allowed);
        Assert.assertEquals(403, response.getStatus());
    }

    @Test
    public void unknownManagementEndpointFailsClosed() throws Exception {
        RuleEngineConsoleLoginProperties properties =
                new RuleEngineConsoleLoginProperties();
        ConsolePermissionInterceptor interceptor =
                new ConsolePermissionInterceptor(
                        properties, new FixedPermissionService(true));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/rule/unmapped/action");
        request.getSession(true).setAttribute(
                properties.getSessionUserIdAttribute(), 9L);
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(
                request, response, handler("viewPublic"));

        Assert.assertFalse(allowed);
        Assert.assertEquals(403, response.getStatus());
        Assert.assertTrue(response.getContentAsString()
                .contains("PERMISSION_MAPPING_MISSING"));
    }

    @Test
    public void explicitPermissionCoversOperationsPath() throws Exception {
        RuleEngineConsoleLoginProperties properties =
                new RuleEngineConsoleLoginProperties();
        ConsolePermissionInterceptor interceptor =
                new ConsolePermissionInterceptor(
                        properties, new FixedPermissionService(false));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/rule/ops/execution-metrics");
        request.getSession(true).setAttribute(
                properties.getSessionUserIdAttribute(), 9L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(
                request, response, handler("ruleView"));

        Assert.assertFalse(allowed);
        Assert.assertEquals(403, response.getStatus());
        Assert.assertTrue(response.getContentAsString()
                .contains("PERMISSION_DENIED"));
        Assert.assertFalse(response.getContentAsString()
                .contains("PERMISSION_MAPPING_MISSING"));
    }

    @Test
    public void dashboardEndpointUsesSectionLevelPermissionCropping()
            throws Exception {
        ConsolePermissionInterceptor interceptor =
                new ConsolePermissionInterceptor(
                        new RuleEngineConsoleLoginProperties(),
                        new FixedPermissionService(false));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/rule/dashboard/applications");

        boolean allowed = interceptor.preHandle(request,
                new MockHttpServletResponse(), handler("viewPublic"));

        Assert.assertTrue(allowed);
    }

    private static HandlerMethod handler(String methodName) throws Exception {
        Method method = SecuredController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(new SecuredController(), method);
    }

    private static class FixedPermissionService extends ConsolePermissionService {
        private final boolean allowed;

        private FixedPermissionService(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public boolean hasPermission(Long userId, String permissionCode) {
            return allowed && "approval:approve".equals(permissionCode);
        }
    }

    private static class SecuredController {
        @RequirePermission("approval:approve")
        public void approve() {
        }

        public void viewPublic() {
        }

        @RequirePermission("rule:view")
        public void ruleView() {
        }
    }
}
