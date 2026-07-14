package com.hengshucredit.rule.client.auth;

import com.hengshucredit.rule.client.log.HttpLogReporter;
import com.hengshucredit.rule.client.sync.HttpSyncClient;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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

    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, data.length);
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
}
