package com.hengshucredit.rule.server.derived;

import com.hengshucredit.rule.server.functions.GeoFunctions;
import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.server.search.ElasticLogClient;
import com.hengshucredit.rule.server.search.LogSearchDocuments;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * MySQL selects the authorized logical revisions. ES can only contribute precisely those revisions.
 */
@Repository
@ConditionalOnProperty(name = "rule-engine.search.enabled", havingValue = "true")
public class ElasticHistoryReader {
    static final String COLUMNS =
            "l.id,l.create_time,l.es_revision,l.es_due_at,l.trace_id,l.started_at";
    private static final int MAX_ROWS = 100000;
    private final JdbcTemplate jdbc;
    private final ElasticLogClient es;
    private final LogSearchDocuments documents;

    public ElasticHistoryReader(
            JdbcTemplate jdbc, ElasticLogClient es, LogSearchDocuments documents) {
        this.jdbc = jdbc;
        this.es = es;
        this.documents = documents;
    }

    interface SnapshotConsumer {
        void accept(long id, String trace, LocalDateTime started, String raw);
    }

    private record Candidate(
            ElasticLogClient.Ref ref, String trace, LocalDateTime started, boolean visible) {}

    private List<Candidate> candidates(String sql, List<Object> args) {
        return jdbc.query(
                connection -> {
                    var ps = connection.prepareStatement(sql);
                    ps.setQueryTimeout(10);
                    for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                    return ps;
                },
                (rs, i) -> {
                    LocalDateTime started = rs.getObject("started_at", LocalDateTime.class);
                    return new Candidate(
                            new ElasticLogClient.Ref(
                                    rs.getLong("id"),
                                    rs.getObject("create_time", LocalDateTime.class),
                                    rs.getLong("es_revision")),
                            rs.getString("trace_id"),
                            started,
                            rs.getObject("es_due_at") == null);
                });
    }

