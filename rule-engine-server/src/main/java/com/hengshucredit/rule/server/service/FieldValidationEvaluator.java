package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleFieldValidation;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class FieldValidationEvaluator {

    public List<FieldValidationViolation> validate(List<RuleDefinitionInputField> fields,
                                                    List<RuleFieldValidation> validations,
                                                    Map<String, Object> params) {
        Map<Long, RuleFieldValidation> validationMap = new HashMap<>();
        for (RuleFieldValidation validation : validations == null
                ? Collections.<RuleFieldValidation>emptyList() : validations) {
            if (validation != null && validation.getId() != null
                    && !Integer.valueOf(0).equals(validation.getStatus())) {
                validationMap.put(validation.getId(), validation);
            }
        }
        List<FieldValidationViolation> violations = new ArrayList<>();
        for (RuleDefinitionInputField field : fields == null
                ? Collections.<RuleDefinitionInputField>emptyList() : fields) {
            if (field == null || Integer.valueOf(0).equals(field.getStatus())) continue;
            String path = firstText(field.getScriptName(), field.getFieldName());
            if (path == null) continue;
            PathValue pathValue = readPath(params, path);
            for (Long validationId : parseIds(field.getValidationRuleIds())) {
                RuleFieldValidation validation = validationMap.get(validationId);
                if (validation == null || passes(validation, pathValue)) continue;
                violations.add(new FieldValidationViolation(path, validation.getId(),
                        validation.getValidationCode(), validation.getValidationName(),
                        firstText(validation.getErrorMessage(), field.getFieldLabel() + "校验失败", path + "校验失败")));
            }
        }
        return violations;
    }

    private boolean passes(RuleFieldValidation validation, PathValue pathValue) {
        String type = text(validation.getValidationType()).toUpperCase(Locale.ROOT);
        boolean missing = !pathValue.present || isBlank(pathValue.value);
        if ("REQUIRED".equals(type)) return !missing;
        if (missing) return true;
        Object value = pathValue.value;
        String expected = validation.getValidationValue();
        try {
            switch (type) {
                case "REGEX":
                    return Pattern.compile(expected).matcher(String.valueOf(value)).matches();
                case "MIN_VALUE":
                    return decimal(value).compareTo(decimal(expected)) >= 0;
                case "MAX_VALUE":
                    return decimal(value).compareTo(decimal(expected)) <= 0;
                case "MIN_LENGTH":
                    return length(value) >= Integer.parseInt(text(expected));
                case "MAX_LENGTH":
                    return length(value) <= Integer.parseInt(text(expected));
                case "IN":
                    return setValues(expected).contains(String.valueOf(value));
                case "NOT_IN":
                    return !setValues(expected).contains(String.valueOf(value));
                default:
                    return false;
            }
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private List<Long> parseIds(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyList();
        try {
            List<Long> ids = JSON.parseArray(json, Long.class);
            return ids == null ? Collections.<Long>emptyList() : ids;
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
    }

    private List<String> setValues(String value) {
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        String trimmed = value.trim();
        if (trimmed.startsWith("[")) {
            List<Object> parsed = JSON.parseArray(trimmed, Object.class);
            List<String> result = new ArrayList<>();
            for (Object item : parsed) result.add(String.valueOf(item));
            return result;
        }
        List<String> result = new ArrayList<>();
        for (String item : trimmed.split(",")) result.add(item.trim());
        return result;
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal ? (BigDecimal) value
                : new BigDecimal(String.valueOf(value).trim());
    }

    private int length(Object value) {
        if (value instanceof CharSequence) return ((CharSequence) value).length();
        if (value instanceof Collection) return ((Collection<?>) value).size();
        if (value instanceof Map) return ((Map<?, ?>) value).size();
        if (value != null && value.getClass().isArray()) return Array.getLength(value);
        return String.valueOf(value).length();
    }

    private boolean isBlank(Object value) {
        if (value == null) return true;
        if (value instanceof CharSequence) return value.toString().trim().isEmpty();
        if (value instanceof Collection) return ((Collection<?>) value).isEmpty();
        if (value instanceof Map) return ((Map<?, ?>) value).isEmpty();
        return value.getClass().isArray() && Array.getLength(value) == 0;
    }

    private PathValue readPath(Map<String, Object> params, String path) {
        Map<String, Object> source = params == null ? Collections.<String, Object>emptyMap() : params;
        if (source.containsKey(path)) return new PathValue(true, source.get(path));
        Object current = source;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map) || !((Map<?, ?>) current).containsKey(part)) {
                return new PathValue(false, null);
            }
            current = ((Map<?, ?>) current).get(part);
        }
        return new PathValue(true, current);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static class PathValue {
        private final boolean present;
        private final Object value;

        private PathValue(boolean present, Object value) {
            this.present = present;
            this.value = value;
        }
    }
}
