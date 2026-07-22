package com.hengshucredit.rule.client.auth;

import com.hengshucredit.rule.client.log.HttpLogReporter;
import com.hengshucredit.rule.client.sync.HttpSyncClient;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import okhttp3.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HttpClientAuthenticationTest {

    private final AtomicReference<String> syncAuth = new AtomicReference<>();
    private final AtomicReference<String> logAuth = new AtomicReference<>();
    private HttpServer server;
    private String serverUrl;

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/rule/sync/all", exchange -> {
            syncAuth.set(exchange.getRequestHeaders().getFirst("X-Test-Auth"));
            respond(exchange, "{\"code\":200,\"data\":[]}");
        });
        server.createContext("/api/rule/log/report", exchange -> {
            logAuth.set(exchange.getRequestHeaders().getFirst("X-Test-Auth"));
            respond(exchange, "{\"code\":200}");
        });
        server.createContext("/api/rule/sync/unauthorized", exchange ->
                respond(exchange, 401, "{\"code\":401,\"message\":\"Project token expired\"}"));
        server.start();
        serverUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    public void syncAndLogClientsUseSharedRequestAuthenticator() {
        ClientRequestAuthenticator authenticator = new HeaderAuthenticator();
        HttpSyncClient syncClient = new HttpSyncClient(serverUrl, 1000, authenticator);
        HttpLogReporter logReporter = new HttpLogReporter(serverUrl, 1000, authenticator);

        syncClient.fetchAll();
        logReporter.report(Collections.singletonList(new RuleExecutionLog()));

        assertEquals("shared", syncAuth.get());
        assertEquals("shared", logAuth.get());
    }

    @Test
    public void syncDoesNotTurnAuthenticationFailureIntoMissingRule() {
        HttpSyncClient syncClient = new HttpSyncClient(serverUrl, 1000, new HeaderAuthenticator());

        try {
            syncClient.fetchRule("unauthorized");
        } catch (ProjectClientAuthenticationException e) {
            assertTrue(e.getMessage().contains("Project token expired"));
            return;
        }
        throw new AssertionError("Expected project authentication failure");
    }

    @Test
    public void tokenRefreshFailureIsReturnedToCaller() {
        HttpSyncClient syncClient = new HttpSyncClient(serverUrl, 1000, new FailingAuthenticator());

        try {
            syncClient.fetchAll();
        } catch (ProjectClientAuthenticationException e) {
            assertTrue(e.getMessage().contains("token refresh failed"));
            return;
        }
        throw new AssertionError("Expected token refresh failure");
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        respond(exchange, 200, body);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    private static class HeaderAuthenticator extends ClientRequestAuthenticator {
        private HeaderAuthenticator() {
            super("http://localhost", 1000, null);
        }

        @Override
        public Request authenticate(Request request) {
            return request.newBuilder().header("X-Test-Auth", "shared").build();
        }
    }

    private static class FailingAuthenticator extends ClientRequestAuthenticator {
        private FailingAuthenticator() {
            super("http://localhost", 1000, null);
        }

        @Override
        public Request authenticate(Request request) throws IOException {
            throw new IOException("token refresh failed");
        }
    }
}