    void read(String sql, List<Object> args, SnapshotConsumer consume) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        var rows = candidates(sql, args);
        if (rows.size() > MAX_ROWS)
            throw new IllegalStateException("执行日志历史窗口超过 100000 条，请缩小时间或作用范围；未返回截断统计值");
        for (int i = 0; i < rows.size(); i += 128) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("历史字段读取超时，未返回截断统计值");
            var batch = rows.subList(i, Math.min(i + 128, rows.size()));
            var refs = batch.stream().filter(Candidate::visible).map(Candidate::ref).toList();
            var bodies = es.bodies(ElasticLogClient.HISTORY, refs);
            var snapshots = new HashMap<ElasticLogClient.Ref, String>();
            var missing = new ArrayList<ElasticLogClient.Ref>();
            for (var row : batch) {
                String raw =
                        documents.open(
                                ElasticLogClient.HISTORY,
                                row.ref(),
                                bodies.get(row.ref()),
                                String.class);
                if (raw == null) missing.add(row.ref());
                else snapshots.put(row.ref(), raw);
            }
            snapshots.putAll(sqlSnapshots(missing));
            for (var row : batch)
                consume.accept(
                        row.ref().id(), row.trace(), row.started(), snapshots.get(row.ref()));
        }
    }

    private Map<ElasticLogClient.Ref, String> sqlSnapshots(List<ElasticLogClient.Ref> refs) {
        Map<ElasticLogClient.Ref, String> values = new HashMap<>();
        if (refs.isEmpty()) return values;
        String tuples = String.join(",", Collections.nCopies(refs.size(), "(?,?)"));
        List<Object> args = new ArrayList<>();
        for (var ref : refs) {
            args.add(ref.id());
            args.add(ref.time());
        }
        jdbc.query(
                "SELECT id,create_time,es_revision,history_fields FROM rule_execution_log WHERE"
                    + " (id,create_time) IN ("
                        + tuples
                        + ")",
                rs -> {
                    values.put(
                            new ElasticLogClient.Ref(
                                    rs.getLong(1),
                                    rs.getObject(2, LocalDateTime.class),
                                    rs.getLong(3)),
                            rs.getString(4));
                },
                args.toArray());
        return values;
    }

    Map<LocalDateTime, IndexedHistoryCount.Count> count(
            String sql,
            List<Object> args,
            List<LocalDateTime> windows,
            String field,
            Object input) {
        Set<String> tokens = field == null ? Set.of() : documents.matchTokens(field, input);
        if (tokens == null) return Map.of();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        var rows = candidates(sql + " LIMIT " + (MAX_ROWS + 1), args);
        if (rows.size() > MAX_ROWS) return Map.of();
        long[] total = new long[windows.size()], matched = new long[windows.size()];
        long bytes = 0;
        for (int offset = 0; offset < rows.size(); offset += 512) {
            if (System.nanoTime() > deadline) return Map.of();
            var batch = rows.subList(offset, Math.min(offset + 512, rows.size()));
            var settled = batch.stream().filter(Candidate::visible).toList();
            List<Candidate> delta = batch;
            if (!settled.isEmpty() && aggregate(settled, windows, field, tokens, matched))
                delta = batch.stream().filter(row -> !row.visible()).toList();
            var values =
                    field == null
                            ? Map.<ElasticLogClient.Ref, String>of()
                            : sqlSnapshots(delta.stream().map(Candidate::ref).toList());
            for (var row : delta) {
                if (field != null) {
                    String raw = values.get(row.ref());
                    bytes = checkBudget(bytes, raw);
                    var snapshot = JSON.parseObject(raw);
                    if (snapshot == null || snapshot.getIntValue("version") != 1) return Map.of();
                    var fields = snapshot.getJSONObject("fields");
                    if (fields == null || !fields.containsKey(field))
                        return Map.of(); // may need a legacy API result
                    var stored = documents.matchTokens(field, fields.get(field));
                    if (stored == null) return Map.of();
                    if (Collections.disjoint(stored, tokens)) continue;
                }
                addWindows(row, windows, matched);
            }
            for (var row : batch) addWindows(row, windows, total);
        }
        return counts(windows, total, matched);
    }

    private static Map<LocalDateTime, IndexedHistoryCount.Count> counts(
            List<LocalDateTime> windows, long[] total, long[] matched) {
        Map<LocalDateTime, IndexedHistoryCount.Count> result = new LinkedHashMap<>();
        for (int i = 0; i < windows.size(); i++)
            result.put(windows.get(i), new IndexedHistoryCount.Count(total[i], matched[i]));
        return result;
    }

    Map<LocalDateTime, IndexedHistoryCount.Count> spatial(
            String sql,
            List<Object> args,
            List<LocalDateTime> windows,
            IndexedHistoryCount.Region region) {
        if (region.longitudeKey().equals(region.latitudeKey())) return Map.of();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        var rows = candidates(sql + " LIMIT " + (MAX_ROWS + 1), args);
        if (rows.size() > MAX_ROWS) return Map.of();
        long[] total = new long[windows.size()], matched = new long[windows.size()];
        long bytes = 0;
        var geo = new GeoFunctions();
        double degrees =
                Math.toDegrees(
                                (region.radius() + 0.000001)
                                        / GeoFunctions
                                                .EARTH_RADIUS_METERS)
                        + 1e-8;
        for (int i = 0; i < rows.size(); i += 256) {
            if (System.nanoTime() > deadline) return Map.of();
            var batch = rows.subList(i, Math.min(i + 256, rows.size()));
            Map<String, Candidate> visible = new HashMap<>();
            var pending = new ArrayList<Candidate>();
            for (var row : batch) {
                addWindows(row, windows, total);
                if (row.visible()) visible.put(row.ref().stamp(), row);
                else pending.add(row);
            }
            if (!visible.isEmpty()) {
                var query =
                        Map.<String, Object>of(
                                "size",
                                visible.size(),
                                "track_total_hits",
                                true,
                                "_source",
                                List.of("stamp", "numeric_fields"),
                                "query",
                                Map.of(
                                        "bool",
                                        Map.of(
                                                "filter",
                                                List.of(
                                                        Map.of(
                                                                "terms",
                                                                Map.of("stamp", visible.keySet())),
                                                        Map.of("term", Map.of("eligible", true))))),
                                "aggs",
                                Map.of(
                                        "coverage",
                                        Map.of(
                                                "filter",
                                                Map.of(
                                                        "bool",
                                                        Map.of(
                                                                "filter",
                                                                List.of(
                                                                        numericRange(
                                                                                region
                                                                                        .latitudeKey(),
                                                                                -90,
                                                                                90),
                                                                        numericRange(
                                                                                region
                                                                                        .longitudeKey(),
                                                                                -180,
                                                                                180)))))),
                                "post_filter",
                                numericRange(
                                        region.latitudeKey(),
                                        Math.max(-90, region.latitude() - degrees),
                                        Math.min(90, region.latitude() + degrees)));
                var response = es.historySearch(query);
                if (response == null
                        || response.path("aggregations")
                                        .path("coverage")
                                        .path("doc_count")
                                        .asInt(-1)
                                != visible.size()) return Map.of();
                var hits = response.path("hits");
                if (!"eq".equals(hits.path("total").path("relation").asText())
                        || !hits.path("hits").isArray()
                        || hits.path("hits").size() != hits.path("total").path("value").asInt(-1))
                    return Map.of();
                for (var hit : hits.path("hits")) {
                    var source = hit.path("_source");
                    var row = visible.remove(source.path("stamp").asText());
                    if (row == null) return Map.of();
                    Double lon = null, lat = null;
                    for (var number : source.path("numeric_fields")) {
                        String key = number.path("field").asText();
                        if (region.longitudeKey().equals(key))
                            lon = number.path("value").doubleValue();
                        if (region.latitudeKey().equals(key))
                            lat = number.path("value").doubleValue();
                    }
                    if (lon == null || lat == null) return Map.of();
                    if (geo.distanceMeters(region.longitude(), region.latitude(), lon, lat)
                            <= region.radius() + 0.000001) addWindows(row, windows, matched);
                }
            }
            var raw = sqlSnapshots(pending.stream().map(Candidate::ref).toList());
            for (var row : pending) {
                String entry = raw.get(row.ref());
                bytes = checkBudget(bytes, entry);
                var snapshot = JSON.parseObject(entry);
                if (snapshot == null
                        || snapshot.getIntValue("version") != 1
                        || snapshot.getJSONObject("fields") == null) return Map.of();
                var fields = snapshot.getJSONObject("fields");
                if (!(fields.get(region.latitudeKey()) instanceof Number lat)
                        || !(fields.get(region.longitudeKey()) instanceof Number lon))
                    return Map.of();
                if (geo.distanceMeters(
                                region.longitude(),
                                region.latitude(),
                                lon.doubleValue(),
                                lat.doubleValue())
                        <= region.radius() + 0.000001) addWindows(row, windows, matched);
            }
        }
        return counts(windows, total, matched);
    }

    private static Map<String, Object> numericRange(String field, double from, double until) {
        return Map.of(
                "nested",
                Map.of(
                        "path",
                        "numeric_fields",
                        "query",
                        Map.of(
                                "bool",
                                Map.of(
                                        "filter",
                                        List.of(
                                                Map.of(
                                                        "term",
                                                        Map.of("numeric_fields.field", field)),
                                                Map.of(
                                                        "range",
                                                        Map.of(
                                                                "numeric_fields.value",
                                                                Map.of(
                                                                        "gte", from, "lte",
                                                                        until))))))));
    }

    private static long checkBudget(long bytes, String snapshot) {
        bytes += snapshot == null ? 0 : snapshot.length() * 2L;
        if (bytes > 64L * 1024 * 1024)
            throw new IllegalStateException("历史窗口超过 64 MiB，请缩小范围；未返回截断统计值");
        return bytes;
    }

    private static void addWindows(Candidate row, List<LocalDateTime> windows, long[] counts) {
        for (int i = 0; i < windows.size(); i++)
            if (!row.started().isBefore(windows.get(i))) counts[i]++;
    }

    private boolean aggregate(
            List<Candidate> rows,
            List<LocalDateTime> windows,
            String field,
            Set<String> tokens,
            long[] counts) {
        var filters = new ArrayList<Object>();
        filters.add(
                Map.of(
                        "terms",
                        Map.of("stamp", rows.stream().map(row -> row.ref().stamp()).toList())));
        filters.add(Map.of("term", Map.of("eligible", true)));
        filters.add(Map.of("exists", Map.of("field", "started_micros")));
        if (field != null) {
            filters.add(Map.of("term", Map.of("complete_keys", field)));
            filters.add(Map.of("term", Map.of("match_key_id", documents.matchKeyId())));
        }
        var periods = new LinkedHashMap<String, Object>();
        for (int i = 0; i < windows.size(); i++)
            periods.put(
                    "w" + i,
                    Map.of(
                            "range",
                            Map.of(
                                    "started_micros",
                                    Map.of(
                                            "gte",
                                            ElasticLogClient.microsAtOrAfter(windows.get(i))))));
        Object match =
                field == null
                        ? Map.of("match_all", Map.of())
                        : Map.of("terms", Map.of("match_keys", tokens));
        var query =
                Map.<String, Object>of(
                        "size",
                        0,
                        "track_total_hits",
                        true,
                        "query",
                        Map.of("bool", Map.of("filter", filters)),
                        "aggs",
                        Map.of(
                                "matched",
                                Map.of(
                                        "filter",
                                        match,
                                        "aggs",
                                        Map.of(
                                                "windows",
                                                Map.of("filters", Map.of("filters", periods))))));
        var response = es.historySearch(query);
        if (response == null) return false;
        var total = response.path("hits").path("total");
        if (!"eq".equals(total.path("relation").asText())
                || total.path("value").asLong(-1) != rows.size()) return false;
        var buckets = response.path("aggregations").path("matched").path("windows").path("buckets");
        for (int i = 0; i < windows.size(); i++)
            if (!buckets.path("w" + i).path("doc_count").isIntegralNumber()) return false;
        for (int i = 0; i < windows.size(); i++)
            counts[i] += buckets.path("w" + i).path("doc_count").longValue();
        return true;
    }
}
