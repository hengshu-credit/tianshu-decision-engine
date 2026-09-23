package com.hengshucredit.rule.server.derived;

import com.hengshucredit.rule.model.entity.RuleVariable;
import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class HistoricalFieldDefinitionTest {
    @Test
    public void reusableStructureKeepsOwnPathAndInputProvenance() {
        var field = new com.hengshucredit.rule.model.entity.RuleDataObjectField();
        field.setId(10L); field.setRefVariableId(1L); field.setReferenceMode("STRUCTURE");
        var variable = new RuleVariable(); variable.setId(1L); variable.setVarSource("DERIVED");
        Map<String, String> paths = new java.util.LinkedHashMap<>(Map.of("DATA_OBJECT:10", "request.amount", "VARIABLE:1", "remoteAmount"));
        HistoryFieldValues.applyAliases(paths, List.of(field));
        assertEquals("request.amount", paths.get("DATA_OBJECT:10"));
        var definition = HistoricalFieldDefinition.build(List.of(variable), List.of(field), List.of(), paths).get("DATA_OBJECT:10");
        assertEquals("INPUT", definition.source()); assertTrue(definition.queryable());
    }
    @Test
    public void nestedObjectFieldFollowsParentVariableById() {
        var parent = new com.hengshucredit.rule.model.entity.RuleDataObjectField();
        parent.setId(10L); parent.setRefVariableId(1L);
        var child = new com.hengshucredit.rule.model.entity.RuleDataObjectField();
        child.setId(11L); child.setParentFieldId(10L);
        Map<String, String> paths = new java.util.LinkedHashMap<>(Map.of("DATA_OBJECT:10", "request.contacts", "DATA_OBJECT:11", "request.contacts.phone", "VARIABLE:1", "contacts"));
        HistoryFieldValues.applyAliases(paths, List.of(child, parent));
        assertEquals("contacts.phone", paths.get("DATA_OBJECT:11"));
        assertEquals(List.of("123", "456"), HistoryFieldValues.read(Map.of("contacts", List.of(Map.of("phone", "123"), Map.of("phone", "456"))), paths.get("DATA_OBJECT:11")));
    }
    @Test
    public void defaultHistoryAllowsInputsAndApiButOtherSourcesRequireOptIn() {
        for (String source : List.of("INPUT", "API", "DB", "LIST", "DERIVED", "CONSTANT")) {
            RuleVariable field = new RuleVariable();
            field.setId(1L); field.setVarCode("v"); field.setVarSource(source);
            if ("API".equals(source)) field.setSourceConfig("{\"apiConfigId\":8,\"resultPath\":\"body.score\"}");
            String key = "CONSTANT".equals(source) ? "CONSTANT:1" : "VARIABLE:1";
            var definition = HistoricalFieldDefinition.build(List.of(field), List.of(), List.of(), Map.of(key, "v")).get(key);
            assertEquals(source, "INPUT".equals(source) || "API".equals(source), definition.queryable());
            field.setRecordResult(true);
            assertTrue(HistoricalFieldDefinition.build(List.of(field), List.of(), List.of(), Map.of(key, "v")).get(key).queryable());
        }
    }
}
