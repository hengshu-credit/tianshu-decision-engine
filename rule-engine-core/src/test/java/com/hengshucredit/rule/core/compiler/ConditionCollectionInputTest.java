package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.Assert.*;

public class ConditionCollectionInputTest {
    @Test
    public void typedStringMembersPreserveCommaWhitespaceEmptyAndEscapes() {
        JSONObject leaf = condition("in", strings("A,B", " padded ", "", "say\"yes", "path\\name"));
        String script = ConditionOperandCompiler.compile(leaf, null);
        assertEquals("value in [\"A,B\", \" padded \", \"\", \"say\\\"yes\", \"path\\\\name\"]", script);
        for (String value : new String[]{"A,B", " padded ", "say\"yes", "path\\name"}) assertResult(script, value, true);
        assertResult(script, "A", false);
        assertResult(script, "padded", false);
        // 保留引擎既有缺失值规则：空白左值不因集合中保留空字符串而命中。
        assertResult(script, "", false);
    }

    @Test
    public void newExactNumericMembersUseExistingCastWithoutDoubleRounding() {
        String exact = "9007199254740993.123456789";
        JSONObject array = array(number(exact));
        JSONObject leaf = condition("in", array);
        String script = ConditionOperandCompiler.compile(leaf, null);
        assertTrue(script, script.contains("toNumberValue(\"" + exact + "\")"));
        assertResult(script, new BigDecimal(exact), true);
        assertResult(script, new BigDecimal("9007199254740993.123456790"), false);
        assertResult(script, exact, false);
    }

    @Test
    public void inAndInArrayKeepDifferentStringNumberMembershipSemantics() {
        JSONObject leaf = condition("in", array(number("1")));
        String script = ConditionOperandCompiler.compile(leaf, null);
        assertResult(script, "1", false);
        assertResult(script, 1, true);
        leaf.put("operator", "in_array");
        assertResult(ConditionOperandCompiler.compile(leaf, null), "1", true);
    }

    @Test
    public void typedNotInRetainsMissingGuardAndNumberTypes() {
        JSONObject leaf = condition("not_in", array(number("1"), literal("STRING", "1"), literal("BOOLEAN", true)));
        String script = ConditionOperandCompiler.compile(leaf, null);
        assertTrue(script, script.startsWith("(!isBlank(value) && !(value in ["));
        assertResult(script, 1L, false);
        assertResult(script, "1", false);
        assertResult(script, true, false);
        assertResult(script, "true", true);
        assertResult(script, "", false);
    }

    @Test
    public void oldCsvAndExistingArrayItemsAreNotRewritten() {
        JSONObject leaf = condition("in", literal("STRING", " 1 , true , A "));
        String before = leaf.toJSONString();
        assertEquals("value in [1, true, \"A\"]", ConditionOperandCompiler.compile(leaf, null));
        assertEquals(before, leaf.toJSONString());
        JSONObject array = array(literal("NUMBER", "1.234567890123456789"));
        leaf.put("operator", "in_array"); leaf.put("rightOperand", array);
        assertEquals("containsValue([1.234567890123456789], value)", ConditionOperandCompiler.compile(leaf, null));
    }

    @Test
    public void existingMemberReferencesResolveByIdAndArePreserved() {
        JSONObject reference = JSON.parseObject("{\"kind\":\"REFERENCE\",\"refId\":19,\"refType\":\"VARIABLE\",\"code\":\"oldAllowed\",\"valueType\":\"STRING\"}");
        JSONObject leaf = condition("in", array(reference));
        String before = leaf.toJSONString();
        VarContext context = new VarContext(Map.of(19L, "renamedAllowed"), Map.of(), Map.of("VARIABLE:19", "renamedAllowed"));
        assertEquals("value in [renamedAllowed]", ConditionOperandCompiler.compile(leaf, context));
        assertEquals(before, leaf.toJSONString());
    }

    @Test
    public void unfinishedTypedCollectionFailsBeforeGraphFallbackOrSilentTruth() {
        for (JSONObject values : new JSONObject[]{array(), array(number("")), array(number("NaN")), array(number("1 + 2"))}) {
            JSONObject leaf = condition("in", values);
            assertCollectionError(() -> ConditionOperandCompiler.compile(leaf, null));
            assertCollectionError(() -> ConditionOperandCompiler.hasUsableCondition(leaf));
        }
        JSONObject leaf = condition("in", strings("A"));
        leaf.put("leftOperand", literal("NUMBER", ""));
        assertCollectionError(() -> ConditionOperandCompiler.compile(leaf, null));
        assertCollectionError(() -> ConditionOperandCompiler.hasUsableCondition(leaf));
    }

