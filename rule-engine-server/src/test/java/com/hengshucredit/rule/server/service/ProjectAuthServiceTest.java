package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleAuthAccessLog;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleProjectAuth;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.CachedBodyHttpServletRequest;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.auth.ProjectAuthType;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ProjectAuthServiceTest {

    private CredentialCipher cipher;
    private InMemoryProjectAuthService service;

    @Before
    public void setUp() {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setActiveKeyId("v1");
        properties.setMasterKeys(Collections.singletonMap("v1", "test-master-key"));
        cipher = new CredentialCipher(properties);
        service = new InMemoryProjectAuthService(cipher);
    }

    @Test
    public void basicCredentialsAreRecognizedWithoutAuthCode() {
        RuleProject project = project(7L, "credit", null);
        service.projects.add(project);
        service.auths.add(auth(11L, project.getId(), "BASIC_MAIN", ProjectAuthType.BASIC,
                "partner", "secret", null));

        MockHttpServletRequest request = new MockHttpServletRequest();
        String credentials = Base64.getEncoder().encodeToString(
                "partner:secret".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Basic " + credentials);

        ProjectAuthContext context = service.authenticate(request);

        assertNotNull(context);
        assertEquals(Long.valueOf(7L), context.getProjectId());
        assertEquals("BASIC_MAIN", context.getAuthCode());
        assertEquals(ProjectAuthType.BASIC, context.getAuthType());
        assertEquals("DIRECT", context.getAuthPhase());
    }

    @Test
    public void apiKeyUsesConfiguredHeaderWithoutAuthCode() {
        RuleProject project = project(8L, "fraud", null);
        service.projects.add(project);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("placement", "HEADER");
        config.put("parameterName", "X-Partner-Key");
        service.auths.add(auth(12L, project.getId(), "API_PARTNER", ProjectAuthType.API_KEY,
                null, "api-secret", JSON.toJSONString(config)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Partner-Key", "api-secret");

        ProjectAuthContext context = service.authenticate(request);

        assertNotNull(context);
        assertEquals("API_PARTNER", context.getAuthCode());
        assertEquals(ProjectAuthType.API_KEY, context.getAuthType());
    }

    @Test
    public void legacyHeaderAndQueryParameterRemainCompatible() {
        RuleProject project = project(9L, "legacy", null);
        service.projects.add(project);
        service.auths.add(auth(13L, project.getId(), "LEGACY_9", ProjectAuthType.LEGACY_TOKEN,
                null, "old-token", null));

        MockHttpServletRequest headerRequest = new MockHttpServletRequest();
        headerRequest.addHeader("X-Rule-Token", "old-token");
        MockHttpServletRequest queryRequest = new MockHttpServletRequest();
        queryRequest.setParameter("token", "old-token");

        assertEquals("LEGACY_9", service.authenticate(headerRequest).getAuthCode());
        assertEquals("LEGACY_9", service.authenticate(queryRequest).getAuthCode());
    }

    @Test
    public void legacyTokenTakesPriorityOverBasicWhenBothArePresented() {
        RuleProject project = project(9L, "priority", null);
        service.projects.add(project);
        service.auths.add(auth(13L, project.getId(), "LEGACY_PRIORITY", ProjectAuthType.LEGACY_TOKEN,
                null, "old-token", null));
        service.auths.add(auth(14L, project.getId(), "BASIC_PRIORITY", ProjectAuthType.BASIC,
                "partner", "secret", null));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Rule-Token", "old-token");
        request.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                "partner:secret".getBytes(StandardCharsets.UTF_8)));

        assertEquals("LEGACY_PRIORITY", service.authenticate(request).getAuthCode());
    }

    @Test
    public void apiKeyTakesPriorityOverTokenJsonCredentials() throws Exception {
        RuleProject project = project(9L, "priority", null);
        service.projects.add(project);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("placement", "HEADER");
        config.put("parameterName", "X-Partner-Key");
        service.auths.add(auth(13L, project.getId(), "API_PRIORITY", ProjectAuthType.API_KEY,
                null, "api-secret", JSON.toJSONString(config)));
        service.auths.add(auth(14L, project.getId(), "BASIC_PRIORITY", ProjectAuthType.BASIC,
                "partner", "secret", null));
        MockHttpServletRequest raw = new MockHttpServletRequest("POST", "/api/rule/auth/token");
        raw.addHeader("X-Partner-Key", "api-secret");
        raw.setContent("{\"username\":\"partner\",\"password\":\"secret\"}"
                .getBytes(StandardCharsets.UTF_8));

        assertEquals("API_PRIORITY",
                service.authenticate(new CachedBodyHttpServletRequest(raw)).getAuthCode());
    }

    @Test
    public void clientIpDoesNotTrustCallerControlledForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "198.51.100.7");

        assertEquals("10.0.0.8", service.clientIp(request));
    }

    @Test
    public void invalidPasswordAndDisabledProjectAreRejected() {
        RuleProject project = project(10L, "disabled", null);
        project.setStatus(0);
        service.projects.add(project);
        service.auths.add(auth(14L, project.getId(), "BASIC_DISABLED", ProjectAuthType.BASIC,
                "partner-disabled", "secret", null));

        MockHttpServletRequest request = new MockHttpServletRequest();
        String credentials = Base64.getEncoder().encodeToString(
                "partner-disabled:wrong".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Basic " + credentials);
        assertNull(service.authenticate(request));

        credentials = Base64.getEncoder().encodeToString(
                "partner-disabled:secret".getBytes(StandardCharsets.UTF_8));
        request.removeHeader("Authorization");
        request.addHeader("Authorization", "Basic " + credentials);
        assertNull(service.authenticate(request));
        assertEquals("300005", service.authenticationFailureStatus(request).getCode());
    }

    @Test
    public void invalidPasswordHasAStablePublicFailureCode() {
        RuleProject project = project(16L, "enabled", null);
        service.projects.add(project);
        service.auths.add(auth(16L, project.getId(), "BASIC_ENABLED", ProjectAuthType.BASIC,
                "partner", "secret", null));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                "partner:wrong".getBytes(StandardCharsets.UTF_8)));

        assertEquals("300002", service.authenticationFailureStatus(request).getCode());
    }

    @Test
    public void legacyTokensAreMigratedOnceAndPlaintextColumnIsCleared() {
        RuleProject project = project(15L, "migrate", "plaintext-token");
        service.projects.add(project);

        assertEquals(1, service.migrateLegacyTokens());
        assertEquals(0, service.migrateLegacyTokens());

        assertEquals(1, service.auths.size());
        RuleProjectAuth auth = service.auths.get(0);
        assertEquals("LEGACY_15", auth.getAuthCode());
        assertEquals(ProjectAuthType.LEGACY_TOKEN, auth.getAuthType());
        assertEquals("plaintext-token", cipher.decrypt(auth.getSecretCiphertext()));
        assertNull(project.getAccessToken());
        assertEquals("plaintext-token", service.getLegacyToken(15L));
    }

    @Test
    public void accessLogUsesTrustedAuthenticationContextWithoutQuerySecrets() {
        ProjectAuthContext context = ProjectAuthContext.temporary(7L, "credit", 11L,
                "BASIC_MAIN", ProjectAuthType.BASIC, 21L, "TOKEN_A", "GRACE");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/rule/sync/execute/R001");
        request.setQueryString("apiKey=must-not-be-logged");
        request.addHeader("X-Request-Id", "request-a");

        service.recordAccess(request, context, true, null);

        assertNotNull(service.lastAccessLog);
        assertEquals(Long.valueOf(11L), service.lastAccessLog.getAuthId());
        assertEquals("BASIC_MAIN", service.lastAccessLog.getAuthCode());
        assertEquals(Long.valueOf(21L), service.lastAccessLog.getTokenId());
        assertEquals("TOKEN_A", service.lastAccessLog.getTokenCode());
        assertEquals("GRACE", service.lastAccessLog.getAuthPhase());
        assertEquals("/api/rule/sync/execute/R001", service.lastAccessLog.getRequestUri());
        assertEquals(Integer.valueOf(1), service.lastAccessLog.getSuccess());
    }

    private RuleProjectAuth auth(Long id, Long projectId, String authCode, String authType,
                                 String identifier, String secret, String configJson) {
        RuleProjectAuth auth = new RuleProjectAuth();
        auth.setId(id);
        auth.setProjectId(projectId);
        auth.setAuthCode(authCode);
        auth.setAuthName(authCode);
        auth.setAuthType(authType);
        auth.setLookupKey(cipher.lookupKey(authType, identifier == null ? secret : identifier));
        auth.setIdentifierCiphertext(cipher.encrypt(identifier));
        auth.setSecretCiphertext(cipher.encrypt(secret));
        auth.setConfigJson(configJson);
        auth.setTokenTtlSeconds(7200);
        auth.setTokenGraceSeconds(600);
        auth.setStatus(1);
        return auth;
    }

    private RuleProject project(Long id, String code, String token) {
        RuleProject project = new RuleProject();
        project.setId(id);
        project.setProjectCode(code);
        project.setProjectName(code);
        project.setAccessToken(token);
        project.setStatus(1);
        return project;
    }

    private static class InMemoryProjectAuthService extends ProjectAuthService {
        private final List<RuleProjectAuth> auths = new ArrayList<>();
        private final List<RuleProject> projects = new ArrayList<>();
        private long nextAuthId = 100L;
        private RuleAuthAccessLog lastAccessLog;

        private InMemoryProjectAuthService(CredentialCipher cipher) {
            super(cipher);
        }

        private String clientIp(MockHttpServletRequest request) {
            return resolveClientIp(request);
        }

        @Override
        protected RuleProjectAuth findEnabledAuthByLookupKey(String lookupKey) {
            for (RuleProjectAuth auth : auths) {
                if (lookupKey.equals(auth.getLookupKey()) && Integer.valueOf(1).equals(auth.getStatus())) {
                    return auth;
                }
            }
            return null;
        }

        @Override
        protected RuleProjectAuth findAuthByLookupKey(String lookupKey) {
            for (RuleProjectAuth auth : auths) {
                if (lookupKey.equals(auth.getLookupKey())) return auth;
            }
            return null;
        }

        @Override
        protected List<RuleProjectAuth> findEnabledAuthByLookupKeys(List<String> lookupKeys) {
            List<RuleProjectAuth> matches = new ArrayList<>();
            for (RuleProjectAuth auth : auths) {
                if (lookupKeys.contains(auth.getLookupKey()) && Integer.valueOf(1).equals(auth.getStatus())) {
                    matches.add(auth);
                }
            }
            return matches;
        }

        @Override
        protected RuleProject findProjectById(Long projectId) {
            for (RuleProject project : projects) {
                if (project.getId().equals(projectId)) return project;
            }
            return null;
        }

        @Override
        protected List<RuleProject> findProjectsWithLegacyToken() {
            List<RuleProject> matches = new ArrayList<>();
            for (RuleProject project : projects) {
                if (project.getAccessToken() != null && !project.getAccessToken().isEmpty()) {
                    matches.add(project);
                }
            }
            return matches;
        }

        @Override
        protected RuleProjectAuth findAuthByCode(String authCode) {
            for (RuleProjectAuth auth : auths) {
                if (authCode.equals(auth.getAuthCode())) return auth;
            }
            return null;
        }

        @Override
        protected void insertAuth(RuleProjectAuth auth) {
            auth.setId(nextAuthId++);
            auths.add(auth);
        }

        @Override
        protected void updateAuth(RuleProjectAuth auth) {
        }

        @Override
        protected void updateProject(RuleProject project) {
        }

        @Override
        protected void insertAccessLog(RuleAuthAccessLog accessLog) {
            this.lastAccessLog = accessLog;
        }
    }
}
