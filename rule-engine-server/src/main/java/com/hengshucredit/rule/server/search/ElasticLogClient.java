package com.hengshucredit.rule.server.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Versioned monthly indices. Retrying an event always addresses the same concrete document. */
@Component
@ConditionalOnProperty(name = "rule-engine.search.enabled", havingValue = "true")
public class ElasticLogClient {
    public static final String EXECUTION = "execution-log",
            CALL = "runtime-call",
            HISTORY = "history-feature",
            STEPS = "rule-step";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy.MM");
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final URI endpoint;
    private final String prefix;
    private final String authorization;
    private final ObjectMapper json;
    private final LongAdder fallback = new LongAdder();
    private final LongAdder reads = new LongAdder();
    private volatile long retryReadAfter;

    public ElasticLogClient(
            ObjectMapper json,
            @Value("${rule-engine.search.url}") URI endpoint,
            @Value("${rule-engine.search.index-prefix:tianshu}") String prefix,
            @Value("${rule-engine.search.api-key:}") String apiKey,
            @Value("${rule-engine.search.username:}") String username,
            @Value("${rule-engine.search.password:}") String password) {
        if (endpoint.getHost() == null
                || !List.of("http", "https").contains(endpoint.getScheme())
                || endpoint.getUserInfo() != null
                || endpoint.getRawQuery() != null
                || endpoint.getRawFragment() != null
                || !prefix.matches("[a-z0-9][a-z0-9-]{0,62}"))
            throw new IllegalArgumentException("ES 服务地址或索引前缀无效");
        this.json = json;
        this.endpoint = URI.create(endpoint.toString().replaceAll("/+$", "") + "/");
        this.prefix = prefix;
        this.authorization =
                !apiKey.isBlank()
                        ? "ApiKey " + apiKey
                        : username.isBlank()
                                ? null
                                : "Basic "
                                        + Base64.getEncoder()
                                                .encodeToString(
                                                        (username + ":" + password)
                                                                .getBytes(StandardCharsets.UTF_8));
    }

    public record Ref(long id, LocalDateTime time, long revision) {
        public String key() {
            return id + "@" + time;
        }

        public String stamp() {
            return key() + "#" + revision;
        }
    }

    public record Document(String kind, Ref ref, Map<String, Object> source) {}

    public static String date(LocalDateTime time) {
        return time == null ? null : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(time);
    }

    /** MySQL DATETIME(6) has microsecond precision and a wider year range than ES date_nanos. */
    public static long microsAtOrAfter(LocalDateTime time) {
        return time.toEpochSecond(java.time.ZoneOffset.UTC) * 1_000_000L
                + (time.getNano() + 999L) / 1000L;
    }

    String index(String kind, LocalDateTime time) {
        return prefix + "-" + kind + "-v1-" + MONTH.format(time);
    }

    public String historyIndices() {
        return prefix + "-" + HISTORY + "-v1-*";
    }