    @Test
    public void typedCollectionWithoutLeftCannotFallBackToLegacyTrue() {
        JSONObject leaf = condition("in", strings("A"));
        leaf.put("leftOperand", null);
        JSONObject rule = new JSONObject(); rule.put("conditionRoot", leaf);
        JSONObject model = new JSONObject(); model.put("rules", new JSONArray(java.util.List.of(rule)));
        CompileResult result = new DecisionTableCompiler().compile(model.toJSONString());
        assertFalse(result.getCompiledScript(), result.isSuccess());
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("集合"));
    }

    @Test
    public void emptyCollectionInUnselectedTernaryCannotBeSilentlySkipped() {
        JSONObject ternary = condition("in", array());
        ternary.put("type", "ternary"); ternary.put("targetOperand", null);
        assertCollectionError(() -> ActionDataCompiler.compile(new JSONArray(java.util.List.of(ternary))));
    }

    @Test
    public void emptyCrossTableCellDoesNotHideInvalidTypedCollection() {
        JSONObject model = JSON.parseObject("{\"resultVar\":{\"varCode\":\"result\",\"varType\":\"NUMBER\"},\"rowDimensions\":[{\"varCode\":\"value\",\"varType\":\"STRING\",\"segments\":[{\"operator\":\"in\"}]}],\"colDimensions\":[{\"varCode\":\"kind\",\"varType\":\"STRING\",\"segments\":[{\"operator\":\"==\",\"value\":\"A\"}]}],\"cells\":[[null]]}");
        model.getJSONArray("rowDimensions").getJSONObject(0).getJSONArray("segments").getJSONObject(0).put("valueOperand", array());
        CompileResult result = new AdvancedCrossTableCompiler().compile(model.toJSONString());
        assertFalse(result.getCompiledScript(), result.isSuccess());
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("集合"));
    }

    @Test
    public void oldNumericLiteralItemsCannotCompileAsEmptyOrExecutableFragmentsInNewCollections() {
        for (String operator : new String[]{"in", "not_in"}) {
            for (String type : new String[]{"NUMBER", "LONG", "DOUBLE", "BIGDECIMAL", "PROBABILITY"}) {
                for (String value : new String[]{null, "", "  ", "NaN", "1 + 2"}) {
                    JSONObject leaf = condition(operator, array(literal(type, value)));
                    assertCollectionError(() -> ConditionOperandCompiler.compile(leaf, null));
                    assertCollectionError(() -> ConditionOperandCompiler.hasUsableCondition(leaf));
                }
            }
        }
        JSONObject leaf = condition("in", array(literal("NUMBER", "1.234567890123456789")));
        assertEquals("value in [1.234567890123456789]", ConditionOperandCompiler.compile(leaf, null));
        leaf.put("operator", "in_array");
        assertEquals("containsValue([1.234567890123456789], value)", ConditionOperandCompiler.compile(leaf, null));
    }

    private static JSONObject condition(String operator, JSONObject values) {
        JSONObject leaf = JSON.parseObject("{\"type\":\"leaf\",\"leftOperand\":{\"kind\":\"PATH\",\"value\":\"value\",\"valueType\":\"STRING\"}}");
        leaf.put("operator", operator); leaf.put("rightOperand", values); return leaf;
    }

    private static JSONObject literal(String type, Object value) {
        JSONObject item = new JSONObject(); item.put("kind", "LITERAL"); item.put("valueType", type); item.put("value", value); return item;
    }

    private static JSONObject number(String value) {
        JSONObject item = new JSONObject(); item.put("kind", "CAST"); item.put("targetType", "NUMBER"); item.put("operand", literal("STRING", value)); return item;
    }

    private static JSONObject strings(String... values) {
        JSONObject[] items = new JSONObject[values.length];
        for (int i = 0; i < values.length; i++) items[i] = literal("STRING", values[i]);
        return array(items);
    }

    private static JSONObject array(JSONObject... values) {
        JSONArray items = new JSONArray(); for (JSONObject value : values) items.add(value);
        JSONObject array = new JSONObject(); array.put("kind", "ARRAY"); array.put("valueType", "LIST"); array.put("items", items); return array;
    }

    private static void assertResult(String expression, Object input, boolean expected) {
        RuleResult result = new QLExpressEngine().execute("return " + expression + ";", Map.of("value", input), false);
        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(expression, expected, result.getResult());
    }

    private static void assertCollectionError(Runnable action) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, action::run);
        assertTrue(error.getMessage(), error.getMessage().contains("集合"));
    }
}
