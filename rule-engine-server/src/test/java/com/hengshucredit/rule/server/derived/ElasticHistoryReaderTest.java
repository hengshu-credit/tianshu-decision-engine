package com.hengshucredit.rule.server.derived;

import static org.junit.Assert.*;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.server.search.ElasticLogClient;
import com.hengshucredit.rule.server.search.LogSearchDocuments;
import com.hengshucredit.rule.server.search.LogSearchDocumentsTest;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElasticHistoryReaderTest {
    private static final String SQL =
            "SELECT "
                    + ElasticHistoryReader.COLUMNS
                    + " FROM rule_execution_log l WHERE l.root_rule_id=?";
    private static final LocalDateTime AT = LocalDateTime.parse("2026-11-26T09:00:00");
    private static final String VALUE =
            "{\"version\":1,\"fields\":{\"VARIABLE:1\":\"device\",\"VARIABLE:2\":null,\"MODEL_OUTPUT:2\":123456789000000000.012300}}";

    @Test
    public void confirmedAndPendingFactsMergeAtTheSameRevisionAndUnconfirmedCountsUseMysql()
            throws Exception {
        var ds =
                new SingleConnectionDataSource("jdbc:h2:mem:es_history;MODE=MySQL", "sa", "", true);
        try {
            var jdbc = new JdbcTemplate(ds);
            jdbc.execute(
                    "CREATE TABLE rule_execution_log(id BIGINT,create_time TIMESTAMP,es_revision"
                        + " BIGINT,es_due_at TIMESTAMP,trace_id VARCHAR(64),started_at"
                        + " TIMESTAMP,root_rule_id BIGINT,history_fields CLOB)");
            for (int i = 1; i <= 4; i++)
                jdbc.update(
                        "INSERT INTO rule_execution_log VALUES(?,?,?,?,?,?,?,?)",
                        i,
                        AT,
                        2,
                        i == 2 ? AT : null,
                        "t" + i,
                        AT,
                        i == 4 ? 9 : 1,
                        VALUE);
            var codec = LogSearchDocumentsTest.codec();
            var es = new FakeEs(codec);
            var reader = new ElasticHistoryReader(jdbc, es, codec);
            var snapshots = new ArrayList<String>();
            reader.read(SQL, List.of(1), (id, trace, time, raw) -> snapshots.add(raw));
            assertEquals(List.of(VALUE, VALUE, VALUE), snapshots);
            assertEquals(List.of(1L, 3L), es.asked.stream().map(ElasticLogClient.Ref::id).toList());
            var expected = new IndexedHistoryCount.Count(3, 3);
            var count = reader.count(SQL, List.of(1), List.of(AT), "VARIABLE:1", "device");
            assertEquals(expected, count.get(AT));
            assertTrue(es.query.contains("complete_keys"));
            assertFalse(es.query.contains("device"));
            assertTrue(es.query.contains("1@"));
            assertFalse(es.query.contains("4@"));
            es.coverage =
                    1; // one settled revision is gone, the other still exists: never add a partial
                       // aggregate
            assertEquals(
                    expected,
                    reader.count(SQL, List.of(1), List.of(AT), "VARIABLE:1", "device").get(AT));
            assertEquals(
                    0,
                    reader.count(SQL, List.of(1), List.of(AT), "VARIABLE:1", "other")
                            .get(AT)
                            .matched());
            jdbc.update(
                    "UPDATE rule_execution_log SET"
                        + " history_fields='{\"version\":1,\"fields\":{},\"apiIds\":[15]}' WHERE"
                        + " id=2");
            assertTrue(
                    reader.count(SQL, List.of(1), List.of(AT), "VARIABLE:1", "device").isEmpty());
            var windows = List.of(AT.minusDays(1), AT.plusSeconds(1));
            assertEquals(
                    new IndexedHistoryCount.Count(0, 0),
                    reader.count(SQL, List.of(1), windows, null, null).get(windows.get(1)));
        } finally {
            ds.destroy();
        }
    }

    static class FakeEs extends ElasticLogClient {
        int coverage = 2;
        String query;
        List<Ref> asked = List.of();
        final LogSearchDocuments codec;

        FakeEs(LogSearchDocuments codec) {
            super(new ObjectMapper(), URI.create("http://127.0.0.1:9200"), "test", "", "", "");
            this.codec = codec;
        }

        @Override
        public Map<Ref, String> bodies(String kind, Collection<Ref> refs) {
            asked = List.copyOf(refs);
            var result = new HashMap<Ref, String>();
            for (var ref : refs) {
                var log = new RuleExecutionLog();
                log.setSuccess(1);
                log.setSource("CLIENT_SERVER");
                log.setStartedAt(AT);
                log.setHistoryFields(VALUE);
                // Return an authenticated older envelope for one row, despite its current search
                // stamp.
                var sealedRef = ref.id() == 3 ? new Ref(ref.id(), ref.time(), 1) : ref;
                result.put(
                        ref,
                        (String)
                                codec.execution(sealedRef, log, null, true)
                                        .get(1)
                                        .source()
                                        .get("body"));
            }
            return result;
        }

        @Override
        public JsonNode historySearch(Map<String, Object> request) {
            var mapper = new ObjectMapper();
            query = JSON.toJSONString(request);
            return mapper.valueToTree(
                    Map.of(
                            "hits",
                            Map.of("total", Map.of("relation", "eq", "value", coverage)),
                            "aggregations",
                            Map.of(
                                    "matched",
                                    Map.of(
                                            "windows",
                                            Map.of(
                                                    "buckets",
                                                    Map.of("w0", Map.of("doc_count", 2)))))));
        }
    }
}
