package com.hengshucredit.rule.client.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class TokenExchangeHttpTest {

    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private HttpServer server;
    private String serverUrl;

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/rule/auth/token", this::handleToken);
        server.start();
        serverUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    public void exchangesUsernameAndPasswordWithoutSendingAuthCode() throws Exception {
        TokenExchangeManager manager = new TokenExchangeManager(
                serverUrl, 1000, ClientAuthConfig.basic("partner", "secret"));

        assertEquals("temporary-token", manager.getAccessToken());
        assertEquals("Basic " + Base64.getEncoder().encodeToString(
                "partner:secret".getBytes(StandardCharsets.UTF_8)), authorization.get());
        assertEquals("", requestBody.get());
    }

    private void handleToken(HttpExchange exchange) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(new String(readAll(exchange), StandardCharsets.UTF_8));
        byte[] response = ("{\"code\":200,\"data\":{" +
                "\"accessToken\":\"temporary-token\"," +
                "\"expiresInSeconds\":7200," +
                "\"graceExpiresInSeconds\":7800," +
                "\"expiresAt\":\"2000-01-01T00:00:00\"," +
                "\"graceExpiresAt\":\"2000-01-01T00:00:00\"}}").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private byte[] readAll(HttpExchange exchange) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int read;
        while ((read = exchange.getRequestBody().read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
