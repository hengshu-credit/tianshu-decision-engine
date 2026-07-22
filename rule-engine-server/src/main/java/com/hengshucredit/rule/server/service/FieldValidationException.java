package com.hengshucredit.rule.server.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FieldValidationException extends RuntimeException {
    private final List<FieldValidationViolation> violations;

    public FieldValidationException(List<FieldValidationViolation> violations) {
        super(message(violations));
        this.violations = Collections.unmodifiableList(new ArrayList<>(violations));
    }

    public List<FieldValidationViolation> getViolations() {
        return violations;
    }

    public FieldValidationViolation getFirstViolation() {
        return violations.isEmpty() ? null : violations.get(0);
    }

    private static String message(List<FieldValidationViolation> violations) {
        if (violations == null || violations.isEmpty()) return "字段校验失败";
        return violations.stream().map(FieldValidationViolation::getMessage)
                .collect(Collectors.joining("；"));
    }
}
