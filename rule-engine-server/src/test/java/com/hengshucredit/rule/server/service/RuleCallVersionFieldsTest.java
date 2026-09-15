package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.server.artifact.PublishedRuleFieldSnapshotResolver;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.Assert.*;

public class RuleCallVersionFieldsTest {
    @Test
    public void fixedCallUsesSelectedVersionFieldsNotMutableDefinitionProjection() {
        RuleFieldAnalyzer analyzer = new RuleFieldAnalyzer();
        ReflectionTestUtils.setField(analyzer, "publishedFieldSnapshotResolver", new PublishedRuleFieldSnapshotResolver() {
            @Override public RuleFieldAnalyzer.ResolvedFields resolve(Long id, Long binding) {
                assertEquals(Long.valueOf(22), id); assertEquals(Long.valueOf(81), binding);
                RuleDefinitionInputField input = new RuleDefinitionInputField(); input.setVarId(5L);
                input.setRefType("VARIABLE"); input.setScriptName("historical_input");
                return new RuleFieldAnalyzer.ResolvedFields(List.of(input), List.of());
            }
        });
        RuleFieldAnalyzer.ResolvedFields fields = ReflectionTestUtils.invokeMethod(analyzer, "resolveRuleCallFields", 30L,
                "{\"actions\":[{\"type\":\"rule-call\",\"ruleId\":22,\"versionMode\":\"FIXED\",\"versionBindingId\":81}]}");
        assertEquals(1, fields.getInputFields().size());
        assertEquals("historical_input", fields.getInputFields().get(0).getScriptName());
        assertEquals(Long.valueOf(5), fields.getInputFields().get(0).getVarId());
    }
}
