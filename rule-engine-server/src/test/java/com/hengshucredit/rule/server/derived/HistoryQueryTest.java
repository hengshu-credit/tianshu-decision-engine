package com.hengshucredit.rule.server.derived;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class HistoryQueryTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 12, 0);

    @Test
    public void countsApplicationsAndCompositeSubjectsWithoutStringKeyCollisions() {
        List<HistoryQuery.Row> rows = List.of(
                row(1, Map.of("VARIABLE:1", "a|b", "VARIABLE:2", "c")),
                row(2, Map.of("VARIABLE:1", "a", "VARIABLE:2", "b|c")),
                row(3, Map.of("VARIABLE:1", "a|b", "VARIABLE:2", "c")),
                row(4, Map.of("VARIABLE:1", "")));
        JSONObject query = query("COUNT");
        assertEquals(4L, evaluate(query, rows));
        query.put("aggregate", "DISTINCT_COUNT");
        query.put("subjectFields", List.of(ref(1), ref(2)));
        assertEquals(2L, evaluate(query, rows));
    }

    @Test
    public void followsContactsToApplicantsAndThenTheirOtherApplicationsWithoutJoinMultiplication() {
        List<HistoryQuery.Row> rows = List.of(
                row(1, Map.of("VARIABLE:1", "person-a", "VARIABLE:2", List.of("phone-x", "phone-y"), "VARIABLE:3", 10)),
                row(2, Map.of("VARIABLE:1", "person-a", "VARIABLE:2", List.of("other"), "VARIABLE:3", 20)),
                row(3, Map.of("VARIABLE:1", "person-b", "VARIABLE:2", List.of("phone-x"), "VARIABLE:3", 30)),
                row(4, Map.of("VARIABLE:1", "person-c", "VARIABLE:2", List.of("unrelated"), "VARIABLE:3", 500)));
        JSONObject query = query("SUM");
        query.put("valueField", ref(3));
        query.put("steps", JSON.parseArray("[{\"inputs\":[{\"kind\":\"LITERAL\",\"valueType\":\"LIST\",\"value\":[\"phone-x\",\"phone-y\"]}],\"fields\":[" + ref(2) + "]},"
                + "{\"fromFields\":[" + ref(1) + "],\"fields\":[" + ref(1) + "]}]"));
        assertEquals(new BigDecimal("60"), evaluate(query, rows));
        query.put("aggregate", "DISTINCT_COUNT");
        query.put("subjectFields", List.of(ref(1)));
        assertEquals(2L, evaluate(query, rows));
    }

    @Test
    public void emptyContactDoesNotMatchOtherEmptyContacts() {
        JSONObject query = query("COUNT");
        query.put("steps", JSON.parseArray("[{\"inputs\":[{\"kind\":\"LITERAL\",\"valueType\":\"STRING\",\"value\":\"\"}],\"fields\":[" + ref(1) + "]}]"));
        assertEquals(0L, evaluate(query, List.of(row(1, Map.of("VARIABLE:1", "")))));
    }

    @Test
    public void populationAndSampleStatisticsHaveDifferentDenominators() {
        List<HistoryQuery.Row> rows = List.of(row(1, Map.of("VARIABLE:1", 2)), row(2, Map.of("VARIABLE:1", 4)), row(3, Map.of("VARIABLE:1", 6)));
        JSONObject query = query("VARIANCE");
        query.put("valueField", ref(1));
        assertEquals(8d / 3, ((Number) evaluate(query, rows)).doubleValue(), 1e-12);
        query.put("aggregate", "SAMPLE_VARIANCE");
        assertEquals(4d, ((Number) evaluate(query, rows)).doubleValue(), 1e-12);
        query.put("aggregate", "SAMPLE_STDDEV");
        assertEquals(2d, ((Number) evaluate(query, rows)).doubleValue(), 1e-12);
        assertNull(evaluate(query, rows.subList(0, 1)));
        query.put("aggregate", "AVG");
        assertNull(evaluate(query, List.of()));
        query.put("aggregate", "SUM");
        assertEquals(BigDecimal.ZERO, evaluate(query, List.of()));
    }

    @Test
    public void stringLengthCountsUnicodeCodePointsAndNumericOperationsRejectStrings() {
        JSONObject query = query("STRING_LENGTH");
        query.put("valueField", ref(1));
        List<HistoryQuery.Row> rows = List.of(row(1, Map.of("VARIABLE:1", "人😀")));
        assertEquals(2L, evaluate(query, rows));
        query.put("aggregate", "SUM");
        assertThrows(IllegalArgumentException.class, () -> evaluate(query, rows));
    }

    @Test
    public void latestSubjectRecordIsDeterministicAndCustomAggregationReceivesValues() {
        JSONObject query = query("CUSTOM");
        query.put("valueField", ref(2));
        query.put("subjectFields", List.of(ref(1)));
        query.put("recordMode", "LATEST_PER_SUBJECT");
        query.put("functionId", 9L);
        Object result = HistoryQuery.evaluate(query, List.of(
                row(1, Map.of("VARIABLE:1", "a", "VARIABLE:2", 10)),
                row(2, Map.of("VARIABLE:1", "a", "VARIABLE:2", 20))),
                operand -> operand.get("value"), (id, code, args) -> {
                    assertEquals(Long.valueOf(9), id);
                    assertEquals(List.of(List.of(20)), args);
                    return 99;
                });
        assertEquals(99, result);
    }

    @Test
    public void onlyCurrentRequestOperandsBecomeApiInputs() {
        JSONObject config = query("SUM");
        config.put("mode", "HISTORY");
        config.put("valueField", ref(8));
        config.put("subjectFields", List.of(ref(9)));
        config.put("steps", JSON.parseArray("[{\"inputs\":[" + ref(1) + "],\"fields\":[" + ref(2) + "]},"
                + "{\"fromFields\":[" + ref(3) + "],\"fields\":[" + ref(4) + "]}]"));
        assertEquals(List.of(ref(1)), DerivedVariableConfig.currentInputs(config));
    }

    @Test
    public void invalidWindowAndCoordinatesFailEvenWhenHistoryIsEmpty() {
        JSONObject config = query("COUNT");
        config.put("window", 1.5);
        assertThrows(IllegalArgumentException.class, () -> evaluate(config, List.of()));
        config.put("window", 30);
        config.put("geo", JSON.parseObject("{\"longitudeField\":" + ref(1) + ",\"latitudeField\":" + ref(2)
                + ",\"longitude\":{\"kind\":\"LITERAL\",\"value\":181},\"latitude\":{\"kind\":\"LITERAL\",\"value\":0},\"radiusMeters\":{\"kind\":\"LITERAL\",\"value\":1000}}"));
        assertThrows(IllegalArgumentException.class, () -> evaluate(config, List.of()));
        config.getJSONObject("geo").getJSONObject("longitude").put("value", 0);
        assertEquals(1L, evaluate(config, List.of(row(1, Map.of("VARIABLE:1", 0, "VARIABLE:2", 0)), row(2, Map.of("VARIABLE:1", 10, "VARIABLE:2", 10)))));
    }

    private static Object evaluate(JSONObject query, List<HistoryQuery.Row> rows) {
        return HistoryQuery.evaluate(query, rows, operand -> operand.get("value"), null);
    }

    private static JSONObject query(String aggregate) {
        return JSON.parseObject("{\"mode\":\"HISTORY\",\"scope\":\"PROJECT\",\"window\":30,\"windowUnit\":\"DAY\",\"aggregate\":\"" + aggregate + "\"}");
    }

    private static JSONObject ref(long id) {
        return JSON.parseObject("{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":" + id + "}");
    }

    private static HistoryQuery.Row row(long id, Map<String, Object> fields) {
        return new HistoryQuery.Row(id, NOW, fields);
    }
}
