package com.hengshucredit.rule.server.security;

import com.hengshucredit.rule.model.dto.ProjectAuthSaveRequest;
import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.controller.mgmt.InstitutionAuthController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class InstitutionAuthPermissionsTest {

    @Test
    public void managingAProjectCannotGrantOrRevealInstitutionCredentials() throws Exception {
        assertFalse(allowed("project:edit", "create", ProjectAuthSaveRequest.class, HttpServletRequest.class));
        assertFalse(allowed("project:view", "list"));
        assertTrue(allowed("institution-auth:view", "list"));
        assertFalse(allowed("institution-auth:view", "full", Long.class, HttpServletRequest.class, HttpServletResponse.class));
        assertTrue(allowed("institution-auth:manage", "full", Long.class, HttpServletRequest.class, HttpServletResponse.class));
    }

    @Test
    public void projectOverridesCannotConferGlobalAuthorityAndReadOnlyAccountsCannotManage() {
        var limited = new ConsoleProjectAccess.Scope(7L, Set.of(42L), Set.of("project:edit"), false, true,
                Map.of(42L, Map.of("institution-auth:manage", "ALLOW", "institution-auth:view", "ALLOW")));
        assertFalse(limited.can("institution-auth:manage", 42L));
        assertFalse(limited.can("institution-auth:view"));
        var reader = new ConsoleProjectAccess.Scope(7L, Set.of(), Set.of("institution-auth:manage"), true, false, Map.of());
        assertFalse(reader.globalCan("institution-auth:manage"));
    }

    private static boolean allowed(String granted, String method, Class<?>... parameters) throws Exception {
        var props = new RuleEngineConsoleLoginProperties();
        var permissions = new ConsolePermissionService() {
            @Override public boolean hasPermission(Long userId, String code) { return granted.equals(code); }
        };
        var request = new MockHttpServletRequest("GET", "/api/rule/institution-auth");
        request.getSession(true).setAttribute(props.getSessionUserIdAttribute(), 7L);
        var handler = new HandlerMethod(new InstitutionAuthController(null, null),
                InstitutionAuthController.class.getDeclaredMethod(method, parameters));
        return new ConsolePermissionInterceptor(props, permissions)
                .preHandle(request, new MockHttpServletResponse(), handler);
    }
}
