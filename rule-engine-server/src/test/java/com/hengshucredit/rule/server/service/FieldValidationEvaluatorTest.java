package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleFieldValidation;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FieldValidationEvaluatorTest {

    private final FieldValidationEvaluator evaluator = new FieldValidationEvaluator();

    @Test
    public void appliesMultipleRulesToNestedFieldAndReturnsEveryFailure() {
        RuleDefinitionInputField field = field("customer.age", "[1,2]");
        RuleFieldValidation required = rule(1L, "age_required", "REQUIRED", null, "年龄不能为空");
        RuleFieldValidation minimum = rule(2L, "age_min", "MIN_VALUE", "18", "年龄不能小于18岁");

        List<FieldValidationViolation> missing = evaluator.validate(
                Collections.singletonList(field), Arrays.asList(required, minimum),
                JSON.parseObject("{\"customer\":{}}"));
        List<FieldValidationViolation> tooYoung = evaluator.validate(
                Collections.singletonList(field), Arrays.asList(required, minimum),
                JSON.parseObject("{\"customer\":{\"age\":16}}"));
        List<FieldValidationViolation> valid = evaluator.validate(
                Collections.singletonList(field), Arrays.asList(required, minimum),
                JSON.parseObject("{\"customer\":{\"age\":20}}"));

        assertEquals(Collections.singletonList("年龄不能为空"), messages(missing));
        assertEquals(Collections.singletonList("年龄不能小于18岁"), messages(tooYoung));
        assertTrue(valid.isEmpty());
    }

    @Test
    public void supportsRegexLengthAndSetRulesWhileOptionalBlankValuesAreSkipped() {
        RuleDefinitionInputField field = field("mobile", "[3,4,5]");
        RuleFieldValidation regex = rule(3L, "mobile_regex", "REGEX", "^1\\d{10}$", "手机号格式错误");
        RuleFieldValidation minLength = rule(4L, "mobile_length", "MIN_LENGTH", "11", "手机号长度不足");
        RuleFieldValidation excluded = rule(5L, "mobile_excluded", "NOT_IN", "[\"13800000000\"]", "测试手机号不可用");

        assertTrue(evaluator.validate(Collections.singletonList(field), Arrays.asList(regex, minLength, excluded),
                Collections.<String, Object>emptyMap()).isEmpty());
        assertEquals(Arrays.asList("手机号格式错误", "手机号长度不足"), messages(evaluator.validate(
                Collections.singletonList(field), Arrays.asList(regex, minLength, excluded),
                Collections.<String, Object>singletonMap("mobile", "123"))));
        assertEquals(Collections.singletonList("测试手机号不可用"), messages(evaluator.validate(
                Collections.singletonList(field), Arrays.asList(regex, minLength, excluded),
                Collections.<String, Object>singletonMap("mobile", "13800000000"))));
    }

    @Test
    public void supportsAllRegexPresetsForStringFields() {
        Object[][] cases = {
                {"digits", "^[0-9]*$", "012345", "12a"},
                {"digits_min_16", "^\\d{16,}$", "1234567890123456", "123456789012345"},
                {"digits_15_to_18", "^\\d{15,18}$", "123456789012345", "12345678901234"},
                {"chinese", "^[\\u4e00-\\u9fa5]{0,}$", "中文", "中文A"},
                {"alphanumeric", "^[a-zA-Z0-9]+$", "abc123", "abc-123"},
                {"email", "^\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*$",
                        "user.name+tag@example-domain.com", "user@"},
                {"domain", "^[a-zA-Z0-9][-a-zA-Z0-9]{0,62}"
                        + "(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+\\.?$", "sub.example.com.", "example"},
                {"mobile", "^1[0-9]{10}$", "13800138000", "23800138000"},
                {"id_card", "^[1-9]\\d{5}(18|19|20)\\d{2}((0[1-9])|(1[0-2]))"
                        + "(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$",
                        "11010519900101123X", "11010519901301123X"},
                {"ip_address", "^((?:(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.){3}"
                        + "(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d))$", "192.168.1.1", "256.168.1.1"}
        };
        long id = 100L;
        for (Object[] item : cases) {
            RuleDefinitionInputField field = field("value", "[" + id + "]");
            field.setFieldType("STRING");
            RuleFieldValidation regex = rule(id, String.valueOf(item[0]), "REGEX",
                    String.valueOf(item[1]), "格式错误");

            assertTrue(String.valueOf(item[0]), evaluator.validate(Collections.singletonList(field),
                    Collections.singletonList(regex),
                    Collections.<String, Object>singletonMap("value", item[2])).isEmpty());
            assertEquals(String.valueOf(item[0]), Collections.singletonList("格式错误"),
                    messages(evaluator.validate(Collections.singletonList(field), Collections.singletonList(regex),
                            Collections.<String, Object>singletonMap("value", item[3]))));
            assertTrue(String.valueOf(item[0]), evaluator.validate(Collections.singletonList(field),
                    Collections.singletonList(regex),
                    Collections.<String, Object>singletonMap("value", "   ")).isEmpty());
            id++;
        }
    }

    private RuleDefinitionInputField field(String scriptName, String validationRuleIds) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setScriptName(scriptName);
        field.setFieldName(scriptName);
        field.setFieldLabel("年龄");
        field.setValidationRuleIds(validationRuleIds);
        field.setStatus(1);
        return field;
    }

    private RuleFieldValidation rule(Long id, String code, String type, String value, String message) {
        RuleFieldValidation rule = new RuleFieldValidation();
        rule.setId(id);
        rule.setValidationCode(code);
        rule.setValidationName(code);
        rule.setValidationType(type);
        rule.setValidationValue(value);
        rule.setErrorMessage(message);
        rule.setStatus(1);
        return rule;
    }

    private List<String> messages(List<FieldValidationViolation> violations) {
        java.util.ArrayList<String> messages = new java.util.ArrayList<>();
        for (FieldValidationViolation violation : violations) messages.add(violation.getMessage());
        return messages;
    }
}
