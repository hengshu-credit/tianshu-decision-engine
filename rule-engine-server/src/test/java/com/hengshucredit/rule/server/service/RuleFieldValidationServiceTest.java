package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleFieldValidation;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class RuleFieldValidationServiceTest {

    @Test
    public void preservesUserProvidedCodeAndRegex() throws Exception {
        RuleFieldValidation rule = rule("Mobile_Check", "REGEX", "^1\\d{10}$");

        normalize(rule);

        assertEquals("Mobile_Check", rule.getValidationCode());
        assertEquals("^1\\d{10}$", rule.getValidationValue());
        assertEquals("REGEX", rule.getValidationType());
        assertEquals(Long.valueOf(0L), rule.getProjectId());
    }

    @Test
    public void rejectsInvalidRegexBeforeSaving() throws Exception {
        try {
            normalize(rule("bad_regex", "REGEX", "["));
            fail("非法正则表达式必须被拒绝");
        } catch (InvocationTargetException e) {
            if (!(e.getCause() instanceof RuntimeException)) throw e;
        }
    }

    private RuleFieldValidation rule(String code, String type, String value) {
        RuleFieldValidation rule = new RuleFieldValidation();
        rule.setScope("GLOBAL");
        rule.setValidationCode(code);
        rule.setValidationName("手机号格式");
        rule.setValidationType(type);
        rule.setValidationValue(value);
        rule.setErrorMessage("手机号格式错误");
        rule.setStatus(1);
        return rule;
    }

    private void normalize(RuleFieldValidation rule) throws Exception {
        RuleFieldValidationService service = new RuleFieldValidationService();
        Method method = RuleFieldValidationService.class.getDeclaredMethod(
                "normalizeAndValidate", RuleFieldValidation.class);
        method.setAccessible(true);
        method.invoke(service, rule);
    }
}
