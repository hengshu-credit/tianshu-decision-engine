package com.hengshucredit.rule.server.consolelogin;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
}
