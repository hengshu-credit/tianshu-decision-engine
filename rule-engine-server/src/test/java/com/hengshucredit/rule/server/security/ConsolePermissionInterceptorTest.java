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
