package com.hengshucredit.rule.server.derived;

import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.server.service.VariableResolutionInvocationCache;
import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class HistoryLogSnapshotTest {
    @Test
    public void originalObjectLeavesRemainInputsEvenWhenSchemaReusesApiVariables() {
        var cache = new VariableResolutionInvocationCache();
        Map<String, Object> child = new java.util.LinkedHashMap<>(); child.put("zero", 0); child.put("empty", null);
        cache.registerObjectInputs(Map.of("DATA_OBJECT:20", "request.zero", "DATA_OBJECT:21", "request.empty"), Map.of("request", child));
        cache.registerHistoryFields(Map.of("DATA_OBJECT:20", new HistoricalFieldDefinition("DATA_OBJECT:20", "remote", "API", false, null, 7L, "body")));
        var snapshot = cache.historySnapshot(List.of(), Map.of("request", child), Map.of("request", Map.of("zero", 999)));
        Map<String, Object> expected = new java.util.LinkedHashMap<>(); expected.put("DATA_OBJECT:20", 0); expected.put("DATA_OBJECT:21", null);
        assertEquals(expected, snapshot.get("fields"));
    }
    @Test
    @SuppressWarnings("unchecked")
    public void optedInObjectFieldUsesItsResolvedSourceSnapshotNotCallerOrLaterMutation() {
        var cache = new VariableResolutionInvocationCache();
        cache.registerHistoryFields(Map.of(
                "VARIABLE:8", new HistoricalFieldDefinition("VARIABLE:8", "dbResult", "DB", false, "VARIABLE:8", null, null),
                "DATA_OBJECT:9", new HistoricalFieldDefinition("DATA_OBJECT:9", "dbResult.amount", "DB", true, "VARIABLE:8", null, null),
                "DATA_OBJECT:10", new HistoricalFieldDefinition("DATA_OBJECT:10", "dbResult.other", "DB", false, "VARIABLE:8", null, null)));
        cache.rememberFieldResult("VARIABLE:8", Map.of("amount", 80, "other", 10));
        var snapshot = cache.historySnapshot(List.of(), Map.of("dbResult", Map.of("amount", 999)), Map.of("dbResult", Map.of("amount", 888)));
        assertEquals(Map.of("DATA_OBJECT:9", 80), snapshot.get("fields"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void capturesRequestIdentityAndOnlyOptedInComputedResults() {
        var cache = new VariableResolutionInvocationCache();
        cache.registerHistoryDefaults(Map.of("VARIABLE:5", new HistoricalFieldDefinition("VARIABLE:5", "extraInput", "INPUT", false, "VARIABLE:5", null, null)));
        cache.registerHistoryFields(Map.of(
                "VARIABLE:1", new HistoricalFieldDefinition("VARIABLE:1", "amount", "INPUT", true, "VARIABLE:1", null, null),
                "VARIABLE:2", new HistoricalFieldDefinition("VARIABLE:2", "derived", "DERIVED", false, null, null, null),
                "VARIABLE:3", new HistoricalFieldDefinition("VARIABLE:3", "selected", "DERIVED", true, null, null, null),
                "VARIABLE:4", new HistoricalFieldDefinition("VARIABLE:4", "api", "API", false, null, 7L, "body.score")));
        cache.rememberFieldResult("VARIABLE:2", 200);
        cache.rememberFieldResult("VARIABLE:3", 300);
        cache.resolve("API:7", () -> Map.of("body", Map.of("score", 400)));
        RuleDefinitionInputField input = new RuleDefinitionInputField();
        input.setVarId(1L); input.setRefType("VARIABLE"); input.setScriptName("amount");
        var snapshot = cache.historySnapshot(List.of(input), Map.of("amount", 10, "extraInput", 20), Map.of("amount", 999, "api", 400));
        Map<String, Object> fields = (Map<String, Object>) snapshot.get("fields");
        assertEquals(Map.of("VARIABLE:1", 10, "VARIABLE:3", 300, "VARIABLE:5", 20), fields);
        assertEquals(List.of(7L), snapshot.get("apiIds"));
    }
}
