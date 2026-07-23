package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleFieldValidation;
import com.hengshucredit.rule.server.mapper.RuleFieldValidationMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BuiltinFieldValidationMetadataInitializerTest {

    @Test
    public void initializeInsertsMissingRulesUpdatesBuiltinMetadataAndLeavesUserRulesUntouched() {
        Map<String, RuleFieldValidation> rows = new LinkedHashMap<>();
        AtomicLong ids = new AtomicLong(100);
        AtomicInteger inserts = new AtomicInteger();

        RuleFieldValidation custom = rule(1L, "customer_rule", "客户自建规则", "REGEX", "^CUSTOM$", 0);
        rows.put(custom.getValidationCode(), custom);
        RuleFieldValidation staleEmail = rule(2L, "builtin_email", "旧邮箱规则", "REGEX", "^old$", 0);
        rows.put(staleEmail.getValidationCode(), staleEmail);

        RuleFieldValidationMapper mapper = (RuleFieldValidationMapper) Proxy.newProxyInstance(
                RuleFieldValidationMapper.class.getClassLoader(),
                new Class[]{RuleFieldValidationMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        return new ArrayList<>(rows.values());
                    }
                    if ("insert".equals(method.getName())) {
                        RuleFieldValidation value = (RuleFieldValidation) args[0];
                        if (value.getId() == null) value.setId(ids.incrementAndGet());
                        rows.put(value.getValidationCode(), value);
                        inserts.incrementAndGet();
                        return 1;
                    }
                    if ("updateById".equals(method.getName())) {
                        RuleFieldValidation value = (RuleFieldValidation) args[0];
                        rows.put(value.getValidationCode(), value);
                        return 1;
                    }
                    if ("toString".equals(method.getName())) return "RuleFieldValidationMapperStub";
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                });

        BuiltinFieldValidationMetadataInitializer initializer =
                new BuiltinFieldValidationMetadataInitializer();
        ReflectionTestUtils.setField(initializer, "validationMapper", mapper);

        initializer.initialize();

        assertEquals(12, rows.size());
        assertEquals(10, inserts.get());
        assertEquals("客户自建规则", rows.get("customer_rule").getValidationName());
        assertEquals("^CUSTOM$", rows.get("customer_rule").getValidationValue());
        RuleFieldValidation email = rows.get("builtin_email");
        assertEquals("邮箱", email.getValidationName());
        assertEquals(Integer.valueOf(1), email.getStatus());
        assertTrue(email.getValidationValue().contains("@"));

        initializer.initialize();

        assertEquals(12, rows.size());
        assertEquals(10, inserts.get());
    }

    private static RuleFieldValidation rule(Long id, String code, String name,
                                            String type, String value, int status) {
        RuleFieldValidation rule = new RuleFieldValidation();
        rule.setId(id);
        rule.setProjectId(0L);
        rule.setScope(RuleFieldValidationService.SCOPE_GLOBAL);
        rule.setValidationCode(code);
        rule.setValidationName(name);
        rule.setValidationType(type);
        rule.setValidationValue(value);
        rule.setErrorMessage("旧错误提示");
        rule.setDescription("旧说明");
        rule.setStatus(status);
        return rule;
    }
}
