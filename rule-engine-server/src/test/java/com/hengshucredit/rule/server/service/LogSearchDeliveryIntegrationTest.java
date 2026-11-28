package com.hengshucredit.rule.server.service;

import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengshucredit.rule.server.search.ElasticLogClient;
import com.hengshucredit.rule.server.search.LogSearchDelivery;
import com.hengshucredit.rule.server.search.LogSearchDocumentsTest;
import com.hengshucredit.rule.server.search.LogSearchPages;

import org.junit.Assume;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Uses a private MySQL schema and a unique namespace on the local ES cluster. */
public class LogSearchDeliveryIntegrationTest {
    @Test(timeout = 120000)
    public void competingWritersRestoreOldRowsCompactAndReplaceExactlyOneLogicalFact()
            throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("logSearch.integration"));
        URI endpoint = URI.create(System.getenv("RULE_SEARCH_URL"));
        assertTrue(java.net.InetAddress.getByName(endpoint.getHost()).isLoopbackAddress());
        String prefix = "ts-it-" + UUID.randomUUID().toString().replace("-", "");
        String user = System.getenv().getOrDefault("RULE_SEARCH_USERNAME", "");
        String password = System.getenv().getOrDefault("RULE_SEARCH_PASSWORD", "");
        String apiKey = System.getenv().getOrDefault("RULE_SEARCH_API_KEY", "");
        var search =
                new ElasticLogClient(new ObjectMapper(), endpoint, prefix, apiKey, user, password);
        var codec = LogSearchDocumentsTest.codec();
        try (var db = new BillingSummaryTestDatabase()) {
            RuleSetStatisticsIntegrationTest.createLogTables(db);
            String schema =
                    new ClassPathResource("sql/schema.sql")
                            .getContentAsString(StandardCharsets.UTF_8);
            int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `rule_runtime_call_log`");
            db.jdbc.execute(schema.substring(start, schema.indexOf(';', start) + 1));
            var logs = RuleSetStatisticsIntegrationTest.logs(db);
            var full = RuleSetStatisticsIntegrationTest.frame(7, "SET", 19, true, false);
            // Whitespace is part of the original audit body and must round-trip byte for byte.
            full = full + " ".repeat(8192);
            var log = RuleSetStatisticsIntegrationTest.log("elastic-retry", full);
            log.setSource("CLIENT_SERVER");
            log.setSuccess(1);
            log.setStartedAt(LocalDateTime.of(2026, 10, 11, 10, 0));
            log.setHistoryFields(
                    "{\"version\":1,\"fields\":{\"VARIABLE:4\":null,\"MODEL_OUTPUT:9\":0}}");
            logs.saveLogical(log);
            db.jdbc.update(
                    "INSERT INTO"
                        + " rule_runtime_call_log(module_type,action_type,raw_request_body,original_request_body,raw_response_body,original_response_body,create_time)"
                        + " VALUES('DATASOURCE','API_INVOKE',REPEAT('a',5000),REPEAT('A',5000),REPEAT('x',5000),REPEAT('x',5000),'2026-10-11"
                        + " 12:00:00')");
            var manager = new DataSourceTransactionManager(db.source);
            var a = new LogSearchDelivery(db.jdbc, manager, search, codec);
            var b = new LogSearchDelivery(db.jdbc, manager, search, codec);
            try {
                ReflectionTestUtils.setField(a, "compactLogs", true);
                ReflectionTestUtils.setField(b, "compactLogs", true);
                drain(db, a, b);
                assertTrue(
                        db.jdbc.queryForObject(
                                "SELECT es_body_aliases=2 AND original_response_body IS NULL AND"
                                    + " original_request_body IS NOT NULL FROM"
                                    + " rule_runtime_call_log",
                                Boolean.class));
                var call =
                        db.jdbc.queryForObject(
                                "SELECT * FROM rule_runtime_call_log",
                                org.springframework.jdbc.core.BeanPropertyRowMapper.newInstance(
                                        com.hengshucredit.rule.model.entity.RuleRuntimeCallLog
                                                .class));
                assertEquals("x".repeat(5000), call.getOriginalResponseBody());
                assertEquals("A".repeat(5000), call.getOriginalRequestBody());
                call.setRawResponseBody(null);
                assertNull(call.getOriginalResponseBody());
                var ref = new ElasticLogClient.Ref(log.getId(), log.getCreateTime(), 1);
                String body = search.bodies(ElasticLogClient.EXECUTION, List.of(ref)).get(ref);
                assertEquals(
                        full, new ObjectMapper().readTree(body).path("value").path("traceInfo").textValue());
                assertEquals(
                        log.getHistoryFields(),
                        codec.open(
                                ElasticLogClient.HISTORY,
                                ref,
                                search.bodies(ElasticLogClient.HISTORY, List.of(ref)).get(ref),
                                String.class));
                assertTrue(
                        db.jdbc.queryForObject(
                                "SELECT trace_info IS NULL AND"
                                    + " OCTET_LENGTH(es_trace_archive)<OCTET_LENGTH(UNCOMPRESS(es_trace_archive))"
                                    + " FROM rule_execution_log",
                                Boolean.class));
                assertEquals(full, logs.getById(log.getId()).getTraceInfo());
                ReflectionTestUtils.setField(
                        logs, "searchPages", new LogSearchPages(search, codec, db.jdbc));
                assertEquals(full, logs.getById(log.getId()).getTraceInfo());
                var mysql = RuleSetStatisticsIntegrationTest.transactionalQuery(db, db.jdbc);
                var baseline = mysql.query(null, null, null, null, null);
                ReflectionTestUtils.setField(mysql, "search", search);
                var projected = mysql.query(null, null, null, null, null);
                assertEquals(baseline.get("overview"), projected.get("overview"));
                assertEquals(baseline.get("ruleSets"), projected.get("ruleSets"));
                var retry = RuleSetStatisticsIntegrationTest.log(log.getTraceId(), null);
                retry.setAttemptNo(2);
                retry.setSource("CLIENT_SERVER");
                retry.setSuccess(0);
                ReflectionTestUtils.setField(logs, "deferStatistics", true);
                logs.saveLogical(retry);
                assertNull(
                        "A pending failed retry cannot return the archived success",
                        logs.getById(log.getId()).getTraceInfo());
                logs.saveLogical(log); // delayed lower attempt must leave revision 2 current
                assertEquals(
                        0L,
                        db.jdbc
                                .queryForObject(
                                        "SELECT COUNT(*) FROM rule_execution_statistics_current",
                                        Long.class)
                                .longValue());
                var backfill = new RuleSetStatisticsBackfill(db.jdbc, manager);
                try {
                    assertEquals(1, backfill.processBatch());
                } finally {
                    backfill.close();
                }
                drain(db, a, b);
                var next = new ElasticLogClient.Ref(log.getId(), ref.time(), 3);
                assertFalse(
                        search.fields(ElasticLogClient.HISTORY, List.of(next), "eligible")
                                .get(next)
                                .get("eligible")
                                .asBoolean());
                assertTrue(search.bodies(ElasticLogClient.EXECUTION, List.of(ref)).isEmpty());
                assertEquals(
                        1L,
                        db.jdbc
                                .queryForObject(
                                        "SELECT COUNT(*) FROM rule_execution_log", Long.class)
                                .longValue());
            } finally {
                a.close();
                b.close();
            }
        } finally {
            var http = HttpClient.newHttpClient();
            for (var kind :
                    List.of(
                            ElasticLogClient.EXECUTION,
                            ElasticLogClient.CALL,
                            ElasticLogClient.STEPS,
                            ElasticLogClient.HISTORY)) {
                var name = prefix + "-" + kind + "-v1";
                for (var resource : List.of("/" + name + "-2026.10", "/_index_template/" + name)) {
                    var request = HttpRequest.newBuilder(endpoint.resolve(resource)).DELETE();
                    if (!apiKey.isEmpty()) request.header("Authorization", "ApiKey " + apiKey);
                    else if (!user.isEmpty())
                        request.header(
                                "Authorization",
                                "Basic "
                                        + Base64.getEncoder()
                                                .encodeToString(
                                                        (user + ":" + password)
                                                                .getBytes(StandardCharsets.UTF_8)));
                    var response =
                            http.send(request.build(), HttpResponse.BodyHandlers.discarding());
                    assertTrue(response.statusCode() == 200 || response.statusCode() == 404);
                }
            }
        }
    }

    private static void drain(
            BillingSummaryTestDatabase db, LogSearchDelivery a, LogSearchDelivery b)
            throws Exception {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        do {
            a.schedule();
            b.schedule();
            if (db.jdbc.queryForObject(
                            "SELECT (SELECT COUNT(*) FROM rule_execution_log WHERE es_due_at IS NOT"
                                    + " NULL)+(SELECT COUNT(*) FROM rule_runtime_call_log WHERE"
                                    + " es_due_at IS NOT NULL)",
                            Long.class)
                    == 0) return;
            Thread.sleep(100);
        } while (System.nanoTime() < end);
        fail("未能确认检索投递");
    }
}