    void templates() {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String field :
                List.of(
                        "stamp",
                        "trace_id",
                        "root_trace_id",
                        "rule_trace_id",
                        "call_id",
                        "project_code",
                        "rule_code",
                        "source",
                        "module_type",
                        "action_type",
                        "target_code",
                        "model_type",
                        "auth_type",
                        "auth_code",
                        "token_code",
                        "artifact_digest",
                        "stats_status",
                        "field_keys",
                        "match_keys",
                        "complete_keys",
                        "match_key_id"))
            properties.put(field, Map.of("type", "keyword", "ignore_above", 1024));
        for (String field :
                List.of(
                        "id",
                        "project_id",
                        "root_rule_id",
                        "actor_user_id",
                        "revision_id",
                        "rule_version",
                        "attempt_no",
                        "success",
                        "cost_ms",
                        "target_ref_id",
                        "datasource_id",
                        "request_success",
                        "provider_request",
                        "found",
                        "started_micros")) properties.put(field, Map.of("type", "long"));
        for (String field : List.of("created_at", "started_at"))
            properties.put(
                    field, Map.of("type", "date", "format", "strict_date_optional_time_nanos"));
        properties.put("eligible", Map.of("type", "boolean"));
        properties.put("body", Map.of("type", "keyword", "index", false, "doc_values", false));
        properties.put(
                "frames",
                Map.of(
                        "type",
                        "nested",
                        "dynamic",
                        false,
                        "properties",
                        Map.of(
                                "key",
                                Map.of("type", "keyword"),
                                "hit",
                                Map.of("type", "long"),
                                "failed",
                                Map.of("type", "long"),
                                "cost",
                                Map.of("type", "long"),
                                "ruleId",
                                Map.of("type", "long"),
                                "items",
                                Map.of("type", "object", "enabled", false))));
        properties.put(
                "numeric_fields",
                Map.of(
                        "type",
                        "nested",
                        "properties",
                        Map.of(
                                "field",
                                Map.of("type", "keyword"),
                                "value",
                                Map.of("type", "double"))));
        // The deployment owns replica policy; refreshing mappings must preserve that policy.
        var replicaPolicy = List.of(prefix + "-log-replicas-v1");
        for (String kind : List.of(EXECUTION, CALL, HISTORY, STEPS)) {
            String name = prefix + "-" + kind + "-v1";
            request(
                    "PUT",
                    "_index_template/" + name,
                    encode(
                            Map.of(
                                    "index_patterns",
                                    List.of(name + "-*"),
                                    "composed_of",
                                    replicaPolicy,
                                    "ignore_missing_component_templates",
                                    replicaPolicy,
                                    "template",
                                    Map.of(
                                            "mappings",
                                            Map.of(
                                                    "dynamic",
                                                    "strict",
                                                    "properties",
                                                    properties)))),
                    false);
        }
    }

    String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalArgumentException("无法序列化检索文档", e);
        }
    }

    String bulkLine(Document document) {
        var ref = document.ref();
        return encode(
                        Map.of(
                                "index",
                                Map.of(
                                        "_index",
                                        index(document.kind(), ref.time()),
                                        "_id",
                                        ref.key(),
                                        "version",
                                        ref.revision(),
                                        "version_type",
                                        "external_gte")))
                + "\n"
                + encode(document.source())
                + "\n";
    }

    List<Boolean> bulk(String body, List<Document> docs) {
        JsonNode items = request("POST", "_bulk?refresh=wait_for", body, true).path("items");
        if (!items.isArray() || items.size() != docs.size())
            throw new IllegalStateException("ES 批次确认数量不符");
        List<Boolean> accepted = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            var item = items.get(i).path("index");
            Document doc = docs.get(i);
            accepted.add(
                    doc.ref().key().equals(item.path("_id").asText())
                            && index(doc.kind(), doc.ref().time())
                                    .equals(item.path("_index").asText())
                            && item.path("status").asInt() >= 200
                            && item.path("status").asInt() < 300
                            && item.path("_version").asLong() == doc.ref().revision()
                            && item.path("_shards").path("failed").asInt(-1) == 0
                            && item.path("_shards").path("successful").asInt() > 0);
        }
        return accepted;
    }

    /**
     * Callers already hold a permission-filtered MySQL identity and revision for every requested
     * record.
     */
    public Map<Ref, String> bodies(String kind, Collection<Ref> refs) {
        Map<Ref, String> result = new LinkedHashMap<>();
        fields(kind, refs, "body")
                .forEach(
                        (ref, source) -> {
                            if (source.path("body").isTextual())
                                result.put(ref, source.path("body").textValue());
                        });
        return result;
    }

    public Map<Ref, JsonNode> fields(String kind, Collection<Ref> refs, String fields) {
        if (refs.isEmpty() || coolingDown()) return Map.of();
        List<Ref> ordered = List.copyOf(refs);
        var keys =
                ordered.stream()
                        .map(ref -> Map.of("_index", index(kind, ref.time()), "_id", ref.key()))
                        .toList();
        try {
            var docs =
                    request(
                                    "POST",
                                    "_mget?_source_includes=stamp," + fields,
                                    encode(Map.of("docs", keys)),
                                    false)
                            .path("docs");
            Map<Ref, JsonNode> result = new LinkedHashMap<>();
            if (docs.isArray() && docs.size() == ordered.size())
                for (int i = 0; i < docs.size(); i++) {
                    var doc = docs.get(i);
                    var ref = ordered.get(i);
                    var source = doc.path("_source");
                    if (doc.path("found").asBoolean()
                            && ref.key().equals(doc.path("_id").asText())
                            && index(kind, ref.time()).equals(doc.path("_index").asText())
                            && ref.revision() == doc.path("_version").asLong()
                            && ref.stamp().equals(source.path("stamp").asText()))
                        result.put(ref, source);
                }
            reads.add(result.size());
            fallback.add(ordered.size() - result.size());
            return result;
        } catch (IllegalStateException e) {
            readFailure();
            return Map.of();
        }
    }

    public JsonNode historySearch(Map<String, Object> query) {
        if (coolingDown()) return null;
        try {
            JsonNode result =
                    request(
                            "POST",
                            historyIndices() + "/_search?allow_partial_search_results=false",
                            encode(query),
                            false);
            if (!result.path("timed_out").isBoolean()
                    || result.path("timed_out").asBoolean()
                    || result.path("_shards").path("failed").asInt(-1) != 0) return null;
            reads.increment();
            return result;
        } catch (IllegalStateException e) {
            readFailure();
            return null;
        }
    }

    private boolean coolingDown() {
        return retryReadAfter != 0 && System.nanoTime() - retryReadAfter < 0;
    }

    private void readFailure() {
        fallback.increment();
        retryReadAfter = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    }

    public Map<String, Long> readMetrics() {
        return Map.of("esReads", reads.sum(), "mysqlFallbacks", fallback.sum());
    }

    private JsonNode request(String method, String path, String body, boolean ndjson) {
        var builder =
                HttpRequest.newBuilder(endpoint.resolve(path))
                        .timeout(Duration.ofSeconds(ndjson ? 15 : 4))
                        .header(
                                "Content-Type",
                                ndjson ? "application/x-ndjson" : "application/json");
        if (authorization != null) builder.header("Authorization", authorization);
        try {
            var response =
                    http.send(
                            builder.method(method, HttpRequest.BodyPublishers.ofString(body))
                                    .build(),
                            HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                if (response.statusCode() / 100 != 2)
                    throw new IllegalStateException("ES HTTP " + response.statusCode());
                JsonNode result = json.readTree(stream);
                if (result == null || !result.isObject())
                    throw new IllegalStateException("ES 响应格式无效");
                return result;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ES 调用中断", e);
        } catch (IOException e) {
            throw new IllegalStateException("ES 连接或响应读取失败", e);
        }
    }
}
