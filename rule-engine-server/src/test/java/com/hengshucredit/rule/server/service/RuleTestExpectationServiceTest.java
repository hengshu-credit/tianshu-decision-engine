package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ResolvedField;
import com.hengshucredit.rule.model.dto.TestExpectationResult;
import org.junit.Test;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class RuleTestExpectationServiceTest {
    private final RuleTestExpectationService service = new RuleTestExpectationService();

    @Test
    public void legacyDeepDebugTracesDoNotChangeTheBusinessSnapshotComparison() {
        String trace = nestedJson(160, "{\"debugOnly\":true}");
        String business = "{\"amount\":9007199254740993,\"traces\":[\"business-value\"]}";
        for (boolean success : List.of(true, false)) {
            String error = success ? "null" : "\"业务拒绝\"";
            String historical = "{\"data\":{\"success\":" + success + ",\"result\":" + business
                    + ",\"errorMessage\":" + error + ",\"traces\":[" + trace + "]}}";
            String untouched = historical;
            var execution = new com.hengshucredit.rule.model.dto.RuleResult();
            execution.setSuccess(success);
            execution.setResult(Map.of("amount", new BigInteger("9007199254740993"), "traces", List.of("business-value")));
            execution.setErrorMessage(success ? null : "业务拒绝");
            String current = service.writeExecutionSnapshot(execution);

            assertEquals("PASSED", service.compareSnapshots(historical, current).getStatus());
            execution.setSuccess(!success);
            assertEquals("FAILED", service.compareSnapshots(historical, service.writeExecutionSnapshot(execution)).getStatus());
            execution.setSuccess(success);
            execution.setErrorMessage("不同错误");
            assertEquals("FAILED", service.compareSnapshots(historical, service.writeExecutionSnapshot(execution)).getStatus());
            assertEquals(untouched, historical);
            assertTrue("历史原文保留调试树，不在比较时迁移", historical.contains(trace));
        }
    }

    @Test
    public void selectedBusinessValuesHaveTheirOwn128LevelBudgetWithoutCountingTheEnvelope() {
        String depth128 = nestedJson(128, "1");
        assertEquals("PASSED", service.compareSnapshots(response(depth128), response(depth128)).getStatus());
        assertEquals("INVALID", service.compareSnapshots(response(nestedJson(129, "1")),
                response(nestedJson(129, "1"))).getStatus());
    }

    @Test
    public void missingOrDifferentBusinessShapesCannotBypassTheSnapshotDepthBudget() {
        String deep = response(nestedJson(129, "1"));
        for (String shallow : List.of("{\"data\":{\"success\":true}}", response("null"), response("{}"), response("1"))) {
            var expectedDeep = service.compareSnapshots(deep, shallow);
            var actualDeep = service.compareSnapshots(shallow, deep);
            assertEquals("深预期与浅实际的早期形状差异也必须检查深度", "INVALID", expectedDeep.getStatus());
            assertEquals("浅预期与深实际的早期形状差异也必须检查深度", "INVALID", actualDeep.getStatus());
        }
    }

    @Test
    public void businessFieldsNamedTracesAreComparedAndCannotEscapeTheDepthLimit() {
        var mismatch = service.compareSnapshots(response("{\"traces\":[1]}"), response("{\"traces\":[2]}"));
        assertEquals("FAILED", mismatch.getStatus());
        assertEquals("$.result.traces[0]", mismatch.getDiffs().get(0).path());
        String deepBusinessTrace = response("{\"traces\":" + nestedJson(129, "1") + "}");
        assertEquals("INVALID", service.compareSnapshots(deepBusinessTrace, response("{}")).getStatus());
    }

    @Test
    public void ignoredLegacyDebugContentStillMustBeStrictValidJsonWithoutDuplicateKeys() {
        for (String malformed : List.of("{\"x\":1,\"x\":2}", "[1,]", "{broken}")) {
            String legacy = "{\"data\":{\"success\":true,\"result\":88,\"traces\":["
                    + nestedJson(160, malformed) + "]}}";
            assertEquals("INVALID", service.compareSnapshots(legacy, response("88")).getStatus());
            assertEquals("INVALID", service.compareSnapshots(response("88"), legacy).getStatus());
        }
    }

    @Test
    public void snapshotCompatibilityDoesNotRelaxTheExistingJsonReaderLimitsForInputOrConfig() {
        String overReaderBudget = nestedJson(1100, "1");
        assertThrows(IllegalArgumentException.class, () -> service.requestParams("{\"params\":" + overReaderBudget + "}"));
        assertThrows(IllegalArgumentException.class, () -> service.validateConfiguration(
                "{\"version\":1,\"mode\":\"NONE\",\"extra\":" + overReaderBudget + "}"));
        assertThrows(IllegalArgumentException.class, () -> service.readJsonObject(overReaderBudget, "字段结构"));
    }

    private static String nestedJson(int depth, String leaf) {
        return "{\"next\":".repeat(depth) + leaf + "}".repeat(depth);
    }

    @Test
    public void adjacentLargeIntegersAndLongDecimalsDoNotBecomeEqual() {
        var integer = service.compareSnapshots(response("9007199254740992"), response("9007199254740993"));
        assertEquals("FAILED", integer.getStatus());
        assertEquals("9007199254740992", integer.getDiffs().get(0).expectedJson());
        assertEquals("9007199254740993", integer.getDiffs().get(0).actualJson());
        assertEquals("FAILED", service.compareSnapshots(response("0.123456789012345678901"), response("0.123456789012345678902")).getStatus());
        assertEquals("PASSED", service.compareSnapshots(response("1.00"), response("1e0")).getStatus());
    }

    @Test
    public void nestedObjectKeyOrderIsIgnoredButArrayOrderIsNot() {
        assertEquals("PASSED", service.compareSnapshots(response("[{\"a\":1,\"b\":2}]"), response("[{\"b\":2,\"a\":1}]")).getStatus());
        var changed = service.compareSnapshots(response("[1,2]"), response("[2,1]"));
        assertEquals("FAILED", changed.getStatus());
        assertEquals("$.result[0]", changed.getDiffs().get(0).path());
    }

    @Test
    public void missingAndExplicitNullAreDifferentAndReportedWithoutRounding() {
        var changed = service.compareSnapshots(response("{\"value\":null}"), response("{}"));
        assertEquals("FAILED", changed.getStatus());
        assertEquals("null", changed.getDiffs().get(0).expectedJson());
        assertFalse(changed.getDiffs().get(0).expectedMissing());
        assertTrue(changed.getDiffs().get(0).actualMissing());
    }

    @Test
    public void emptyLegacySnapshotAndExplicitNoneAreNotPassingAssertions() {
        for (String expected : new String[]{null, "", "{}", "{\"code\":200,\"data\":{}}"}) {
            assertEquals("NOT_CONFIGURED", service.compareSnapshots(expected, response("5")).getStatus());
        }
        assertEquals("NOT_CONFIGURED", service.evaluate("{\"version\":1,\"mode\":\"NONE\"}", response("5"), response("5"), List.of()).getStatus());
        assertEquals("NOT_CONFIGURED", service.evaluate("{\"version\":1,\"mode\":\"FIELDS\",\"checks\":[]}", null, response("5"), List.of()).getStatus());
    }

    @Test
    public void preciseFieldBoundsAndCollectionMembersAreComparedByValue() {
        String config = fieldConfig("between", literal("9007199254740992,9007199254740993", "NUMBER"));
        assertEquals("PASSED", evaluate(config, "{\"amount\":9007199254740993}").getStatus());
        assertEquals("FAILED", evaluate(config, "{\"amount\":9007199254740994}").getStatus());
        Map<String, Object> collection = Map.of("kind", "ARRAY", "items", List.of(Map.of("kind", "CAST", "targetType", "NUMBER",
                "operand", literal("9007199254740993", "STRING"))));
        assertEquals("PASSED", evaluate(fieldConfig("in", collection), "{\"amount\":9007199254740993}").getStatus());
        assertEquals("FAILED", evaluate(fieldConfig("in", collection), "{\"amount\":9007199254740992}").getStatus());
        assertEquals("FAILED", evaluate(fieldConfig("==", literal("5", "NUMBER")), "{\"amount\":\"5\"}").getStatus());
    }

    @Test
    public void fieldIdentitySurvivesRenameButMissingAmbiguousAndChangedTypesAreInvalid() {
        String config = fieldConfig("==", literal("5", "NUMBER"));
        ResolvedField renamed = field(); renamed.setScriptName("renamedAmount"); renamed.setCode("renamedAmount");
        assertEquals("PASSED", service.evaluate(config, null, response("{\"renamedAmount\":5}"), List.of(renamed)).getStatus());
        assertEquals("INVALID", service.evaluate(config, null, response("{\"amount\":5}"), List.of()).getStatus());
        assertEquals("INVALID", service.evaluate(config, null, response("{\"amount\":5}"), List.of(field(), field())).getStatus());
        renamed.setValueType("STRING");
        assertTrue(service.evaluate(config, null, response("{\"renamedAmount\":5}"), List.of(renamed)).getMessage().contains("类型已变化"));
    }

    @Test
    public void outputNullAndMissingHaveExplicitOperators() {
        assertEquals("PASSED", evaluate(fieldConfig("is_null", Map.of()), "{\"amount\":null}").getStatus());
        assertEquals("FAILED", evaluate(fieldConfig("is_null", Map.of()), "{}").getStatus());
        assertEquals("PASSED", evaluate(fieldConfig("is_missing", Map.of()), "{}").getStatus());
        assertEquals("PASSED", evaluate(fieldConfig("exists", Map.of()), "{\"amount\":null}").getStatus());
    }

    @Test
    public void executionFailureIsIndependentFromValidationStatus() {
        String failed = "{\"success\":false,\"result\":null,\"errorMessage\":\"业务拒绝\"}";
        assertEquals("NOT_EVALUATED", service.evaluate(fieldConfig("==", literal("5", "NUMBER")), null, failed, List.of(field())).getStatus());
        assertEquals("PASSED", service.compareSnapshots(failed, failed).getStatus());
    }

    @Test
    public void specialBusinessKeysAndAllNumericTokensSurviveRequestParsingAndWriting() {
        var params = service.requestParams("{\"params\":{\"@type\":\"customer\",\"$ref\":\"$.other\",\"__proto__\":\"business\","
                + "\"value\":9007199254740993,\"decimal\":0.123456789012345678901,\"nil\":null}}");
        assertEquals("customer", params.get("@type"));
        assertEquals("$.other", params.get("$ref"));
        assertEquals("business", params.get("__proto__"));
        assertEquals(new BigInteger("9007199254740993"), params.get("value"));
        assertEquals(new BigDecimal("0.123456789012345678901"), params.get("decimal"));
        assertTrue(params.containsKey("nil"));
        String raw = service.writeJson(params);
        assertTrue(raw.contains("9007199254740993")); assertTrue(raw.contains("0.123456789012345678901"));
        assertTrue(raw.contains("\"$ref\":\"$.other\""));
        assertEquals("PASSED", service.compareSnapshots(response(raw), response(raw)).getStatus());
    }

    @Test
    public void repeatedObjectsAreWrittenAsBusinessValuesRatherThanRefMarkers() {
        var shared = Map.of("value", new BigInteger("9007199254740993"));
        String raw = service.writeJson(List.of(shared, shared));
        assertEquals("[{\"value\":9007199254740993},{\"value\":9007199254740993}]", raw);
    }

    @Test
    public void malformedOrDuplicateJsonIsInvalidInsteadOfPassing() {
        assertEquals("INVALID", service.compareSnapshots("{\"result\":1,\"result\":2}", response("2")).getStatus());
        assertThrows(IllegalArgumentException.class, () -> service.requestParams("{\"params\":{\"n\":1,\"n\":1}}"));
        assertThrows(IllegalArgumentException.class, () -> service.requestParams("{\"params\":null}"));
    }

    @Test
    public void trailingRootValuesAreRejectedInsteadOfIgnoringPartOfTheBusinessJson() {
        assertThrows(IllegalArgumentException.class, () -> service.requestParams("{\"params\":{\"n\":1}} {\"params\":{\"n\":2}}"));
        assertEquals("INVALID", service.compareSnapshots(response("1") + " true", response("1")).getStatus());
        assertThrows(IllegalArgumentException.class, () -> service.validateConfiguration("{\"version\":1,\"mode\":\"NONE\"} []"));
    }

    @Test
    public void expressionsReferencesAndUnsupportedTypeOperatorsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.validateConfiguration(fieldConfig("==", Map.of("kind", "REFERENCE", "refId", 88))));
        assertThrows(IllegalArgumentException.class, () -> service.validateConfiguration(fieldConfig("==", literal("1 + 2", "NUMBER"))));
        assertThrows(IllegalArgumentException.class, () -> service.validateConfiguration(fieldConfig("between", literal("5,", "NUMBER"))));
        assertThrows(IllegalArgumentException.class, () -> service.validateConfiguration(fieldConfig("not_contains", literal("x", "STRING"))));
    }

    @Test
    public void scalarTypeMismatchCannotMakeNotEqualAccidentallyPass() {
        String wrongExpectedType = fieldConfig("!=", literal("5", "STRING"));
        assertThrows(IllegalArgumentException.class, () -> service.validateConfiguration(wrongExpectedType));
        assertEquals("INVALID", evaluate(wrongExpectedType, "{\"amount\":5}").getStatus());
        assertEquals("FAILED", evaluate(fieldConfig("!=", literal("2", "NUMBER")), "{\"amount\":\"3\"}").getStatus());
    }

    @Test
    public void dateFieldEqualityUsesDatesWhileSnapshotStringComparisonRemainsLiteral() {
        ResolvedField field = field(); field.setValueType("DATE");
        String config = service.writeJson(Map.of("version", 1, "mode", "FIELDS", "checks", List.of(Map.of(
                "refId", "19", "refType", "VARIABLE", "valueType", "DATE", "operator", "==", "expected", literal("2026/01/01", "DATE")))));
        assertEquals("PASSED", service.evaluate(config, null, response("{\"amount\":\"2026-01-01\"}"), List.of(field)).getStatus());
        assertEquals("FAILED", service.compareSnapshots(response("\"2026/01/01\""), response("\"2026-01-01\"")).getStatus());
    }

    @Test
    public void negativeComparisonsRequireApplicableActualTypesAndDoNotTreatNullAsValues() {
        Object[][] cases = {
                {"STRING", "not_contains", literal("a", "STRING"), "{}"},
                {"STRING", "!=", literal("a", "STRING"), "1"},
                {"NUMBER", "not_in", Map.of("kind", "ARRAY", "items", List.of(literal("1", "NUMBER"))), "\"1\""},
                {"NUMBER", "not_between", literal("1,2", "NUMBER"), "\"3\""},
                {"BOOLEAN", "!=", literal(true, "BOOLEAN"), "\"false\""},
                {"LIST", "not_contains", literal("a", "STRING"), "\"b\""},
                {"OBJECT", "!=", literal("{}", "OBJECT"), "[]"},
                {"DATE", "not_between", literal("2026-01-01,2026-01-02", "DATE"), "\"not-a-date\""}
        };
        for (Object[] entry : cases) {
            ResolvedField output = field(); output.setValueType((String) entry[0]);
            String config = config(output, (String) entry[1], entry[2]);
            assertEquals(entry[0] + " " + entry[1], "FAILED",
                    service.evaluate(config, null, response("{\"amount\":" + entry[3] + "}"), List.of(output)).getStatus());
            assertEquals("缺失不能被负向比较判通过", "FAILED", service.evaluate(config, null, response("{}"), List.of(output)).getStatus());
            assertEquals("null不能被负向比较判通过", "FAILED", service.evaluate(config, null, response("{\"amount\":null}"), List.of(output)).getStatus());
        }
        ResolvedField string = field(); string.setValueType("STRING");
        assertEquals("INVALID", service.evaluate(config(string, "not_contains", literal(1, "NUMBER")), null,
                response("{\"amount\":\"abc\"}"), List.of(string)).getStatus());
        Map<String, Object> nullLiteral = new java.util.LinkedHashMap<>(literal("", "NUMBER")); nullLiteral.put("value", null);
        assertEquals("PASSED", evaluate(fieldConfig("==", nullLiteral), "{\"amount\":null}").getStatus());
        assertEquals("FAILED", evaluate(fieldConfig("!=", nullLiteral), "{\"amount\":null}").getStatus());
        assertEquals("PASSED", evaluate(fieldConfig("!=", nullLiteral), "{\"amount\":1}").getStatus());
        assertEquals("PASSED", evaluate(fieldConfig("exists", Map.of()), "{\"amount\":{}}").getStatus());
        assertEquals("PASSED", evaluate(fieldConfig("not_null", Map.of()), "{\"amount\":{}}").getStatus());
    }

    @Test
    public void fieldDatesAcceptTheRealZonedJsonWrittenByTheExecutionSerializer() {
        var instant = java.time.Instant.parse("2026-01-01T00:00:00.123Z");
        ResolvedField output = field(); output.setValueType("DATETIME");
        for (Object value : List.of(java.util.Date.from(instant), instant, instant.atOffset(java.time.ZoneOffset.ofHours(8)))) {
            String actual = response(service.writeJson(Map.of("amount", value)));
            assertEquals(actual, "PASSED", service.evaluate(config(output, "==", literal("2026-01-01T08:00:00.123+08:00", "DATETIME")), null, actual, List.of(output)).getStatus());
            assertEquals(actual, "PASSED", service.evaluate(config(output, "between", literal("2026-01-01T00:00:00Z,2026-01-01T00:00:01Z", "DATETIME")), null, actual, List.of(output)).getStatus());
            assertEquals(actual, "FAILED", service.evaluate(config(output, "==", literal("2026-01-01T00:00:00.124Z", "DATETIME")), null, actual, List.of(output)).getStatus());
        }
        // 无时区输入继续按既有SimpleDateFormat/JVM默认时区解释，不改为UTC。
        var parser = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            String actual = response(service.writeJson(Map.of("amount", parser.parse("2026-01-01 08:00:00"))));
            assertEquals("PASSED", service.evaluate(config(output, "==", literal("2026-01-01 08:00:00", "DATETIME")), null, actual, List.of(output)).getStatus());
        } catch (java.text.ParseException impossible) { throw new AssertionError(impossible); }
    }

    @Test
    public void largeExponentsAreNotExpandedAndDiffDisplayIsBounded() {
        var exponent = service.compareSnapshots(response("1e400"), response("2e400"));
        assertEquals("FAILED", exponent.getStatus());
        assertTrue(exponent.getDiffs().get(0).actualJson().length() < 20);
        String one = java.util.stream.IntStream.range(0, 120).mapToObj(i -> "\"v" + i + "\":1").collect(java.util.stream.Collectors.joining(",", "{", "}"));
        String two = one.replace(":1", ":2");
        var many = service.compareSnapshots(response(one), response(two));
        assertEquals(100, many.getDiffs().size()); assertTrue(many.isTruncated());
    }

    private TestExpectationResult evaluate(String config, String output) { return service.evaluate(config, null, response(output), List.of(field())); }
    private String fieldConfig(String operator, Object expected) {
        return config(field(), operator, expected);
    }
    private String config(ResolvedField field, String operator, Object expected) {
        return service.writeJson(Map.of("version", 1, "mode", "FIELDS", "checks", List.of(Map.of(
                "refId", field.getRefId().toString(), "refType", field.getRefType(), "valueType", field.getValueType(), "operator", operator, "expected", expected))));
    }
    private static Map<String, Object> literal(Object value, String type) { return Map.of("kind", "LITERAL", "valueType", type, "value", value); }
    private static String response(String result) { return "{\"code\":200,\"data\":{\"success\":true,\"result\":" + result + "}}"; }
    private static ResolvedField field() {
        ResolvedField field = new ResolvedField(); field.setRefId(19L); field.setRefType("VARIABLE");
        field.setCode("amount"); field.setScriptName("amount"); field.setLabel("额度"); field.setValueType("NUMBER"); return field;
    }
}
