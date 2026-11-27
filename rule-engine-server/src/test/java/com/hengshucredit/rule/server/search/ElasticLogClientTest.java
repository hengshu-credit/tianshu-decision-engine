package com.hengshucredit.rule.server.search;

import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ElasticLogClientTest {
    private HttpServer server;
    private ElasticLogClient es;
    private final ObjectMapper json = new ObjectMapper();
    private volatile String reply, request, path;
    private final ElasticLogClient.Ref ref =
            new ElasticLogClient.Ref(9, LocalDateTime.parse("2026-01-31T23:59:59"), 2);

    @Before
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exc -> {
                    try (exc) {
                        path = exc.getRequestURI().toString();
                        request =
                                new String(
                                        exc.getRequestBody().readAllBytes(),
                                        StandardCharsets.UTF_8);
                        byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
                        exc.sendResponseHeaders(200, bytes.length);
                        exc.getResponseBody().write(bytes);
                    }
                });
        server.start();
        es =
                new ElasticLogClient(
                        json,
                        URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                        "qa",
                        "",
                        "",
                        "");
    }

    @After
    public void stop() {
        server.stop(0);
    }

    @Test
    public void partialBulkAndStaleVersionsNeverBecomeSuccessfulAcknowledgements()
            throws Exception {
        var a =
                new ElasticLogClient.Document(
                        ElasticLogClient.EXECUTION, ref, Map.of("stamp", ref.stamp()));
        var b =
                new ElasticLogClient.Document(
                        ElasticLogClient.HISTORY, ref, Map.of("stamp", ref.stamp()));
        var ok =
                Map.of(
                        "_index",
                        es.index(a.kind(), ref.time()),
                        "_id",
                        ref.key(),
                        "_version",
                        2,
                        "status",
                        201,
                        "_shards",
                        Map.of("failed", 0, "successful", 1));
        reply =
                json.writeValueAsString(
                        Map.of(
                                "items",
                                List.of(
                                        Map.of("index", ok),
                                        Map.of("index", Map.of("status", 429)))));
        assertEquals(List.of(true, false), es.bulk(es.bulkLine(a) + es.bulkLine(b), List.of(a, b)));
        assertEquals("/_bulk?refresh=wait_for", path);
        var header = json.readTree(request.lines().findFirst().orElseThrow()).get("index");
        assertEquals("qa-execution-log-v1-2026.01", header.get("_index").asText());
        assertEquals("external_gte", header.get("version_type").asText());
        assertEquals(2, header.get("version").asInt());
        assertTrue(request.endsWith("\n"));
        reply = "{\"items\":[]}";
        assertThrows(IllegalStateException.class, () -> es.bulk(es.bulkLine(a), List.of(a)));
    }

    @Test
    public void replacedMissingAndFailedShardResultsUseCanonicalFallback() throws Exception {
        var other = new ElasticLogClient.Ref(10, ref.time(), 1);
        var doc =
                Map.of(
                        "_id",
                        ref.key(),
                        "_index",
                        es.index(ElasticLogClient.HISTORY, ref.time()),
                        "_version",
                        2,
                        "found",
                        true,
                        "_source",
                        Map.of("stamp", ref.stamp(), "body", "sealed"));
        reply =
                json.writeValueAsString(
                        Map.of("docs", List.of(doc, Map.of("error", "failed shard"))));
        assertEquals(
                Map.of(ref, "sealed"), es.bodies(ElasticLogClient.HISTORY, List.of(ref, other)));
        reply = json.writeValueAsString(Map.of("docs", List.of(doc)));
        assertTrue(
                es.bodies(
                                ElasticLogClient.HISTORY,
                                List.of(new ElasticLogClient.Ref(ref.id(), ref.time(), 3)))
                        .isEmpty());
        reply = "{\"timed_out\":false,\"_shards\":{\"failed\":1}}";
        assertNull(es.historySearch(Map.of("size", 0)));
        reply = "{\"timed_out\":true,\"_shards\":{\"failed\":0}}";
        assertNull(es.historySearch(Map.of("size", 0)));
    }
}
