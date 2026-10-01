package com.hengshucredit.rule.runtime;

import com.hengshucredit.rule.client.http.RuleHttpClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class RuntimeAuthenticationTest {
    @Test
    public void basicAuthDoesNotRequireLegacyToken() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/rule/sync/execute/R", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] json = "{\"code\":200,\"data\":{\"success\":true,\"result\":12}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });
        server.start();
        try {
            MockEnvironment env = new MockEnvironment().withProperty("RULE_SERVER_URL", "http://127.0.0.1:" + server.getAddress().getPort())
                    .withProperty("RULE_AUTH_TYPE", "BASIC").withProperty("RULE_USERNAME", "Caller")
                    .withProperty("RULE_PASSWORD", "secret").withProperty("RULE_TOKEN_EXCHANGE_ENABLED", "false");
            try (RuleHttpClient client = new RuntimeConfiguration().httpClient(env)) {
                assertTrue(client.execute("R", Map.of()).isSuccess());
                assertEquals("Basic " + Base64.getEncoder().encodeToString("Caller:secret".getBytes(StandardCharsets.UTF_8)),
                        authorization.get());
            }
        } finally { server.stop(0); }
    }
}
