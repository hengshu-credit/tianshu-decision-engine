package com.hengshucredit.rule.core.function;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RandomBuiltinFunctionsTest {

    private final RandomBuiltinFunctions functions = new RandomBuiltinFunctions();

    @Test
    public void randomIntSupportsDefaultAndConfiguredIntervals() {
        for (int i = 0; i < 200; i++) {
            long defaultValue = functions.randomInt();
            assertTrue(defaultValue == 0L || defaultValue == 1L);

            long closedValue = functions.randomInt(10, 20);
            assertTrue(closedValue >= 10L && closedValue <= 20L);

            long leftOpenValue = functions.randomInt(10, 20, false, true);
            assertTrue(leftOpenValue > 10L && leftOpenValue <= 20L);

            long rightOpenValue = functions.randomInt(10, 20, true, false);
            assertTrue(rightOpenValue >= 10L && rightOpenValue < 20L);

            long openValue = functions.randomInt(10, 20, false, false);
            assertTrue(openValue > 10L && openValue < 20L);
        }
        assertEquals(2L, functions.randomInt(1, 3, false, false));
        assertEquals(Long.MAX_VALUE, functions.randomInt(Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test
    public void randomDecimalSupportsDefaultAndConfiguredIntervals() {
        for (int i = 0; i < 200; i++) {
            double defaultValue = functions.randomDecimal();
            assertTrue(defaultValue >= 0D && defaultValue <= 1D);

            double closedValue = functions.randomDecimal(10D, 20D);
            assertTrue(closedValue >= 10D && closedValue <= 20D);

            double leftOpenValue = functions.randomDecimal(10D, 20D, false, true);
            assertTrue(leftOpenValue > 10D && leftOpenValue <= 20D);

            double rightOpenValue = functions.randomDecimal(10D, 20D, true, false);
            assertTrue(rightOpenValue >= 10D && rightOpenValue < 20D);

            double openValue = functions.randomDecimal(10D, 20D, false, false);
            assertTrue(openValue > 10D && openValue < 20D);
        }
        assertEquals(2.5D, functions.randomDecimal(2.5D, 2.5D), 0D);
    }

    @Test
    public void randomIntRejectsInvalidArgumentsAndEmptyIntervals() {
        expectError("参数数量", () -> functions.randomInt(1));
        expectError("下界必须是数字", () -> functions.randomInt(null, 2));
        expectError("下界必须是整数", () -> functions.randomInt(1.5D, 2));
        expectError("下界不能大于上界", () -> functions.randomInt(3, 2));
        expectError("开闭参数必须是布尔值", () -> functions.randomInt(1, 2, "true", true));
        expectError("不存在可选整数", () -> functions.randomInt(1, 2, false, false));
        expectError("不存在可选整数", () -> functions.randomInt(Long.MAX_VALUE, Long.MAX_VALUE, false, true));
    }

    @Test
    public void randomDecimalRejectsInvalidArgumentsAndEmptyIntervals() {
        expectError("参数数量", () -> functions.randomDecimal(1));
        expectError("上界必须是数字", () -> functions.randomDecimal(1, null));
        expectError("有限数字", () -> functions.randomDecimal(Double.NaN, 1));
        expectError("有限数字", () -> functions.randomDecimal(0, Double.POSITIVE_INFINITY));
        expectError("下界不能大于上界", () -> functions.randomDecimal(3, 2));
        expectError("开闭参数必须是布尔值", () -> functions.randomDecimal(1, 2, true, 1));
        expectError("空区间", () -> functions.randomDecimal(1, 1, true, false));
        double lower = 1D;
        double upper = Math.nextUp(lower);
        expectError("空区间", () -> functions.randomDecimal(lower, upper, false, false));
    }

    @Test
    public void qlExpressSupportsAllRandomFunctionArities() {
        QLExpressEngine engine = new QLExpressEngine();
        RuleResult result = engine.execute(
                "a = randomInt();\n" +
                        "b = randomInt(5, 5);\n" +
                        "c = randomInt(1, 3, false, false);\n" +
                        "d = randomDecimal();\n" +
                        "e = randomDecimal(2.5, 2.5);\n" +
                        "f = randomDecimal(10, 20, false, false);\n" +
                        "_result = {\"a\": a, \"b\": b, \"c\": c, \"d\": d, \"e\": e, \"f\": f};\n" +
                        "_result",
                new LinkedHashMap<String, Object>());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertTrue(((Number) output.get("a")).longValue() == 0L
                || ((Number) output.get("a")).longValue() == 1L);
        assertEquals(5L, ((Number) output.get("b")).longValue());
        assertEquals(2L, ((Number) output.get("c")).longValue());
        assertTrue(((Number) output.get("d")).doubleValue() >= 0D
                && ((Number) output.get("d")).doubleValue() <= 1D);
        assertEquals(2.5D, ((Number) output.get("e")).doubleValue(), 0D);
        assertTrue(((Number) output.get("f")).doubleValue() > 10D
                && ((Number) output.get("f")).doubleValue() < 20D);
    }

    private static void expectError(String messagePart, Runnable action) {
        try {
            action.run();
            fail("expected IllegalArgumentException containing: " + messagePart);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(messagePart));
        }
    }
}
