package com.hengshucredit.rule.server.config;

import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.auth.ProjectAuthType;
import com.hengshucredit.rule.server.service.ProjectAuthService;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TokenAuthInterceptorTest {

    @Test
    public void protectsCurrentRuleSyncEndpoints() {
        assertTrue(TokenAuthInterceptor.isProtectedPath("/api/rule/sync"));
        assertTrue(TokenAuthInterceptor.isProtectedPath("/api/rule/sync/all"));
        assertTrue(TokenAuthInterceptor.isProtectedPath("/api/rule/sync/functions/1"));
        assertTrue(TokenAuthInterceptor.isProtectedPath("/api/rule/log/report"));
        assertTrue(TokenAuthInterceptor.isProtectedPath("/api/rule/auth/token"));
    }

    @Test
    public void keepsLegacySyncEndpointProtected() {
        assertTrue(TokenAuthInterceptor.isProtectedPath("/api/sync"));
        assertTrue(TokenAuthInterceptor.isProtectedPath("/api/sync/all"));
    }

    @Test
    public void doesNotProtectSimilarNonSyncPaths() {
        assertFalse(TokenAuthInterceptor.isProtectedPath("/api/rule/sync-all"));
        assertFalse(TokenAuthInterceptor.isProtectedPath("/api/rule/syncfoo"));
        assertFalse(TokenAuthInterceptor.isProtectedPath("/api/rule/log/reporting"));
        assertFalse(TokenAuthInterceptor.isProtectedPath("/api/rule/definition"));
    }

    @Test
    public void attachesTrustedContextAndRecordsSuccessfulAccess() throws Exception {
        ProjectAuthContext context = ProjectAuthContext.direct(7L, "credit", 9L,
                "BASIC_MAIN", ProjectAuthType.BASIC);
        FakeProjectAuthService service = service(context);
        TokenAuthInterceptor interceptor = interceptor(service);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rule/sync/all");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));

        assertSame(context, ProjectAuthContext.from(request));
        assertEquals(Boolean.TRUE, service.lastSuccess);
        assertSame(context, service.lastContext);
    }

    @Test
    public void exposesGraceStateForTemporaryToken() throws Exception {
        ProjectAuthContext context = ProjectAuthContext.temporary(7L, "credit", 9L,
                "BASIC_MAIN", ProjectAuthType.BASIC, 21L, "TOKEN_A", "GRACE");
        TokenAuthInterceptor interceptor = interceptor(service(context));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET", "/api/rule/sync/all"),
                response, new Object()));

        assertEquals("grace", response.getHeader("X-Rule-Token-State"));
    }

    @Test
    public void recordsFailedAccessBeforeReturningUnauthorized() throws Exception {
        FakeProjectAuthService service = service(null);
        TokenAuthInterceptor interceptor = interceptor(service);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rule/sync/all");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));

        assertEquals(401, response.getStatus());
        assertEquals(Boolean.FALSE, service.lastSuccess);
        assertEquals("INVALID_CREDENTIAL", service.lastFailureReason);
    }

    @Test
    public void rateLimitedTokenExchangeReturns429BeforeAuthentication() throws Exception {
        FakeProjectAuthService service = service(null);
        service.tokenRequestAllowed = false;
        TokenAuthInterceptor interceptor = interceptor(service);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/rule/auth/token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));

        assertEquals(429, response.getStatus());
        assertEquals("RATE_LIMITED", service.lastFailureReason);
    }

    @Test
    public void tokenExchangeFailureIsCountedAndSuccessClearsCredentialFailures() throws Exception {
        FakeProjectAuthService failedService = service(null);
        TokenAuthInterceptor interceptor = interceptor(failedService);
        assertFalse(interceptor.preHandle(
                new MockHttpServletRequest("POST", "/api/rule/auth/token"),
                new MockHttpServletResponse(), new Object()));
        assertEquals(1, failedService.failureCount);

        ProjectAuthContext context = ProjectAuthContext.direct(7L, "credit", 9L,
                "BASIC_MAIN", ProjectAuthType.BASIC);
        FakeProjectAuthService successService = service(context);
        interceptor = interceptor(successService);
        assertTrue(interceptor.preHandle(
                new MockHttpServletRequest("POST", "/api/rule/auth/token"),
                new MockHttpServletResponse(), new Object()));
        assertEquals(1, successService.clearCount);
    }

    private TokenAuthInterceptor interceptor(ProjectAuthService service) {
        TokenAuthInterceptor interceptor = new TokenAuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "projectAuthService", service);
        return interceptor;
    }

    private FakeProjectAuthService service(ProjectAuthContext context) {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setMasterKeys(Collections.singletonMap("v1", "test-master-key"));
        return new FakeProjectAuthService(new CredentialCipher(properties), context);
    }

    private static class FakeProjectAuthService extends ProjectAuthService {
        private final ProjectAuthContext authenticated;
        private Boolean lastSuccess;
        private ProjectAuthContext lastContext;
        private String lastFailureReason;
        private boolean tokenRequestAllowed = true;
        private int failureCount;
        private int clearCount;

        private FakeProjectAuthService(CredentialCipher cipher, ProjectAuthContext authenticated) {
            super(cipher);
            this.authenticated = authenticated;
        }

        @Override
        public ProjectAuthContext authenticate(HttpServletRequest request) {
            return authenticated;
        }

        @Override
        public void recordAccess(HttpServletRequest request, ProjectAuthContext context,
                                 boolean success, String failureReason) {
            this.lastSuccess = success;
            this.lastContext = context;
            this.lastFailureReason = failureReason;
        }

        @Override
        public boolean isTokenRequestAllowed(HttpServletRequest request) {
            return tokenRequestAllowed;
        }

        @Override
        public void recordTokenFailure(HttpServletRequest request) {
            failureCount++;
        }

        @Override
        public void clearTokenFailures(HttpServletRequest request) {
            clearCount++;
        }
    }
}
