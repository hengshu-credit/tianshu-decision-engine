package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ProjectTokenResponse;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleProjectAuth;
import com.hengshucredit.rule.model.entity.RuleProjectAuthToken;
import com.hengshucredit.rule.server.auth.CachedBodyHttpServletRequest;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.auth.ProjectAuthType;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ProjectAuthTokenServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 15, 2, 0);

    private CredentialCipher cipher;
    private TokenAuthService service;
    private RuleProjectAuth auth;
    private ProjectAuthContext directContext;

    @Before
    public void setUp() {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setMasterKeys(Collections.singletonMap("v1", "test-master-key"));
        cipher = new CredentialCipher(properties);

        RuleProject project = new RuleProject();
        project.setId(7L);
        project.setProjectCode("credit");
        project.setStatus(1);

        auth = new RuleProjectAuth();
        auth.setId(11L);
        auth.setProjectId(project.getId());
        auth.setAuthCode("BASIC_MAIN");
        auth.setAuthType(ProjectAuthType.BASIC);
        auth.setLookupKey(cipher.lookupKey(ProjectAuthType.BASIC, "partner"));
        auth.setIdentifierCiphertext(cipher.encrypt("partner"));
        auth.setSecretCiphertext(cipher.encrypt("secret"));
        auth.setTokenTtlSeconds(7200);
        auth.setTokenGraceSeconds(600);
        auth.setStatus(1);
        service = new TokenAuthService(cipher, auth, project);
        directContext = ProjectAuthContext.direct(project.getId(), project.getProjectCode(), auth.getId(),
                auth.getAuthCode(), auth.getAuthType());
    }

    @Test
    public void everyExchangeIssuesDistinctTokenWithConfiguredGrace() {
        ProjectTokenResponse first = service.issueToken(directContext);
        ProjectTokenResponse second = service.issueToken(directContext);

        assertNotEquals(first.getTokenCode(), second.getTokenCode());
        assertNotEquals(first.getAccessToken(), second.getAccessToken());
        assertEquals(NOW.plusSeconds(7200), first.getExpiresAt());
        assertEquals(first.getExpiresAt().plusSeconds(600), first.getGraceExpiresAt());
        assertEquals(Integer.valueOf(7200), first.getExpiresInSeconds());
        assertEquals(Integer.valueOf(7800), first.getGraceExpiresInSeconds());
        assertEquals("Bearer", first.getTokenType());
        assertEquals("BASIC_MAIN", first.getAuthCode());
        assertEquals("credit", first.getProjectCode());
    }

    @Test
    public void bearerWorksInValidAndGracePhasesThenExpires() {
        ProjectTokenResponse issued = service.issueToken(directContext);

        ProjectAuthContext valid = service.authenticate(bearerRequest(issued.getAccessToken()));
        assertNotNull(valid);
        assertEquals("VALID", valid.getAuthPhase());
        assertEquals(issued.getTokenCode(), valid.getTokenCode());

        service.now = issued.getExpiresAt().plusSeconds(1);
        ProjectAuthContext grace = service.authenticate(bearerRequest(issued.getAccessToken()));
        assertNotNull(grace);
        assertEquals("GRACE", grace.getAuthPhase());

        service.now = issued.getGraceExpiresAt().plusSeconds(1);
        assertNull(service.authenticate(bearerRequest(issued.getAccessToken())));
    }

    @Test
    public void revokedBearerIsRejectedImmediately() {
        ProjectTokenResponse issued = service.issueToken(directContext);
        service.tokens.get(0).setStatus(0);

        assertNull(service.authenticate(bearerRequest(issued.getAccessToken())));
    }

    @Test(expected = IllegalArgumentException.class)
    public void bearerCannotExchangeForAnotherBearer() {
        ProjectAuthContext temporary = ProjectAuthContext.temporary(7L, "credit", 11L,
                "BASIC_MAIN", ProjectAuthType.BASIC, 21L, "TOKEN_A", "VALID");
        service.issueToken(temporary);
    }

    @Test
    public void tokenEndpointRecognizesUsernameAndPasswordBodyWithoutAuthCode() throws Exception {
        MockHttpServletRequest raw = new MockHttpServletRequest("POST", "/api/rule/auth/token");
        raw.setContentType("application/json");
        raw.setContent("{\"username\":\"partner\",\"password\":\"secret\"}"
                .getBytes(StandardCharsets.UTF_8));

        ProjectAuthContext context = service.authenticate(new CachedBodyHttpServletRequest(raw));

        assertNotNull(context);
        assertEquals("BASIC_MAIN", context.getAuthCode());
        assertEquals(ProjectAuthType.BASIC, context.getAuthType());
    }

    private MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rule/sync/all");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static class TokenAuthService extends ProjectAuthService {
        private final RuleProjectAuth auth;
        private final RuleProject project;
        private final List<RuleProjectAuthToken> tokens = new ArrayList<>();
        private LocalDateTime now = NOW;
        private long nextId = 100L;

        private TokenAuthService(CredentialCipher cipher, RuleProjectAuth auth, RuleProject project) {
            super(cipher);
            this.auth = auth;
            this.project = project;
        }

        @Override
        protected RuleProjectAuth findEnabledAuthByLookupKey(String lookupKey) {
            return auth.getLookupKey().equals(lookupKey) ? auth : null;
        }

        @Override
        protected RuleProjectAuth findAuthById(Long authId) {
            return auth.getId().equals(authId) ? auth : null;
        }

        @Override
        protected RuleProject findProjectById(Long projectId) {
            return project.getId().equals(projectId) ? project : null;
        }

        @Override
        protected RuleProjectAuthToken findEnabledTokenByLookupKey(String lookupKey) {
            for (RuleProjectAuthToken token : tokens) {
                if (lookupKey.equals(token.getLookupKey()) && Integer.valueOf(1).equals(token.getStatus())) {
                    return token;
                }
            }
            return null;
        }

        @Override
        protected void insertToken(RuleProjectAuthToken token) {
            token.setId(nextId++);
            tokens.add(token);
        }

        @Override
        protected void updateToken(RuleProjectAuthToken token) {
        }

        @Override
        protected LocalDateTime currentDateTime() {
            return now;
        }
    }
}
