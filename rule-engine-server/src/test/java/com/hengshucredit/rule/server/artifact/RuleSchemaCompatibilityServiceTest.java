package com.hengshucredit.rule.server.artifact;

import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RuleSchemaCompatibilityServiceTest {

    private final RuleSchemaCompatibilityService service = new RuleSchemaCompatibilityService();

    @Test
    public void schemaKeepsFieldNameExactlyAsEntered() {
        RuleDefinitionInputField input = new RuleDefinitionInputField();
        input.setFieldName("Customer_ID");
        input.setFieldType("STRING");
        input.setStatus(1);

        RuleDefinitionOutputField output = new RuleDefinitionOutputField();
        output.setFieldName("RiskScore_V2");
        output.setFieldType("DOUBLE");
        output.setStatus(1);

        RuleSchemaService.SchemaSnapshot snapshot = new RuleSchemaService()
                .build(List.of(input), List.of(output));

        Assert.assertTrue(properties(snapshot.getInputSchema()).containsKey("Customer_ID"));
        Assert.assertFalse(properties(snapshot.getInputSchema()).containsKey("customerId"));
        Assert.assertTrue(properties(snapshot.getOutputSchema()).containsKey("RiskScore_V2"));
        Assert.assertFalse(properties(snapshot.getOutputSchema()).containsKey("riskScoreV2"));
    }

    @Test
    public void caseOnlyRenameIsBreakingInsteadOfBeingNormalized() {
        Map<String, Object> previousInput = schema(properties("Customer_ID", "string"), "Customer_ID");
        Map<String, Object> currentInput = schema(properties("customer_id", "string"), "customer_id");

        RuleSchemaCompatibilityService.CompatibilityReport report = service.compare(
                previousInput, emptySchema(), currentInput, emptySchema());

        Assert.assertTrue(report.hasBreakingChanges());
        Assert.assertTrue(report.getChanges().stream()
                .anyMatch(change -> "INPUT_REMOVED".equals(change.getChangeType())
                        && "Customer_ID".equals(change.getFieldName())));
    }

    @Test
    public void optionalInputAdditionIsCompatibleButRequiredAdditionIsBreaking() {
        Map<String, Object> previousInput = schema(properties("age", "integer"), "age");
        Map<String, Object> optionalInput = schema(properties(
                "age", "integer", "channel", "string"), "age");
        Map<String, Object> requiredInput = schema(properties(
                "age", "integer", "channel", "string"), "age", "channel");

        Assert.assertFalse(service.compare(previousInput, emptySchema(), optionalInput, emptySchema())
                .hasBreakingChanges());
        RuleSchemaCompatibilityService.CompatibilityReport report = service.compare(
                previousInput, emptySchema(), requiredInput, emptySchema());
        Assert.assertTrue(report.hasBreakingChanges());
        Assert.assertTrue(report.getChanges().stream()
                .anyMatch(change -> "REQUIRED_INPUT_ADDED".equals(change.getChangeType())));
    }

    @Test
    public void outputRemovalAndTypeChangeAreBreaking() {
        Map<String, Object> previousOutput = schema(properties(
                "score", "number", "decision", "string"));
        Map<String, Object> currentOutput = schema(properties("score", "string"));

        RuleSchemaCompatibilityService.CompatibilityReport report = service.compare(
                emptySchema(), previousOutput, emptySchema(), currentOutput);

        Assert.assertEquals(2L, report.getChanges().stream()
                .filter(RuleSchemaCompatibilityService.SchemaChange::isBreaking).count());
        Assert.assertTrue(report.getChanges().stream()
                .anyMatch(change -> "OUTPUT_REMOVED".equals(change.getChangeType())));
        Assert.assertTrue(report.getChanges().stream()
                .anyMatch(change -> "OUTPUT_TYPE_CHANGED".equals(change.getChangeType())));
    }

    @Test
    public void sameJsonTypeWithDifferentFormatOrConstraintsIsBreaking() {
        Map<String, Object> previousProperty = new java.util.LinkedHashMap<>();
        previousProperty.put("type", "string");
        previousProperty.put("format", "date");
        previousProperty.put("enum", List.of("A", "B"));
        Map<String, Object> currentProperty = new java.util.LinkedHashMap<>();
        currentProperty.put("type", "string");
        currentProperty.put("format", "date-time");
        currentProperty.put("enum", List.of("A"));
        Map<String, Object> previous = schema(Map.of("eventAt", previousProperty), "eventAt");
        Map<String, Object> current = schema(Map.of("eventAt", currentProperty), "eventAt");

        RuleSchemaCompatibilityService.CompatibilityReport report = service.compare(
                previous, emptySchema(), current, emptySchema());

        Assert.assertTrue(report.hasBreakingChanges());
        Assert.assertTrue(report.getChanges().stream()
                .anyMatch(change -> "INPUT_FORMAT_CHANGED".equals(change.getChangeType())));
        Assert.assertTrue(report.getChanges().stream()
                .anyMatch(change -> "INPUT_CONSTRAINT_CHANGED".equals(change.getChangeType())));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> schema) {
        return (Map<String, Object>) schema.get("properties");
    }

    private static Map<String, Object> emptySchema() {
        return schema(Collections.emptyMap());
    }

    private static Map<String, Object> schema(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of(required));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> properties(String... pairs) {
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            properties.put(pairs[index], Map.of("type", pairs[index + 1]));
        }
        return properties;
    }
}
