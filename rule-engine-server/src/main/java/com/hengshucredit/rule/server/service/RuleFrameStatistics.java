package com.hengshucredit.rule.server.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Uses complete frame/item frequencies. Names are the first non-null value in physical audit order.
 */
final class RuleFrameStatistics {
    private record Key(String kind, String rule, String item) {}

    private final Map<Key, Counter> counters = new LinkedHashMap<>();
    private final Map<String, Long> coverage = new LinkedHashMap<>();

    void add(String status, JsonNode frames, String filter) {
        coverage.merge(status, 1L, Long::sum);
        for (var frame : frames) {
            String code = frame.path("ruleCode").textValue();
            if (filter != null && !filter.isBlank() && !filter.equals(code)) continue;
            var key = new Key("RULE", frame.path("key").asText(), "");
            counters.computeIfAbsent(key, k -> new Counter()).add(frame);
            counters.computeIfAbsent(new Key("OVERVIEW", null, null), k -> new Counter())
                    .add(frame);
            for (var item : frame.path("items")) {
                var itemKey = new Key("ITEM", key.rule(), item.path("key").asText());
                counters.computeIfAbsent(itemKey, k -> new Counter()).add(item);
            }
        }
    }

    List<Map<String, Object>> rows() {
        List<Map<String, Object>> result = new ArrayList<>();
        counters.forEach((key, counter) -> result.add(counter.metrics(key)));
        coverage.forEach(
                (status, count) ->
                        result.add(
                                new LinkedHashMap<>(
                                        Map.of(
                                                "kind",
                                                "COVERAGE",
                                                "ruleCode",
                                                status,
                                                "evaluationCount",
                                                count))));
        return result;
    }

    private static final class Counter {
        private final TreeMap<Long, Long> costs = new TreeMap<>();
        private long count, hits, failures;
        private BigDecimal sum = BigDecimal.ZERO;
        private Long id;
        private String code, name;

        void add(JsonNode node) {
            count++;
            hits += node.path("hit").asLong();
            failures += node.path("failed").asLong();
            long cost = node.path("cost").asLong();
            sum = sum.add(BigDecimal.valueOf(cost));
            costs.merge(cost, 1L, Long::sum);
            if (node.path("ruleId").isIntegralNumber()) id = node.get("ruleId").longValue();
            if (code == null) code = node.path("ruleCode").textValue();
            if (name == null) name = node.path("ruleName").textValue();
        }

        Map<String, Object> metrics(Key key) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kind", key.kind());
            row.put("key", key.rule());
            row.put("ruleId", id);
            row.put("ruleCode", code);
            row.put("ruleName", name);
            row.put("evaluationCount", count);
            row.put("hitCount", hits);
            row.put("failureCount", failures);
            row.put("hitRate", (double) hits / count);
            row.put("failureRate", (double) failures / count);
            row.put(
                    "avgCostTimeMs",
                    sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP).doubleValue());
            row.put("p95CostTimeMs", percentile(95));
            row.put("p99CostTimeMs", percentile(99));
            return row;
        }

        long percentile(int pct) {
            long left = (count * pct + 99) / 100;
            for (var entry : costs.entrySet()) {
                left -= entry.getValue();
                if (left <= 0) return entry.getKey();
            }
            throw new IllegalStateException("规则集耗时频数不完整");
        }
    }
}
