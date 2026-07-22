package com.hengshucredit.rule.server.service;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal blocking HTTP server for tests that must also run where Java NIO pipes are unavailable.
 */
final class HttpServer {

    private final ServerSocket serverSocket;
    private final Map<String, Handler> contexts = new ConcurrentHashMap<>();
    private volatile boolean running;
    private Thread acceptThread;

    private HttpServer(InetSocketAddress address) throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.bind(address);
    }

    static HttpServer create(InetSocketAddress address, int backlog) throws IOException {
        return new HttpServer(address);
    }

    void createContext(String path, Handler handler) {
        contexts.put(path, handler);
    }

    void start() {
        running = true;
        acceptThread = new Thread(this::acceptConnections, "external-api-test-http-server");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    InetSocketAddress getAddress() {
        return (InetSocketAddress) serverSocket.getLocalSocketAddress();
    }

    void stop(int delaySeconds) {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // The server is already stopped.
        }
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                Thread worker = new Thread(() -> handle(socket), "external-api-test-http-worker");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException e) {
                if (running) throw new IllegalStateException("Test HTTP server failed", e);
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket connection = socket;
             InputStream input = new BufferedInputStream(connection.getInputStream());
             OutputStream output = connection.getOutputStream()) {
            String requestLine = readLine(input);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ", 3);
            if (parts.length < 2) return;
            Headers requestHeaders = readHeaders(input);
            byte[] requestBody = readRequestBody(input, requestHeaders);
            String path = URI.create(parts[1]).getPath();
            Handler handler = contexts.get(path);
            Exchange exchange = new Exchange(requestHeaders, requestBody);
            if (handler == null) {
                exchange.sendResponseHeaders(404, 0L);
            } else {
                handler.handle(exchange);
            }
            exchange.writeTo(output);
        } catch (IOException ignored) {
            // A timeout test may close the client connection before the delayed response is written.
        }
    }

    private static Headers readHeaders(InputStream input) throws IOException {
        Headers headers = new Headers();
        String line;
        while ((line = readLine(input)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) headers.add(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
        }
        return headers;
    }

    private static byte[] readRequestBody(InputStream input, Headers headers) throws IOException {
        String transferEncoding = headers.getFirst("Transfer-Encoding");
        if (transferEncoding != null && transferEncoding.toLowerCase().contains("chunked")) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            while (true) {
                String sizeLine = readLine(input);
                if (sizeLine == null) throw new EOFException("Missing chunk size");
                int semicolon = sizeLine.indexOf(';');
                String sizeValue = semicolon >= 0 ? sizeLine.substring(0, semicolon) : sizeLine;
                int size = Integer.parseInt(sizeValue.trim(), 16);
                if (size == 0) {
                    readLine(input);
                    break;
                }
                body.write(readExactly(input, size));
                readLine(input);
            }
            return body.toByteArray();
        }
        String contentLength = headers.getFirst("Content-Length");
        return contentLength == null ? new byte[0] : readExactly(input, Integer.parseInt(contentLength));
    }

    private static byte[] readExactly(InputStream input, int length) throws IOException {
        byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(bytes, offset, length - offset);
            if (read < 0) throw new EOFException("Unexpected end of HTTP request");
            offset += read;
        }
        return bytes;
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int previous = -1;
        int current;
        while ((current = input.read()) >= 0) {
            if (previous == '\r' && current == '\n') {
                byte[] bytes = line.toByteArray();
                return new String(bytes, 0, Math.max(0, bytes.length - 1), StandardCharsets.ISO_8859_1);
            }
            line.write(current);
            previous = current;
        }
        return line.size() == 0 ? null : line.toString(StandardCharsets.ISO_8859_1);
    }

    @FunctionalInterface
    interface Handler {
        void handle(Exchange exchange) throws IOException;
    }

    static final class Exchange {
        private final Headers requestHeaders;
        private final InputStream requestBody;
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private int status = 200;
        private long responseLength = -1L;

        private Exchange(Headers requestHeaders, byte[] requestBody) {
            this.requestHeaders = requestHeaders;
            this.requestBody = new ByteArrayInputStream(requestBody);
        }

        Headers getRequestHeaders() {
            return requestHeaders;
        }

        InputStream getRequestBody() {
            return requestBody;
        }

        Headers getResponseHeaders() {
            return responseHeaders;
        }

        OutputStream getResponseBody() {
            return responseBody;
        }

        void sendResponseHeaders(int status, long responseLength) {
            this.status = status;
            this.responseLength = responseLength;
        }

        void close() {
        }

        private void writeTo(OutputStream output) throws IOException {
            byte[] body = responseLength < 0L ? new byte[0] : responseBody.toByteArray();
            output.write(("HTTP/1.1 " + status + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            if (responseHeaders.getFirst("Content-Length") == null) {
                responseHeaders.set("Content-Length", String.valueOf(body.length));
            }
            responseHeaders.set("Connection", "close");
            for (Map.Entry<String, List<String>> header : responseHeaders.entries()) {
                for (String value : header.getValue()) {
                    output.write((header.getKey() + ": " + value + "\r\n")
                            .getBytes(StandardCharsets.ISO_8859_1));
                }
            }
            output.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
            output.write(body);
            output.flush();
        }
    }

    static final class Headers {
        private final Map<String, List<String>> values =
                new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        void set(String name, String value) {
            values.put(name, new ArrayList<>(Collections.singletonList(value)));
        }

        void add(String name, String value) {
            values.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }

        String getFirst(String name) {
            List<String> found = values.get(name);
            return found == null || found.isEmpty() ? null : found.get(0);
        }

        Iterable<Map.Entry<String, List<String>>> entries() {
            return values.entrySet();
        }
    }
}
