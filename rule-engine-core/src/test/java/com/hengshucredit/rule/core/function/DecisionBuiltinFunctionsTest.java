package com.hengshucredit.rule.core.function;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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
    public void dateAndScoreFunctionsWork() {
        assertEquals("2026-07-09", functions.dateFormat("2026-07-09 10:30:00", "yyyy-MM-dd"));
        assertEquals("2026-07-09", functions.dateConvert("20260709", "yyyyMMdd", "yyyy-MM-dd"));
        assertEquals("2026-07-12 10:30:00", functions.dateAdd("2026-07-09 10:30:00", 3, "DAY"));
        assertEquals("2026-06-09 10:30:00", functions.dateSub("2026-07-09 10:30:00", 1, "MONTH"));
        assertEquals(8L, functions.dateDiff("2026-07-01 00:00:00", "2026-07-09 12:30:00", "DAY"));
        assertEquals(750L, functions.dateDiff("2026-07-09 10:30:00", "2026-07-09 10:30:00.750", "MILLISECOND"));

        assertEquals(620.0d, functions.scoreByOddsPdo(40, 600, 20, 20, "ASC").doubleValue(), 0.000001d);
        assertEquals(580.0d, functions.scoreByOddsPdo(40, 600, 20, 20, "DESC").doubleValue(), 0.000001d);
        assertEquals(600.0d, functions.scoreByBadRatePdo(1d / 21d, 600, 20, 20, "ASC").doubleValue(), 0.000001d);
    }

    @Test
    public void idCardFunctionsExtractBirthDateAndCalculateAge() {
        assertEquals("1990-01-02", functions.idCardBirthDate("110105199001022317"));
        assertEquals("1990-01-02", functions.idCardBirthDate("110105900102231"));
        assertNull(functions.idCardBirthDate("110105199013022317"));

        assertEquals(34L, functions.idCardAge("110105199001022317", "2025-01-01 12:30:00", "FULL"));
        assertEquals(35L, functions.idCardAge("110105199001022317", "2025-01-02", "DAY"));
        assertEquals(35L, functions.idCardAge("110105199001022317", "2025-01-01", "YEAR"));
        assertEquals(-1L, functions.idCardAge("110105209001022317", "2025-01-02", "FULL"));
    }

    @Test
    public void probabilityScoreUsesConfiguredDirection() {
        assertEquals(600.0d, functions.scoreByProbability(0.5, 600, 20, null).doubleValue(), 0.000001d);
        assertEquals(556.06d, functions.scoreByProbability(0.9, 600, 20, "HIGH_GOOD").doubleValue(), 0.000001d);
        assertEquals(556.06d, functions.scoreByProbability(0.9, 600, 20, "越大越好").doubleValue(), 0.000001d);
        assertEquals(643.94d, functions.scoreByProbability(0.9, 600, 20, "LOW_GOOD").doubleValue(), 0.000001d);
        assertEquals(643.94d, functions.scoreByProbability(0.9, 600, 20, "越小越好").doubleValue(), 0.000001d);
        assertNull(functions.scoreByProbability(0, 600, 20, "HIGH_GOOD"));
        assertNull(functions.scoreByProbability(1, 600, 20, "HIGH_GOOD"));
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
                "nextDate = dateAdd(\"2026-07-09 10:30:00\", 3, \"DAY\");\n" +
                "birthDate = idCardBirthDate(\"110105199001022317\");\n" +
                "age = idCardAge(\"110105199001022317\", \"2025-01-02 12:30:00\", \"FULL\");\n" +
                "probabilityScore = scoreByProbability(0.9, 600, 20, \"HIGH_GOOD\");\n" +
                "score = scoreByOddsPdo(40, 600, 20, 20, \"ASC\");\n" +
                "_result = {\"successAmount\": successAmount, \"phone\": phone, \"sorted\": sorted, \"ratio\": ratio, \"nextDate\": nextDate, \"birthDate\": birthDate, \"age\": age, \"probabilityScore\": probabilityScore, \"score\": score}\n" +
                "_result",
                context);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertEquals(20.0d, ((Number) output.get("successAmount")).doubleValue(), 0.000001d);
        assertEquals("13800138000", output.get("phone"));
        assertEquals(Arrays.asList(1, 2, 3), output.get("sorted"));
        assertEquals(3.33d, ((Number) output.get("ratio")).doubleValue(), 0.000001d);
        assertEquals("2026-07-12 10:30:00", output.get("nextDate"));
        assertEquals("1990-01-02", output.get("birthDate"));
        assertEquals(35, ((Number) output.get("age")).intValue());
        assertEquals(556.06d, ((Number) output.get("probabilityScore")).doubleValue(), 0.000001d);
        assertEquals(620.0d, ((Number) output.get("score")).doubleValue(), 0.000001d);
    }

    @Test
    public void uruleDateAndCurrentTimeEquivalentsWork() {
        assertTrue(Pattern.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}", functions.currentDate()));
        assertTrue(Pattern.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}", functions.currentDateTime()));
        assertEquals("2027-01-31 10:20:30", functions.dateAddYears("2026-01-31 10:20:30", 1));
        assertEquals("2026-02-28 10:20:30", functions.dateAddMonths("2026-01-31 10:20:30", 1));
        assertEquals("2027-03-03 14:25:36", functions.dateAddParts("2026-01-31 10:20:30", 1, 1, 3, 4, 5, 6));
        assertEquals("2024-12-28 06:15:24", functions.dateSubParts("2026-01-31 10:20:30", 1, 1, 3, 4, 5, 6));
        assertEquals("2026-01-29 10:20:30", functions.dateSubDays("2026-01-31 10:20:30", 2));
        assertEquals(2026L, functions.dateYear("2026-07-14 15:16:17"));
        assertEquals(7L, functions.dateMonth("2026-07-14 15:16:17"));
        assertEquals(14L, functions.dateDay("2026-07-14 15:16:17"));
        assertEquals(15L, functions.dateHour("2026-07-14 15:16:17"));
        assertEquals(16L, functions.dateMinute("2026-07-14 15:16:17"));
        assertEquals(17L, functions.dateSecond("2026-07-14 15:16:17"));
        assertEquals(2L, functions.dateDiffDays("2026-07-01", "2026-07-03"));
        assertEquals(31, functions.dateDaysInMonths("2026-07-14").size());
        assertEquals(334, functions.dateDaysOutsideMonths("2026-07-14").size());
        assertEquals(59, functions.dateDaysInSpecifiedMonths("2026-01-01", "2026-03-31", "1,2").size());
        assertEquals(31, functions.dateDaysOutsideSpecifiedMonths("2026-01-01", "2026-03-31", "1,2").size());
    }

    @Test
    public void uruleStringAndMathEquivalentsWork() {
        assertEquals("BCD", functions.strSubstring("ABCDE", 1, 4));
        assertEquals("CDE", functions.strSubstringFrom("ABCDE", 2));
        assertEquals("ABC", functions.strSubstringTo("ABCDE", 3));
        assertEquals("abc", functions.strLower("AbC"));
        assertEquals("ABC", functions.strUpper("AbC"));
        assertEquals("B", functions.strCharAt("ABC", 1));
        assertEquals(1L, functions.strIndexOf("ABCA", "B"));
        assertEquals(3L, functions.strLastIndexOf("ABCA", "A"));
        assertEquals("A.B", functions.strReplaceLiteral("A-B", "-", "."));
        assertEquals(9d, functions.numMax(9, 2), 0d);
        assertEquals(2d, functions.numMin(9, 2), 0d);
        assertEquals(2d, functions.numCeil(1.2), 0d);
        assertEquals(1d, functions.numFloor(1.9), 0d);
        assertEquals(2d, functions.numRoundInteger(1.5), 0d);
        assertEquals(0d, functions.numSin(0), 0d);
        assertEquals(1d, functions.numCos(0), 0d);
        assertEquals(1d, functions.numLn(Math.E), 0.000001d);
        assertEquals(2d, functions.numLog10(100), 0.000001d);
    }

    @Test
    public void uruleListMapObjectAndCastEquivalentsWorkWithoutMutatingInput() {
        List<Object> source = Arrays.<Object>asList(3, 1, 2);
        assertEquals(3d, ((Number) functions.arrMax(source)).doubleValue(), 0d);
        assertEquals(1d, ((Number) functions.arrMin(source)).doubleValue(), 0d);
        assertEquals(Arrays.<Object>asList(3, 1, 2, 4), functions.arrAdd(source, 4));
        assertEquals(Arrays.<Object>asList(3, 1), functions.arrRemove(source, 2));
        assertFalse(functions.arrIsEmpty(source));
        assertTrue(functions.arrIsEmpty(Arrays.asList()));

        Map<String, Object> rowA = new LinkedHashMap<>();
        rowA.put("score", 20);
        Map<String, Object> rowB = new LinkedHashMap<>();
        rowB.put("score", 10);
        assertEquals(Arrays.asList(10, 20), functions.arrPluck(functions.arrSortBy(Arrays.asList(rowA, rowB), "score", "ASC"), "score"));

        Map<String, Object> original = new LinkedHashMap<>();
        original.put("a", 1);
        Map<String, Object> added = functions.mapPut(original, "b", 2);
        assertEquals(1, original.size());
        assertEquals(2, added.size());
        assertTrue(functions.mapHasKey(added, "b"));
        assertEquals(2, functions.mapGet(added, "b"));
        assertEquals(2L, functions.mapSize(added));
        assertEquals(Arrays.asList("a", "b"), functions.mapKeys(added));
        assertEquals(Arrays.asList(1, 2), functions.mapValues(added));
        assertEquals(original, functions.mapRemove(added, "b"));
        assertTrue(functions.newMap().isEmpty());
        assertTrue(functions.newList().isEmpty());
        assertTrue(((Map<?, ?>) functions.newLike(original)).isEmpty());
        assertTrue(((List<?>) functions.newLike(source)).isEmpty());

        assertEquals("12.5", functions.toStringValue(12.5));
        assertEquals(12.5d, functions.toNumberValue("12.5").doubleValue(), 0d);
        assertEquals(Boolean.TRUE, functions.toBooleanValue("true"));
        assertEquals(Arrays.asList(1, 2), functions.toListValue("[1,2]"));
        assertEquals(1, functions.toMapValue("{\"a\":1}").get("a"));
    }

    @Test
    public void qlExpressRegistersNewSafeBuiltins() {
        QLExpressEngine engine = new QLExpressEngine();
        RuleResult result = engine.execute(
                "m = newMap();\n" +
                "m = mapPut(m, \"score\", numCeil(toNumberValue(\"12.2\")));\n" +
                "_result = {\"date\": currentDate(), \"upper\": strUpper(\"ab\"), \"score\": mapGet(m, \"score\")}\n" +
                "_result", new LinkedHashMap<String, Object>());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertTrue(Pattern.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}", String.valueOf(output.get("date"))));
        assertEquals("AB", output.get("upper"));
        assertEquals(13d, ((Number) output.get("score")).doubleValue(), 0d);
    }

    @Test
    public void cosineSimilaritySupportsListsAndPrimitiveArrays() {
        assertEquals(1d, functions.cosineSimilarity(
                Arrays.asList(1d, 2d, 3d), new float[]{1f, 2f, 3f}), 0.000001d);
        assertEquals(-1d, functions.cosineSimilarity(
                new double[]{1d, 0d}, Arrays.asList(-2d, 0d)), 0.000001d);
    }

    @Test(expected = IllegalArgumentException.class)
    public void cosineSimilarityRejectsDifferentDimensions() {
        functions.cosineSimilarity(Arrays.asList(1d), Arrays.asList(1d, 2d));
    }

    @Test(expected = IllegalArgumentException.class)
    public void cosineSimilarityRejectsZeroVector() {
        functions.cosineSimilarity(Arrays.asList(0d, 0d), Arrays.asList(1d, 1d));
    }

    @Test
    public void facenoxLivenessKeepsOriginalLogitsAndCalculatesDecisionFields() {
        List<Double> logits = Arrays.asList(1.2d, 0.2d);

        Map<String, Object> result = functions.facenoxLiveness(logits, 0.5d);

        assertSame(logits, result.get("logits"));
        assertEquals(1d, ((Number) result.get("logitDiff")).doubleValue(), 0.000001d);
        assertEquals(1d, ((Number) result.get("confidence")).doubleValue(), 0.000001d);
        assertEquals(Boolean.TRUE, result.get("isReal"));
        assertEquals(Boolean.FALSE, result.get("isSpoof"));
    }

    @Test
    public void facenoxLivenessUsesAbsoluteDifferenceForSpoofConfidence() {
        Map<String, Object> result = functions.facenoxLiveness(Arrays.asList(-0.5d, 0.7d), 0.3d);

        assertEquals(-1.2d, ((Number) result.get("logitDiff")).doubleValue(), 0.000001d);
        assertEquals(1.2d, ((Number) result.get("confidence")).doubleValue(), 0.000001d);
        assertEquals(Boolean.FALSE, result.get("isReal"));
        assertEquals(Boolean.TRUE, result.get("isSpoof"));
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
