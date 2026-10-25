package com.hengshucredit.rule.runtime;

import com.hengshucredit.rule.client.auth.ClientRequestAuthenticator;
import com.hengshucredit.rule.model.security.GlobalAccessSignature;
import okhttp3.Request;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.*;

public class RuntimeAuthenticationTest {
    @Test
    public void basicAuthDoesNotRequireLegacyToken() throws Exception {
        MockEnvironment env = new MockEnvironment().withProperty("RULE_AUTH_TYPE", "BASIC")
                .withProperty("RULE_USERNAME", "Caller").withProperty("RULE_PASSWORD", "secret")
                .withProperty("RULE_TOKEN_EXCHANGE_ENABLED", "false");
        Request request = authenticate(env);
        assertEquals("Basic " + Base64.getEncoder().encodeToString("Caller:secret".getBytes(StandardCharsets.UTF_8)),
                request.header("Authorization"));
    }

    @Test
    public void globalKeyAuthSignsSdkSyncRequests() throws Exception {
        GlobalAccessSignature.AccessKeyPair pair = GlobalAccessSignature.generateKeyPair();
        MockEnvironment env = new MockEnvironment().withProperty("RULE_AUTH_TYPE", "GLOBAL_KEY")
                .withProperty("RULE_GLOBAL_PRIVATE_KEY", pair.privateKey());
        Request request = authenticate(env);
        assertTrue(GlobalAccessSignature.verify(pair.publicKey(), request.method(), request.url().encodedPath(),
                request.url().encodedQuery(), request.header(GlobalAccessSignature.TIMESTAMP_HEADER),
                request.header(GlobalAccessSignature.NONCE_HEADER), new byte[0],
                request.header(GlobalAccessSignature.SIGNATURE_HEADER)));
    }

    private Request authenticate(MockEnvironment env) throws Exception {
        return new ClientRequestAuthenticator("http://127.0.0.1:8080", 1000, RuntimeConfiguration.projectAuth(env))
                .authenticate(new Request.Builder().url("http://127.0.0.1:8080/api/rule/sync/all").get().build());
    }
}
