package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.server.artifact.PublishedRuleFieldSnapshotResolver;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Proxy;
import static org.junit.Assert.*;

public class RuleCallVersionFieldsTest {
    @Test
    public void latestStructuredCallUsesPublishedFieldsEvenWhenAnotherDraftChangedProjection() {
        assertLatestFields("LATEST");
    }

    @Test
    public void implicitLatestCallUsesTheSamePublishedFieldPolicy() {
        assertLatestFields(null);
    }

    private void assertLatestFields(String mode) {
        RuleFieldAnalyzer analyzer = withDraftProjection();
        ReflectionTestUtils.setField(analyzer, "publishedFieldSnapshotResolver", new PublishedRuleFieldSnapshotResolver() {
            @Override public RuleFieldAnalyzer.ResolvedFields resolve(Long id) {
                assertEquals(Long.valueOf(22), id);
                return new RuleFieldAnalyzer.ResolvedFields(List.of(input("published_input")), List.of(output("published_output")));
            }
        });
        String model = "{\"actions\":[{\"type\":\"rule-call\",\"ruleId\":22"
                + (mode == null ? "" : ",\"versionMode\":\"" + mode + "\"") + "}]}";
        RuleFieldAnalyzer.ResolvedFields fields = ReflectionTestUtils.invokeMethod(analyzer, "resolveRuleCallFields", 30L, model);
        assertEquals("published_input", fields.getInputFields().get(0).getScriptName());
        assertEquals("published_output", fields.getOutputFields().get(0).getScriptName());
        assertTrue(fields.getDiagnostics().isEmpty());
    }

    @Test
    public void missingPublishedSnapshotMustNotBorrowEditableDraftFields() {
        RuleFieldAnalyzer analyzer = withDraftProjection();
        ReflectionTestUtils.setField(analyzer, "publishedFieldSnapshotResolver", new PublishedRuleFieldSnapshotResolver() {
            @Override public RuleFieldAnalyzer.ResolvedFields resolve(Long id) {
                return new RuleFieldAnalyzer.ResolvedFields(List.of(), List.of(), List.of(
                        RuleValidationIssue.error("FROZEN_REVISION_FIELD_SNAPSHOT_MISSING", "$.ruleFields", "RULE", id, "缺少已发布字段快照")), Set.of(), Map.of(), Map.of());
            }
        });
        RuleFieldAnalyzer.ResolvedFields fields = ReflectionTestUtils.invokeMethod(analyzer, "resolveRuleCallFields", 30L,
                "{\"actions\":[{\"type\":\"rule-call\",\"ruleId\":22,\"versionMode\":\"LATEST\"}]}");
        assertTrue(fields.getInputFields().isEmpty());
        assertTrue(fields.getOutputFields().isEmpty());
        assertEquals("FROZEN_REVISION_FIELD_SNAPSHOT_MISSING", fields.getDiagnostics().get(0).getCode());
    }

    private static RuleFieldAnalyzer withDraftProjection() {
        RuleFieldAnalyzer analyzer = new RuleFieldAnalyzer();
        ReflectionTestUtils.setField(analyzer, "inputFieldMapper", Proxy.newProxyInstance(
                RuleDefinitionInputFieldMapper.class.getClassLoader(), new Class<?>[]{RuleDefinitionInputFieldMapper.class},
                (proxy, method, args) -> "selectList".equals(method.getName()) ? List.of(input("unpublished_input")) : null));
        ReflectionTestUtils.setField(analyzer, "outputFieldMapper", Proxy.newProxyInstance(
                RuleDefinitionOutputFieldMapper.class.getClassLoader(), new Class<?>[]{RuleDefinitionOutputFieldMapper.class},
                (proxy, method, args) -> "selectList".equals(method.getName()) ? List.of(output("unpublished_output")) : null));
        return analyzer;
    }

    @Test
    public void inheritedSnapshotFieldsAreDetachedAndDoNotClaimParentOverrides() {
        RuleFieldAnalyzer analyzer = withDraftProjection();
        RuleDefinitionInputField input = input("published_input");
        input.setId(100L); input.setValidationRuleIds("[3]"); input.setValidationOverride(1);
        RuleDefinitionOutputField output = output("published_output"); output.setId(101L);
        ReflectionTestUtils.setField(analyzer, "publishedFieldSnapshotResolver", new PublishedRuleFieldSnapshotResolver() {
            @Override public RuleFieldAnalyzer.ResolvedFields resolve(Long id) {
                return new RuleFieldAnalyzer.ResolvedFields(List.of(input), List.of(output));
            }
        });
        RuleFieldAnalyzer.ResolvedFields fields = ReflectionTestUtils.invokeMethod(analyzer, "resolveRuleCallFields", 30L,
                "{\"actions\":[{\"type\":\"rule-call\",\"ruleId\":22}]}");
        RuleDefinitionInputField inherited = fields.getInputFields().get(0);
        assertNotSame(input, inherited);
        assertNull(inherited.getId()); assertNull(inherited.getDefinitionId());
        assertEquals("[3]", inherited.getValidationRuleIds());
        assertEquals(Integer.valueOf(0), inherited.getValidationOverride());
        assertEquals(Integer.valueOf(1), input.getValidationOverride());
        assertNotSame(output, fields.getOutputFields().get(0));
        assertNull(fields.getOutputFields().get(0).getId());
    }

    private static RuleDefinitionInputField input(String name) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setDefinitionId(22L); field.setVarId(5L); field.setRefType("VARIABLE"); field.setScriptName(name);
        return field;
    }

    private static RuleDefinitionOutputField output(String name) {
        RuleDefinitionOutputField field = new RuleDefinitionOutputField();
        field.setDefinitionId(22L); field.setVarId(6L); field.setRefType("VARIABLE"); field.setScriptName(name);
        return field;
    }

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
