package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class ConditionRangeSafetyTest {
    @Test
    public void incompleteRangesMustFailInsteadOfBecomingConstantPredicates() {
        for (String operator : new String[]{"between", "not_between"}) {
            for (String value : new String[]{null, "", "18", "18,", ",60", "18,,60", "18,60,90", " , "}) {
                assertRangeError(() -> ConditionExpressionBuilder.build("age", "NUMBER", operator, value, false));
            }
        }
    }

    @Test
    public void invalidAndReversedNumericBoundsAreRejectedWithoutExecutingThem() {
        for (String value : new String[]{"60,18", "NaN,60", "18,Infinity", "foo(),60", "18,20 + 1"}) {
            assertRangeError(() -> ConditionExpressionBuilder.build("age", "NUMBER", "between", value, false));
        }
        assertRangeError(() -> ConditionExpressionBuilder.build("age", "NUMBER", "between", "lower,upper", true));
    }

    @Test
    public void inclusiveEqualAndHighPrecisionBoundsPreserveLegacyValues() {
        assertEquals("(age >= 18 && age <= 18)",
                ConditionExpressionBuilder.build("age", "NUMBER", "between", "18,18", false));
        String lower = "9007199254740993.123456789";
        String upper = "9007199254740993.123456790";
        assertEquals("(amount >= " + lower + " && amount <= " + upper + ")",
                ConditionExpressionBuilder.build("amount", "BIGDECIMAL", "between", lower + "," + upper, false));
        assertRangeError(() -> ConditionExpressionBuilder.build("amount", "BIGDECIMAL", "between", upper + "," + lower, false));
    }

    @Test
    public void invalidAndReversedDateRangesFailWhileLegacyDateFormatsRemainSupported() {
        for (String value : new String[]{"2026-02-30,2026-03-01", "2026-12-31,2026-01-01", "not-a-date,2026-01-01"}) {
            assertRangeError(() -> ConditionExpressionBuilder.build("date", "DATE", "between", value, false));
        }
        assertTrue(ConditionExpressionBuilder.build("date", "DATE", "between", "2026/01/01,2026/12/31", false)
                .contains("dateToMillis(\"2026/01/01\")"));
    }

    @Test
    public void operandRangeRequiresLiteralBoundsAndPreservesIdResolution() {
        JSONObject leaf = leaf("18,60");
        leaf.put("leftOperand", JSON.parseObject("{\"kind\":\"REFERENCE\",\"refId\":19,\"refType\":\"VARIABLE\",\"code\":\"oldAge\",\"valueType\":\"NUMBER\"}"));
        VarContext context = new VarContext(Map.of(19L, "currentAge"), Map.of(), Map.of("VARIABLE:19", "currentAge"));
        assertEquals("(currentAge >= 18 && currentAge <= 60)", ConditionOperandCompiler.compile(leaf, context));
        leaf.put("rightOperand", null);
        assertRangeError(() -> ConditionOperandCompiler.compile(leaf, context));
        assertRangeError(() -> ConditionOperandCompiler.hasUsableCondition(leaf));
    }

    @Test
    public void graphCompilersCannotFallbackToStaleScriptOrDefaultBranchForIncompleteRanges() {
        for (RuleCompiler compiler : new RuleCompiler[]{new DecisionFlowCompiler(), new DecisionTreeCompiler()}) {
            for (String stale : new String[]{"", "true"}) {
                JSONObject graph = graph(leaf(null));
                graph.getJSONArray("edges").getJSONObject(1).put("conditionExpression", stale);
                assertCompilationRejected(compiler.compile(graph.toJSONString()));
                graph.getJSONArray("edges").getJSONObject(1).put("conditionConfig", legacyLeaf(null));
                assertCompilationRejected(compiler.compile(graph.toJSONString()));
            }
        }
    }

    @Test
    public void tableRuleSetAndScorecardCompilersRejectStructuredIncompleteRanges() {
        JSONObject rule = new JSONObject();
        rule.put("conditionRoot", leaf("18"));
        rule.put("actions", new JSONArray());
        JSONObject table = new JSONObject();
        table.put("rules", array(rule));
        assertCompilationRejected(new DecisionTableCompiler().compile(table.toJSONString()));
        assertCompilationRejected(new RuleSetCompiler().compile(table.toJSONString()));

        JSONObject item = leaf("18");
        item.put("condOperator", "between");
        item.put("score", 5);
        JSONObject scorecard = new JSONObject();
        scorecard.put("scoreItems", array(item));
        assertCompilationRejected(new ScorecardCompiler().compile(scorecard.toJSONString()));

        JSONObject advanced = JSON.parseObject("{\"resultVar\":{\"varCode\":\"totalScore\",\"varType\":\"NUMBER\"},\"dimensionGroups\":[{\"dimensions\":[{\"rules\":[{\"score\":5}]}]}]}");
        advanced.getJSONArray("dimensionGroups").getJSONObject(0).getJSONArray("dimensions").getJSONObject(0)
                .getJSONArray("rules").getJSONObject(0).put("conditions", array(leaf("18")));
        assertCompilationRejected(new AdvancedScorecardCompiler().compile(advanced.toJSONString()));
    }

    @Test
    public void legacyNullBoundsMustNotBeSkippedOrReplacedWithFalse() {
        JSONObject item = JSON.parseObject("{\"condVar\":\"age\",\"condVarType\":\"NUMBER\",\"condOperator\":\"between\",\"score\":5}");
        JSONObject scorecard = new JSONObject();
        scorecard.put("scoreItems", array(item));
        assertCompilationRejected(new ScorecardCompiler().compile(scorecard.toJSONString()));
        JSONArray definition = array(JSON.parseObject("{\"varCode\":\"age\",\"varType\":\"NUMBER\"}"));
        JSONArray conditions = array(JSON.parseObject("{\"operator\":\"between\",\"value\":null}"));
        assertRangeError(() -> DecisionTableCompiler.compileLegacyFlatAnd(conditions, definition, null));
    }

    @Test
    public void nestedActionConditionsCannotTurnIncompleteNotBetweenIntoUnconditionalAssignments() {
        JSONObject branch = leaf("18");
        branch.put("type", "if");
        branch.put("operator", "not_between");
        branch.put("actions", JSON.parseArray("[{\"type\":\"assign\",\"target\":\"decision\",\"value\":\"PASS\"}]"));
        JSONObject action = new JSONObject();
        action.put("type", "if-block");
        action.put("branches", array(branch));
        assertRangeError(() -> ActionDataCompiler.compile(array(action)));
    }

    @Test
    public void legacyVariableBoundsCannotBypassValidationThroughEarlyFalseReturn() {
        JSONObject leaf = legacyLeaf(null);
        leaf.put("valueKind", "VAR");
        assertRangeError(() -> DecisionTableCompiler.compileLeaf(leaf, null));
    }

    @Test
    public void missingLegacyLeftSideCannotHideAnExplicitIncompleteRange() {
        JSONObject leaf = legacyLeaf("18");
        leaf.remove("varCode");
        assertRangeError(() -> DecisionTableCompiler.compileLeaf(leaf, null));
        JSONArray actions = JSON.parseArray("[{\"type\":\"if-block\",\"branches\":[{\"type\":\"if\",\"condOp\":\"between\",\"condValue\":\"18\",\"actions\":[]}]}]");
        assertRangeError(() -> ActionDataCompiler.compile(actions));
    }

    @Test
    public void emptyNumericLeftOperandCannotBecomeTrueOrAnUnconditionalGraphBranch() {
        JSONObject leaf = leaf("18,60");
        leaf.put("leftOperand", JSON.parseObject("{\"kind\":\"LITERAL\",\"valueType\":\"NUMBER\",\"value\":\"\"}"));
        assertRangeError(() -> ConditionOperandCompiler.compile(leaf, null));
        assertRangeError(() -> ConditionOperandCompiler.hasUsableCondition(leaf));
        for (RuleCompiler compiler : new RuleCompiler[]{new DecisionFlowCompiler(), new DecisionTreeCompiler()}) {
            assertCompilationRejected(compiler.compile(graph(leaf).toJSONString()));
        }
    }

    @Test
    public void incompleteTernaryRangesAreValidatedBeforeSkippingMissingTargetOrLeft() {
        for (String json : new String[]{
                "{\"type\":\"ternary\",\"target\":\"decision\",\"condOp\":\"between\",\"condValue\":\"18\",\"trueValue\":\"1\",\"falseValue\":\"0\"}",
                "{\"type\":\"ternary\",\"condVar\":\"age\",\"condOp\":\"not_between\",\"condValue\":\"18\",\"trueValue\":\"1\",\"falseValue\":\"0\"}"}) {
            assertRangeError(() -> ActionDataCompiler.compile(array(JSON.parseObject(json))));
        }
        JSONObject typed = leaf("18,");
        typed.put("type", "ternary");
        typed.put("targetOperand", null);
        typed.put("trueOperand", JSON.parseObject("{\"kind\":\"LITERAL\",\"valueType\":\"NUMBER\",\"value\":1}"));
        typed.put("falseOperand", JSON.parseObject("{\"kind\":\"LITERAL\",\"valueType\":\"NUMBER\",\"value\":0}"));
        assertRangeError(() -> ActionDataCompiler.compile(array(typed)));
        assertEquals("", ActionDataCompiler.compile(JSON.parseArray("[{\"type\":\"ternary\",\"condOp\":\"==\"}]")));
    }

    @Test
    public void advancedCrossTableEmptyCellsCannotHideInvalidDimensionRanges() {
        JSONObject model = JSON.parseObject("{\"resultVar\":{\"varCode\":\"result\",\"varType\":\"NUMBER\"},\"rowDimensions\":[{\"varCode\":\"age\",\"varType\":\"NUMBER\",\"segments\":[{\"operator\":\"between\",\"value\":\"18\"}]}],\"colDimensions\":[{\"varCode\":\"kind\",\"varType\":\"STRING\",\"segments\":[{\"operator\":\"==\",\"value\":\"A\"}]}],\"cells\":[[null]]}");
        assertCompilationRejected(new AdvancedCrossTableCompiler().compile(model.toJSONString()));
        model.getJSONArray("rowDimensions").getJSONObject(0).getJSONArray("segments").getJSONObject(0).put("value", "18,60");
        assertTrue(new AdvancedCrossTableCompiler().compile(model.toJSONString()).isSuccess());
    }

    private static JSONObject leaf(String value) {
        JSONObject leaf = JSON.parseObject("{\"type\":\"leaf\",\"operator\":\"between\",\"leftOperand\":{\"kind\":\"PATH\",\"value\":\"age\",\"valueType\":\"NUMBER\"}}");
        if (value != null) {
            JSONObject right = new JSONObject();
            right.put("kind", "LITERAL"); right.put("valueType", "NUMBER"); right.put("value", value);
            leaf.put("rightOperand", right);
        }
        return leaf;
    }

    private static JSONObject legacyLeaf(String value) {
        JSONObject leaf = JSON.parseObject("{\"type\":\"leaf\",\"varCode\":\"age\",\"varType\":\"NUMBER\",\"operator\":\"between\"}");
        leaf.put("value", value);
        return leaf;
    }

    private static JSONObject graph(JSONObject condition) {
        JSONObject graph = JSON.parseObject("{\"nodes\":[{\"id\":\"s\",\"type\":\"start\"},{\"id\":\"d\",\"type\":\"decision\"},{\"id\":\"hit\",\"type\":\"end\"},{\"id\":\"miss\",\"type\":\"end\"}],\"edges\":[{\"source\":\"s\",\"target\":\"d\"},{\"source\":\"d\",\"target\":\"hit\"},{\"source\":\"d\",\"target\":\"miss\",\"conditionExpression\":\"age < 0\"}]}");
        graph.getJSONArray("edges").getJSONObject(1).put("conditionConfig", condition);
        return graph;
    }

    private static JSONArray array(JSONObject value) { JSONArray array = new JSONArray(); array.add(value); return array; }

    private static void assertCompilationRejected(CompileResult result) {
        assertFalse("残缺区间不能编译成功: " + result.getCompiledScript(), result.isSuccess());
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("区间"));
    }

    private static void assertRangeError(Runnable action) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, action::run);
        assertTrue(error.getMessage(), error.getMessage().contains("区间"));
    }
}
