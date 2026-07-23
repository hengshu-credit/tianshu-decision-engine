package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleFieldValidation;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BuiltinFieldValidationCatalogTest {

    @Test
    public void definitionsContainTheCompleteCommonValidationCatalog() {
        List<RuleFieldValidation> definitions = BuiltinFieldValidationCatalog.definitions();
        Set<String> codes = new HashSet<>();
        Map<String, RuleFieldValidation> byCode = new HashMap<>();

        for (RuleFieldValidation definition : definitions) {
            assertTrue("duplicate validationCode: " + definition.getValidationCode(),
                    codes.add(definition.getValidationCode()));
            byCode.put(definition.getValidationCode(), definition);
            assertEquals(RuleFieldValidationService.SCOPE_GLOBAL, definition.getScope());
            assertEquals(Long.valueOf(0L), definition.getProjectId());
            assertEquals(Integer.valueOf(1), definition.getStatus());
            assertEquals(Boolean.TRUE, definition.getBuiltIn());
            assertFalse(definition.getValidationName().trim().isEmpty());
            assertFalse(definition.getErrorMessage().trim().isEmpty());
        }

        assertEquals(11, definitions.size());
        assertEquals("必填", byCode.get("builtin_required").getValidationName());
        assertEquals("REQUIRED", byCode.get("builtin_required").getValidationType());
        assertNull(byCode.get("builtin_required").getValidationValue());
        assertTrue(byCode.keySet().contains("builtin_mobile"));
        assertTrue(byCode.keySet().contains("builtin_id_card"));
        assertTrue(byCode.keySet().contains("builtin_email"));
        assertTrue(byCode.keySet().contains("builtin_ip_address"));
    }

    @Test
    public void regularExpressionDefinitionsAcceptValidSamplesAndRejectInvalidSamples() {
        Map<String, String[]> samples = new HashMap<>();
        samples.put("builtin_digits", pair("012345", "12a"));
        samples.put("builtin_digits_min_16", pair("1234567890123456", "123456789012345"));
        samples.put("builtin_digits_15_to_18", pair("123456789012345", "12345678901234"));
        samples.put("builtin_chinese", pair("中文", "中文A"));
        samples.put("builtin_alphanumeric", pair("abc123", "abc-123"));
        samples.put("builtin_email", pair("user.name+tag@example-domain.com", "user@"));
        samples.put("builtin_domain", pair("sub.example.com.", "example"));
        samples.put("builtin_mobile", pair("13800138000", "23800138000"));
        samples.put("builtin_id_card", pair("11010519900101123X", "11010519901301123X"));
        samples.put("builtin_ip_address", pair("192.168.1.1", "256.168.1.1"));

        for (RuleFieldValidation definition : BuiltinFieldValidationCatalog.definitions()) {
            if ("REQUIRED".equals(definition.getValidationType())) continue;
            String[] sample = samples.get(definition.getValidationCode());
            assertTrue("missing sample: " + definition.getValidationCode(), sample != null);
            Pattern pattern = Pattern.compile(definition.getValidationValue());
            assertTrue(definition.getValidationCode(), pattern.matcher(sample[0]).matches());
            assertFalse(definition.getValidationCode(), pattern.matcher(sample[1]).matches());
        }
    }

    private static String[] pair(String valid, String invalid) {
        return new String[]{valid, invalid};
    }
}
