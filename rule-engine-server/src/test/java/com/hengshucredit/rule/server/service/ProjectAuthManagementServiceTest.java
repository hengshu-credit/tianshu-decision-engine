package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ProjectAuthDTO;
import com.hengshucredit.rule.model.dto.ProjectAuthSaveRequest;
import com.hengshucredit.rule.model.dto.ProjectAuthTokenDTO;
import com.hengshucredit.rule.model.dto.ProjectTokenResponse;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleProjectAuth;
import com.hengshucredit.rule.model.entity.RuleProjectAuthToken;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.auth.ProjectAuthType;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public class ProjectAuthManagementServiceTest {

    private CredentialCipher cipher;
    private ManagementAuthService service;

    @Before
    public void setUp() {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setMasterKeys(Collections.singletonMap("v1", "test-master-key"));
        cipher = new CredentialCipher(properties);
        service = new ManagementAuthService(cipher);
        RuleProject project = new RuleProject();
        project.setId(7L);
        project.setProjectCode("credit");
        project.setStatus(1);
        service.projects.add(project);
    }

    @Test
    public void createsBasicAndCanReadCompleteEncryptedCredentialAgain() {
        ProjectAuthSaveRequest request = basicRequest();

        ProjectAuthDTO created = service.createAuth(7L, request);
        ProjectAuthDTO full = service.getFullAuth(7L, created.getId());

        assertEquals("BASIC_MAIN", created.getAuthCode());
        assertNull(created.getSecret());
        assertEquals("partner", full.getIdentifier());
        assertEquals("secret", full.getSecret());
        assertEquals("partner", cipher.decrypt(service.auths.get(0).getIdentifierCiphertext()));
        assertEquals("secret", cipher.decrypt(service.auths.get(0).getSecretCiphertext()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void authCodeCannotBeChanged() {
        ProjectAuthDTO created = service.createAuth(7L, basicRequest());
        ProjectAuthSaveRequest update = basicRequest();
        update.setId(created.getId());
        update.setAuthCode("BASIC_CHANGED");
        service.updateAuth(7L, created.getId(), update);
    }

    @Test
    public void disablingKeepsCredentialAndHistoryViewable() {
        ProjectAuthDTO created = service.createAuth(7L, basicRequest());

        service.setAuthStatus(7L, created.getId(), 0);

        assertEquals(Integer.valueOf(0), service.auths.get(0).getStatus());
        assertEquals("secret", service.getFullAuth(7L, created.getId()).getSecret());
    }

    @Test
    public void apiKeyCanBeGeneratedAndTokenCanBeViewedThenRevoked() {
        ProjectAuthSaveRequest request = new ProjectAuthSaveRequest();
        request.setAuthCode("API_PARTNER");
        request.setAuthName("合作方 API Key");
        request.setAuthType(ProjectAuthType.API_KEY);
        request.setPlacement("HEADER");
        request.setParameterName("X-Partner-Key");
        ProjectAuthDTO created = service.createAuth(7L, request);
        ProjectAuthDTO full = service.getFullAuth(7L, created.getId());
        assertNotNull(full.getSecret());

        ProjectAuthContext context = ProjectAuthContext.direct(7L, "credit", created.getId(),
                created.getAuthCode(), created.getAuthType());
        ProjectTokenResponse issued = service.issueToken(context);
        ProjectAuthTokenDTO fullToken = service.getFullToken(7L, created.getId(), service.tokens.get(0).getId());
        assertEquals(issued.getAccessToken(), fullToken.getAccessToken());

        service.revokeToken(7L, created.getId(), service.tokens.get(0).getId());
        assertEquals(Integer.valueOf(0), service.tokens.get(0).getStatus());
        assertNotNull(service.tokens.get(0).getRevokedTime());
    }

    @Test
    public void apiKeySecretCanBeRegeneratedAndReturnedOnce() {
        ProjectAuthSaveRequest request = new ProjectAuthSaveRequest();
        request.setAuthCode("API_ROTATE");
        request.setAuthName("Rotate API Key");
        request.setAuthType(ProjectAuthType.API_KEY);
        request.setPlacement("HEADER");
        request.setParameterName("X-Rotate-Key");
        ProjectAuthDTO created = service.createAuth(7L, request);
        String oldSecret = service.getFullAuth(7L, created.getId()).getSecret();

        ProjectAuthDTO regenerated = service.regenerateAuthSecret(7L, created.getId());

        assertNotNull(regenerated.getSecret());
        assertNotEquals(oldSecret, regenerated.getSecret());
        assertEquals(regenerated.getSecret(), cipher.decrypt(service.auths.get(0).getSecretCiphertext()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void crossProjectCredentialReadIsRejected() {
        ProjectAuthDTO created = service.createAuth(7L, basicRequest());
        service.getFullAuth(8L, created.getId());
    }

    private ProjectAuthSaveRequest basicRequest() {
        ProjectAuthSaveRequest request = new ProjectAuthSaveRequest();
        request.setAuthCode("BASIC_MAIN");
        request.setAuthName("主账号");
        request.setAuthType(ProjectAuthType.BASIC);
        request.setIdentifier("partner");
        request.setSecret("secret");
        request.setTokenTtlSeconds(7200);
        request.setTokenGraceSeconds(600);
        return request;
    }

    private static class ManagementAuthService extends ProjectAuthService {
        private final List<RuleProject> projects = new ArrayList<>();
        private final List<RuleProjectAuth> auths = new ArrayList<>();
        private final List<RuleProjectAuthToken> tokens = new ArrayList<>();
        private long nextAuthId = 100L;
        private long nextTokenId = 200L;

        private ManagementAuthService(CredentialCipher cipher) {
            super(cipher);
        }

        @Override
        protected RuleProject findProjectById(Long projectId) {
            for (RuleProject project : projects) {
                if (project.getId().equals(projectId)) return project;
            }
            return null;
        }

        @Override
        protected RuleProjectAuth findAuthById(Long authId) {
            for (RuleProjectAuth auth : auths) {
                if (auth.getId().equals(authId)) return auth;
            }
            return null;
        }

        @Override
        protected RuleProjectAuth findAuthByCode(String authCode) {
            for (RuleProjectAuth auth : auths) {
                if (authCode.equals(auth.getAuthCode())) return auth;
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
        protected void insertAuth(RuleProjectAuth auth) {
            auth.setId(nextAuthId++);
            auths.add(auth);
        }

        @Override
        protected void updateAuth(RuleProjectAuth auth) {
        }

        @Override
        protected void insertToken(RuleProjectAuthToken token) {
            token.setId(nextTokenId++);
            tokens.add(token);
        }

        @Override
        protected RuleProjectAuthToken findTokenById(Long tokenId) {
            for (RuleProjectAuthToken token : tokens) {
                if (token.getId().equals(tokenId)) return token;
            }
            return null;
        }

        @Override
        protected void updateToken(RuleProjectAuthToken token) {
        }

        @Override
        protected LocalDateTime currentDateTime() {
            return LocalDateTime.of(2026, 7, 15, 3, 0);
        }
    }
}
