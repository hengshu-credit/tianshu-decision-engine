package com.hengshucredit.rule.core.function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ImageInputFunctionsTest {

    private static final byte[] IMAGE_BYTES = "test-image-content".getBytes(StandardCharsets.UTF_8);
    private HttpServer server;
    private String baseUrl;

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image", exchange -> respond(exchange, 200, IMAGE_BYTES));
        server.createContext("/missing", exchange -> respond(exchange, 404, new byte[0]));
        server.createContext("/large", exchange -> {
            exchange.sendResponseHeaders(200, ImageInputFunctions.MAX_IMAGE_BYTES + 1L);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(300L);
                respond(exchange, 200, IMAGE_BYTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    public void acceptsPlainBase64AndDataUri() {
        ImageInputFunctions functions = new ImageInputFunctions();
        String base64 = Base64.getEncoder().encodeToString(IMAGE_BYTES);

        assertEquals(base64, functions.imageToBase64(base64, 1000D));
        assertEquals(base64, functions.imageToBase64("data:image/jpeg;base64," + base64, 1000D));
    }

    @Test
    public void downloadsHttpImageAsBase64() {
        String result = new ImageInputFunctions().imageToBase64(baseUrl + "/image", 1000D);

        assertEquals(Base64.getEncoder().encodeToString(IMAGE_BYTES), result);
    }

    @Test
    public void rejectsUnsupportedProtocolAndNonSuccessStatus() {
        assertFailure("file://C:/temp/face.jpg", 1000D, "HTTP(S)");
        assertFailure(baseUrl + "/missing", 1000D, "404");
    }

    @Test
    public void enforcesDownloadTimeoutAndSizeLimit() {
        assertFailure(baseUrl + "/slow", 100D, "图片下载超时（100 ms）");
        assertFailure(baseUrl + "/large", 1000D, "10 MB");
    }

    private static void assertFailure(String value, double timeoutMs, String message) {
        try {
            new ImageInputFunctions().imageToBase64(value, timeoutMs);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(message));
            return;
        }
        throw new AssertionError("Expected image conversion failure");
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
