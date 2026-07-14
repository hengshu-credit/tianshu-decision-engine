package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.server.functions.RuleListFunctions;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FunctionRegistrarTest {

    @Test
    public void javaFunctionFallsBackFromExampleClassToServerClass() {
        RuleFunction function = new RuleFunction();
        function.setFuncCode("calculateVAT");
        function.setImplType("JAVA");
        function.setImplClass("com.bjjw.rule.example.functions.TaxFunctions");
        function.setImplMethod("calculateVAT");
        function.setParamsJson("[{\"name\":\"amount\",\"type\":\"NUMBER\"},{\"name\":\"rate\",\"type\":\"NUMBER\"}]");

        QLExpressEngine engine = new QLExpressEngine();
        new FunctionRegistrar().registerJavaFunctions(Collections.singletonList(function), engine.getRunner());

        RuleResult result = engine.execute(
                "taxAmount = calculateVAT(113000, 0.13);\n" +
                "_result = {\"taxAmount\": taxAmount}",
                Collections.emptyMap());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertEquals(13000.0, ((Number) output.get("taxAmount")).doubleValue(), 0.000001);
    }

    @Test
    public void javaFunctionFormatsAmountWithoutScriptConstructor() {
        RuleFunction function = new RuleFunction();
        function.setFuncCode("formatAmount");
        function.setImplType("JAVA");
        function.setImplClass("com.hengshucredit.rule.server.functions.TaxFunctions");
        function.setImplMethod("formatAmount");
        function.setParamsJson("[{\"name\":\"amount\",\"type\":\"NUMBER\"}]");

        QLExpressEngine engine = new QLExpressEngine();
        new FunctionRegistrar().registerJavaFunctions(Collections.singletonList(function), engine.getRunner());

        RuleResult result = engine.execute(
                "formatted = formatAmount(13000.0);\n" +
                "_result = {\"formatted\": formatted}",
                Collections.emptyMap());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertEquals("13,000.00", output.get("formatted"));
    }

    @Test
    public void serverRegistersListFunctionsWithSpringManagedDependencies() {
        RuleListService listService = new RuleListService() {
            @Override
            public boolean match(Long listId, Object content, java.util.List<String> itemTypes, String matchMode) {
                return Long.valueOf(9L).equals(listId) && "13800138000".equals(content);
            }
        };
        FunctionRegistrar registrar = new FunctionRegistrar();
        ReflectionTestUtils.setField(registrar, "ruleListFunctions",
                new RuleListFunctions(new ListMatchMatrix(listService)));
        QLExpressEngine engine = new QLExpressEngine();

        registrar.registerServerFunctions(engine.getRunner());
        RuleResult result = engine.execute(
                "_result = {\"booleanHit\": isInLists(\"13800138000\", [9,10]), "
                        + "\"numberMiss\": isInListsNumber(\"other\", 9)}\n_result",
                Collections.emptyMap());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertEquals(Boolean.TRUE, output.get("booleanHit"));
        assertEquals(0, ((Number) output.get("numberMiss")).intValue());
    }

    @Test
    public void scriptFunctionPrefixExecutesRoundTax() {
        RuleFunction function = new RuleFunction();
        function.setFuncCode("roundTax");
        function.setImplType("SCRIPT");
        function.setParamsJson("[{\"name\":\"amount\",\"type\":\"NUMBER\"}]");
        function.setImplScript("scaled = amount * 100 + 0.5; return (scaled - scaled % 1) / 100;");

        String prefix = new FunctionRegistrar().buildScriptFunctionPrefix(Collections.singletonList(function));

        QLExpressEngine engine = new QLExpressEngine();
        RuleResult result = engine.execute(prefix +
                "taxAmount = roundTax(12.345);\n" +
                "_result = {\"taxAmount\": taxAmount}\n" +
                "_result",
                Collections.emptyMap());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertEquals(12.35, ((Number) output.get("taxAmount")).doubleValue(), 0.000001);
    }

    @Test
    public void scriptFunctionPrefixExecutesIdCardAndStringUtilities() {
        String prefix = new FunctionRegistrar().buildScriptFunctionPrefix(Arrays.asList(
                scriptFunction("idCardGender", "[{\"name\":\"idCard\",\"type\":\"STRING\"}]", idCardGenderScript()),
                scriptFunction("idCardBirthDate", "[{\"name\":\"idCard\",\"type\":\"STRING\"}]", idCardBirthDateScript()),
                scriptFunction("strLeft", "[{\"name\":\"text\",\"type\":\"STRING\"},{\"name\":\"n\",\"type\":\"NUMBER\"}]", strLeftScript()),
                scriptFunction("strRight", "[{\"name\":\"text\",\"type\":\"STRING\"},{\"name\":\"n\",\"type\":\"NUMBER\"}]", strRightScript()),
                scriptFunction("idCardAge", "[{\"name\":\"idCard\",\"type\":\"STRING\"},{\"name\":\"currentDate\",\"type\":\"DATE\"},{\"name\":\"calcMode\",\"type\":\"STRING\"}]", idCardAgeScript()),
                scriptFunction("regexMatch", "[{\"name\":\"text\",\"type\":\"STRING\"},{\"name\":\"regex\",\"type\":\"STRING\"}]", regexMatchScript())
        ));

        QLExpressEngine engine = new QLExpressEngine();
        RuleResult result = engine.execute(prefix +
                "_result = {" +
                "\"genderMale\": idCardGender(\"110105199001012317\"), " +
                "\"genderFemale\": idCardGender(\"110105199001012326\"), " +
                "\"genderBad\": idCardGender(\"bad\"), " +
                "\"birth\": idCardBirthDate(\"110105199001012317\"), " +
                "\"leftShort\": strLeft(\"abc\", 5), " +
                "\"left\": strLeft(\"abcdef\", 3), " +
                "\"rightShort\": strRight(\"abc\", 5), " +
                "\"right\": strRight(\"abcdef\", 3), " +
                "\"ageFullBeforeBirthday\": idCardAge(\"110105199001022317\", \"2025-01-01\", \"FULL\"), " +
                "\"ageFullAfterBirthday\": idCardAge(\"110105199001022317\", \"2025-01-02\", \"FULL\"), " +
                "\"ageYear\": idCardAge(\"110105199001022317\", \"2025-01-01\", \"YEAR\"), " +
                "\"ageDefaultNow\": idCardAge(\"110105199001012317\"), " +
                "\"regexOk\": regexMatch(\"abc123\", \"[a-z]+[0-9]+\"), " +
                "\"regexNo\": regexMatch(\"abc\", \"[0-9]+\"), " +
                "\"regexBad\": regexMatch(\"abc\", \"[\")" +
                "}\n_result", Collections.emptyMap());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertEquals(1, ((Number) output.get("genderMale")).intValue());
        assertEquals(0, ((Number) output.get("genderFemale")).intValue());
        assertEquals(-1, ((Number) output.get("genderBad")).intValue());
        assertTrue(output.get("birth") instanceof Date);
        assertEquals("abc", output.get("leftShort"));
        assertEquals("abc", output.get("left"));
        assertEquals("abc", output.get("rightShort"));
        assertEquals("def", output.get("right"));
        assertEquals(34, ((Number) output.get("ageFullBeforeBirthday")).intValue());
        assertEquals(35, ((Number) output.get("ageFullAfterBirthday")).intValue());
        assertEquals(35, ((Number) output.get("ageYear")).intValue());
        assertTrue(((Number) output.get("ageDefaultNow")).intValue() >= 35);
        assertEquals(1, ((Number) output.get("regexOk")).intValue());
        assertEquals(0, ((Number) output.get("regexNo")).intValue());
        assertEquals(0, ((Number) output.get("regexBad")).intValue());
    }

    private static RuleFunction scriptFunction(String funcCode, String paramsJson, String implScript) {
        RuleFunction function = new RuleFunction();
        function.setFuncCode(funcCode);
        function.setImplType("SCRIPT");
        function.setParamsJson(paramsJson);
        function.setImplScript(implScript);
        return function;
    }

    private static String idCardGenderScript() {
        return "if (idCard == null) {\n" +
                "    return -1;\n" +
                "}\n" +
                "id = (\"\" + idCard).trim();\n" +
                "if (id.matches(\"[0-9]{17}[0-9Xx]\")) {\n" +
                "    sexText = id.substring(16, 17);\n" +
                "} else if (id.matches(\"[0-9]{15}\")) {\n" +
                "    sexText = id.substring(14, 15);\n" +
                "} else {\n" +
                "    return -1;\n" +
                "}\n" +
                "sexDigit = java.lang.Integer.parseInt(sexText);\n" +
                "return sexDigit % 2 == 0 ? 0 : 1;";
    }

    private static String idCardBirthDateScript() {
        return "if (idCard == null) {\n" +
                "    return null;\n" +
                "}\n" +
                "id = (\"\" + idCard).trim();\n" +
                "if (id.matches(\"[0-9]{17}[0-9Xx]\")) {\n" +
                "    birthText = id.substring(6, 14);\n" +
                "} else if (id.matches(\"[0-9]{15}\")) {\n" +
                "    birthText = \"19\" + id.substring(6, 12);\n" +
                "} else {\n" +
                "    return null;\n" +
                "}\n" +
                "try {\n" +
                "    formatter = new java.text.SimpleDateFormat(\"yyyyMMdd\");\n" +
                "    formatter.setLenient(false);\n" +
                "    return formatter.parse(birthText);\n" +
                "} catch (Exception ex) {\n" +
                "    return null;\n" +
                "}";
    }

    private static String strLeftScript() {
        return "if (text == null) {\n" +
                "    return null;\n" +
                "}\n" +
                "if (n == null) {\n" +
                "    return \"\";\n" +
                "}\n" +
                "s = \"\" + text;\n" +
                "limit = n.intValue();\n" +
                "if (limit <= 0) {\n" +
                "    return \"\";\n" +
                "}\n" +
                "if (s.length() <= limit) {\n" +
                "    return s;\n" +
                "}\n" +
                "return s.substring(0, limit);";
    }

    private static String strRightScript() {
        return "if (text == null) {\n" +
                "    return null;\n" +
                "}\n" +
                "if (n == null) {\n" +
                "    return \"\";\n" +
                "}\n" +
                "s = \"\" + text;\n" +
                "limit = n.intValue();\n" +
                "if (limit <= 0) {\n" +
                "    return \"\";\n" +
                "}\n" +
                "len = s.length();\n" +
                "if (len <= limit) {\n" +
                "    return s;\n" +
                "}\n" +
                "return s.substring(len - limit, len);";
    }

    private static String idCardAgeScript() {
        return "if (idCard == null) {\n" +
                "    return -1;\n" +
                "}\n" +
                "id = (\"\" + idCard).trim();\n" +
                "if (id.matches(\"[0-9]{17}[0-9Xx]\")) {\n" +
                "    birthText = id.substring(6, 14);\n" +
                "} else if (id.matches(\"[0-9]{15}\")) {\n" +
                "    birthText = \"19\" + id.substring(6, 12);\n" +
                "} else {\n" +
                "    return -1;\n" +
                "}\n" +
                "try {\n" +
                "    formatter = new java.text.SimpleDateFormat(\"yyyyMMdd\");\n" +
                "    formatter.setLenient(false);\n" +
                "    birthDate = formatter.parse(birthText);\n" +
                "} catch (Exception ex) {\n" +
                "    return -1;\n" +
                "}\n" +
                "if (calcMode == null && currentDate instanceof java.lang.String) {\n" +
                "    currentDateText = (\"\" + currentDate).trim();\n" +
                "    if (currentDateText.equals(\"YEAR\") || currentDateText.equals(\"FULL\")) {\n" +
                "        calcMode = currentDateText;\n" +
                "        currentDate = null;\n" +
                "    }\n" +
                "}\n" +
                "mode = calcMode == null ? \"FULL\" : (\"\" + calcMode).trim();\n" +
                "if (currentDate == null) {\n" +
                "    current = new java.util.Date();\n" +
                "} else if (currentDate instanceof java.util.Date) {\n" +
                "    current = currentDate;\n" +
                "} else if (currentDate instanceof java.lang.String) {\n" +
                "    currentText = (\"\" + currentDate).trim();\n" +
                "    if (currentText.matches(\"[0-9]{8}\")) {\n" +
                "        parseText = currentText;\n" +
                "    } else if (currentText.matches(\"[0-9]{4}-[0-9]{2}-[0-9]{2}\")) {\n" +
                "        parseText = currentText.substring(0, 4) + currentText.substring(5, 7) + currentText.substring(8, 10);\n" +
                "    } else {\n" +
                "        return -1;\n" +
                "    }\n" +
                "    try {\n" +
                "        formatter = new java.text.SimpleDateFormat(\"yyyyMMdd\");\n" +
                "        formatter.setLenient(false);\n" +
                "        current = formatter.parse(parseText);\n" +
                "    } catch (Exception ex) {\n" +
                "        return -1;\n" +
                "    }\n" +
                "} else {\n" +
                "    return -1;\n" +
                "}\n" +
                "age = current.getYear() - birthDate.getYear();\n" +
                "if (age < 0) {\n" +
                "    return -1;\n" +
                "}\n" +
                "if (mode.equals(\"YEAR\")) {\n" +
                "    return age;\n" +
                "}\n" +
                "if (current.getMonth() < birthDate.getMonth() || (current.getMonth() == birthDate.getMonth() && current.getDate() < birthDate.getDate())) {\n" +
                "    age = age - 1;\n" +
                "}\n" +
                "return age < 0 ? -1 : age;";
    }

    private static String regexMatchScript() {
        return "if (text == null || regex == null) {\n" +
                "    return 0;\n" +
                "}\n" +
                "try {\n" +
                "    return (\"\" + text).matches(\"\" + regex) ? 1 : 0;\n" +
                "} catch (Exception ex) {\n" +
                "    return 0;\n" +
                "}";
    }
}
