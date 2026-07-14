package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleProjectAuth;
import com.hengshucredit.rule.server.auth.CachedBodyHttpServletRequest;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.HmacRequestSigner;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.auth.ProjectAuthType;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ProjectAuthHmacTest {

    private static final long NOW = 1700000000L;
    private static final String ACCESS_KEY = "access-key-a";
    private static final String SECRET = "hmac-secret-a";

    private HmacAuthService service;

    @Before
    public void setUp() {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setActiveKeyId("v1");
        properties.setMasterKeys(Collections.singletonMap("v1", "test-master-key"));
        properties.setHmacTimeWindowSeconds(300L);
        CredentialCipher cipher = new CredentialCipher(properties);

        RuleProject project = new RuleProject();
        project.setId(7L);
        project.setProjectCode("credit");
        project.setStatus(1);

        RuleProjectAuth auth = new RuleProjectAuth();
        auth.setId(9L);
        auth.setProjectId(project.getId());
        auth.setAuthCode("HMAC_PARTNER");
        auth.setAuthType(ProjectAuthType.HMAC_SHA256);
        auth.setLookupKey(cipher.lookupKey(ProjectAuthType.HMAC_SHA256, ACCESS_KEY));
        auth.setIdentifierCiphertext(cipher.encrypt(ACCESS_KEY));
        auth.setSecretCiphertext(cipher.encrypt(SECRET));
        auth.setStatus(1);
        service = new HmacAuthService(cipher, properties, auth, project);
    }

    @Test
    public void authenticatesExactBodyAndRawQuery() throws Exception {
        byte[] body = "{\"amount\":100}".getBytes(StandardCharsets.UTF_8);
        CachedBodyHttpServletRequest request = signedRequest(body, NOW, "nonce-a");

        ProjectAuthContext context = service.authenticate(request);

        assertNotNull(context);
    }

    @Test
    public void rejectsChangedBodyAndStaleTimestamp() throws Exception {
        byte[] body = "{\"amount\":100}".getBytes(StandardCharsets.UTF_8);
        CachedBodyHttpServletRequest changed = signedRequest(
                "{\"amount\":101}".getBytes(StandardCharsets.UTF_8), body, NOW, "nonce-b");
        assertNull(service.authenticate(changed));

        CachedBodyHttpServletRequest stale = signedRequest(body, NOW - 301L, "nonce-c");
        assertNull(service.authenticate(stale));
    }

    @Test
    public void rejectsReplayedNonce() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertNotNull(service.authenticate(signedRequest(body, NOW, "nonce-replay")));
        assertNull(service.authenticate(signedRequest(body, NOW, "nonce-replay")));
    }

    private CachedBodyHttpServletRequest signedRequest(byte[] body, long timestamp, String nonce)
            throws Exception {
        return signedRequest(body, body, timestamp, nonce);
    }

    private CachedBodyHttpServletRequest signedRequest(byte[] body, byte[] signedBody,
                                                       long timestamp, String nonce) throws Exception {
        MockHttpServletRequest raw = new MockHttpServletRequest();
        raw.setMethod("POST");
        raw.setRequestURI("/api/rule/sync/execute/R001");
        raw.setQueryString("trace=a%2Bb&debug=true");
        raw.setContent(body);
        raw.addHeader("X-Rule-Access-Key", ACCESS_KEY);
        raw.addHeader("X-Rule-Timestamp", String.valueOf(timestamp));
        raw.addHeader("X-Rule-Nonce", nonce);
        raw.addHeader("X-Rule-Signature", HmacRequestSigner.signHex(SECRET, raw.getMethod(),
                raw.getRequestURI(), raw.getQueryString(), String.valueOf(timestamp), nonce, signedBody));
        return new CachedBodyHttpServletRequest(raw);
    }

    private static class HmacAuthService extends ProjectAuthService {
        private final ProjectAuthProperties properties;
        private final RuleProjectAuth auth;
        private final RuleProject project;
        private final Set<String> nonces = new HashSet<>();

        private HmacAuthService(CredentialCipher cipher, ProjectAuthProperties properties,
                                RuleProjectAuth auth, RuleProject project) {
            super(cipher);
            this.properties = properties;
            this.auth = auth;
            this.project = project;
        }

        @Override
        protected RuleProjectAuth findEnabledAuthByLookupKey(String lookupKey) {
            return auth.getLookupKey().equals(lookupKey) ? auth : null;
        }

        @Override
        protected RuleProject findProjectById(Long projectId) {
            return project.getId().equals(projectId) ? project : null;
        }

        @Override
        protected long currentEpochSeconds() {
            return NOW;
        }

        @Override
        protected long hmacTimeWindowSeconds() {
            return properties.getHmacTimeWindowSeconds();
        }

        @Override
        protected boolean claimHmacNonce(Long authId, String nonce) {
            return nonces.add(authId + ":" + nonce);
        }
    }
}
