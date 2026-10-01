package com.hengshucredit.rule.server.consolelogin;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConsoleSessionAuthInterceptorTest {

    @Test
    public void allowsAnonymousProjectTokenExchange() throws Exception {
        RuleEngineConsoleLoginProperties properties = new RuleEngineConsoleLoginProperties();
        properties.setEnabled(true);
        ConsoleSessionAuthInterceptor interceptor = new ConsoleSessionAuthInterceptor(properties);

        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest("POST", "/api/rule/auth/token"),
                new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
    }

    @Test
    public void delegatesOpenRuleEndpointToProjectAuthentication() throws Exception {
        RuleEngineConsoleLoginProperties properties = new RuleEngineConsoleLoginProperties();
        properties.setEnabled(true);
        ConsoleSessionAuthInterceptor interceptor = new ConsoleSessionAuthInterceptor(properties);

        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest("POST", "/api/rule/openapi/RISK_SCORE"),
                new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
    }

    @Test
    public void permissionVersionChangeInvalidatesExistingSession() throws Exception {
        RuleEngineConsoleLoginProperties properties = new RuleEngineConsoleLoginProperties();
        properties.setEnabled(true);
        ConsoleSessionAuthInterceptor interceptor =
                new ConsoleSessionAuthInterceptor(properties, accountService(5L));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/rule/project/list");
        MockHttpSession session = (MockHttpSession) request.getSession(true);
        session.setAttribute(properties.getSessionUsernameAttribute(), "admin");
        session.setAttribute(properties.getSessionUserIdAttribute(), 1L);
        session.setAttribute(
                properties.getSessionPermissionVersionAttribute(), 4L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertTrue(response.getContentAsString().contains("会话权限已变更"));
    }

    private static DatabaseConsoleAccountService accountService(Long version) {
        return new DatabaseConsoleAccountService() {
            @Override
            public ConsoleLoginResult currentUser(Long userId) {
                return new ConsoleLoginResult(
                        true, false, userId, "admin", "admin", version,
                        Collections.emptyList(), Collections.emptySet());
            }
        };
    }
}
