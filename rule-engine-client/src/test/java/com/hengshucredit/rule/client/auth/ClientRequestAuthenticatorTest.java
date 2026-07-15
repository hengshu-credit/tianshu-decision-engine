package com.hengshucredit.rule.client.auth;

import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.Test;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;

public class ClientRequestAuthenticatorTest {

    @Test
    public void legacyTokenKeepsCompatibleHeader() throws Exception {
        ClientRequestAuthenticator authenticator = new ClientRequestAuthenticator(
                "http://localhost:8080", 1000, ClientAuthConfig.legacyToken("legacy-token"));
        Request request = new Request.Builder().url("http://localhost/api/rule/sync/all").get().build();

        Request authenticated = authenticator.authenticate(request);

        assertEquals("legacy-token", authenticated.header("X-Rule-Token"));
    }

    @Test
    public void exchangedCredentialUsesBearerForRuleRequests() throws Exception {
        ClientAuthConfig config = ClientAuthConfig.basic("partner", "secret");
        ClientRequestAuthenticator authenticator = new ClientRequestAuthenticator(
                config, new FixedTokenExchangeManager(config, "temporary-token"));
        Request request = new Request.Builder().url("http://localhost/api/rule/sync/all").get().build();

        Request authenticated = authenticator.authenticate(request);

        assertEquals("Bearer temporary-token", authenticated.header("Authorization"));
    }

    @Test
    public void hmacAddsCanonicalSignatureHeaders() throws Exception {
        ClientAuthConfig config = ClientAuthConfig.hmac("AK_TEST", "hmac-secret");
        config.setTokenExchangeEnabled(false);
        FixedHmacAuthenticator authenticator = new FixedHmacAuthenticator(config);
        Request request = new Request.Builder()
                .url("http://localhost/api/rule/sync/execute/R1?trace=true")
                .post(RequestBody.create("{}", okhttp3.MediaType.parse("application/json")))
                .build();

        Request authenticated = authenticator.authenticate(request);

        assertEquals("AK_TEST", authenticated.header("X-Rule-Access-Key"));
        assertEquals("1721000000", authenticated.header("X-Rule-Timestamp"));
        assertEquals("nonce-1", authenticated.header("X-Rule-Nonce"));
        assertEquals("1e202287921deb2a91c7efc328a40cb38d7ed22211578587f449630da47cfca7",
                authenticated.header("X-Rule-Signature"));
    }

    private static class FixedTokenExchangeManager extends TokenExchangeManager {
        private final String token;

        private FixedTokenExchangeManager(ClientAuthConfig config, String token) {
            super("http://localhost:8080", 1000, config);
            this.token = token;
        }

        @Override
        public String getAccessToken() {
            return token;
        }
    }

    private static class FixedHmacAuthenticator extends ClientRequestAuthenticator {
        private FixedHmacAuthenticator(ClientAuthConfig config) {
            super("http://localhost:8080", 1000, config);
        }

        @Override
        protected long currentEpochSeconds() {
            return 1721000000L;
        }

        @Override
        protected String generateNonce() {
            return "nonce-1";
        }
    }
}
