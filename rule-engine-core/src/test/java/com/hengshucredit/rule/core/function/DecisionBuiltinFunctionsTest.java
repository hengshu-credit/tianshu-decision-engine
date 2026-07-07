package com.hengshucredit.rule.core.function;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DecisionBuiltinFunctionsTest {

    private final DecisionBuiltinFunctions functions = new DecisionBuiltinFunctions();

    @Test
    public void numberStringAndArrayFunctionsWork() {
        assertEquals(16.8d, functions.numAdd(12.3, 4.5).doubleValue(), 0.000001d);
        assertEquals(7.8d, functions.numSub(12.3, 4.5).doubleValue(), 0.000001d);
        assertEquals(55.35d, functions.numMul(12.3, 4.5).doubleValue(), 0.000001d);
        assertEquals(3.33d, functions.numDiv(10, 3, 2).doubleValue(), 0.000001d);
        assertEquals(12.35d, functions.numRound(12.345, 2).doubleValue(), 0.000001d);
        assertEquals(12.3d, functions.numAbs(-12.3).doubleValue(), 0.000001d);
        assertEquals(8d, functions.numPow(2, 3), 0.000001d);
        assertTrue(functions.numBetween(85, 60, 100));

        assertEquals(6, functions.strLength("ABC123"));
        assertEquals("ABC123", functions.strTrim("  ABC123  "));
        assertTrue(functions.strContains("ABC123", "BC"));
        assertEquals("123", functions.strReplace("A-123", "[^0-9]", ""));
        assertEquals("13800138000", functions.strRegexExtract("phone=13800138000", "phone=([0-9]+)", 1));
        assertEquals(Arrays.asList("A", "B", "C"), functions.strSplit("A,B,C", ","));
        assertEquals("A|B|C", functions.strJoin(Arrays.asList("A", "B", "C"), "|"));

        List<Object> values = Arrays.<Object>asList("A", "B", "A", 1, 1.0);
        assertEquals(5, functions.arrSize(values));
        assertEquals("B", functions.arrGet(values, 1));
        assertEquals("A", functions.arrFirst(values));
        assertEquals(1.0, ((Number) functions.arrLast(values)).doubleValue(), 0.000001d);
        assertEquals(Arrays.<Object>asList("A", "B", 1), functions.arrDistinct(values));
        assertEquals(Arrays.<Object>asList(1, 2, 3), functions.arrSort(Arrays.asList(3, 1, 2), "ASC"));
        assertTrue(functions.arrContains(values, 1.0));
    }

    @Test
    public void jsonPathAndObjectFunctionsWorkForObjectsAndLargeText() {
        Map<String, Object> sample = sampleJson();
        String jsonText = JSON.toJSONString(sample);

        assertEquals(35, ((Number) functions.jsonGet(sample, "customer.age")).intValue());
        assertEquals(35, ((Number) functions.jsonGet(jsonText, "$.customer.age")).intValue());
        assertEquals(2, functions.jsonList(sample, "$.orders[?(@.status='SUCCESS')].amount").size());
        assertTrue(functions.jsonExists(sample, "$.customer.city"));
        assertFalse(functions.jsonExists(sample, "$.customer.level"));
        assertEquals(3, functions.jsonCount(sample, "$.orders[*]"));
        assertEquals(20.0d, functions.jsonSum(jsonText, "$.orders[?(@.status='SUCCESS')].amount").doubleValue(), 0.000001d);
        assertEquals(23.0d / 3, functions.jsonAvg(sample, "$.orders[*].amount").doubleValue(), 0.000001d);
        assertEquals(3.0d, functions.jsonMin(sample, "$.orders[*].amount").doubleValue(), 0.000001d);
        assertEquals(12.5d, functions.jsonMax(sample, "$.orders[*].amount").doubleValue(), 0.000001d);

        assertNotNull(functions.jsonParse(jsonText));
        assertEquals(35, ((Number) functions.objGet(sample, "customer.age")).intValue());
        assertEquals("UNKNOWN", functions.objGetOrDefault(sample, "customer.level", "UNKNOWN"));
        assertTrue(functions.objHas(sample, "orders[0].status"));
        assertEquals(2, functions.objSize(sample));
        assertTrue(functions.objKeys(sample).contains("customer"));
        assertEquals(2, functions.objValues(sample).size());
        assertTrue(functions.toJson(sample).contains("\"orders\""));
    }

    @Test
    public void qlExpressCanCallRegisteredDecisionBuiltins() {
        QLExpressEngine engine = new QLExpressEngine();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("payload", sampleJson());
        context.put("numbers", Arrays.asList(3, 1, 2));
        context.put("jsonPath", "$.orders[?(@.status='SUCCESS')].amount");

        RuleResult result = engine.execute(
                "successAmount = jsonSum(payload, jsonPath);\n" +
                "phone = strRegexExtract(\"phone=13800138000\", \"phone=([0-9]+)\", 1);\n" +
                "sorted = arrSort(numbers, \"ASC\");\n" +
                "ratio = numDiv(10, 3, 2);\n" +
                "_result = {\"successAmount\": successAmount, \"phone\": phone, \"sorted\": sorted, \"ratio\": ratio}\n" +
                "_result",
                context);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertEquals(20.0d, ((Number) output.get("successAmount")).doubleValue(), 0.000001d);
        assertEquals("13800138000", output.get("phone"));
        assertEquals(Arrays.asList(1, 2, 3), output.get("sorted"));
        assertEquals(3.33d, ((Number) output.get("ratio")).doubleValue(), 0.000001d);
    }

    private static Map<String, Object> sampleJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("age", 35);
        customer.put("city", "上海");
        root.put("customer", customer);
        root.put("orders", Arrays.asList(
                order("SUCCESS", 12.5, "A"),
                order("FAIL", 3.0, "B"),
                order("SUCCESS", 7.5, "C")
        ));
        return root;
    }

    private static Map<String, Object> order(String status, double amount, String type) {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("status", status);
        order.put("amount", amount);
        order.put("type", type);
        return order;
    }
}
