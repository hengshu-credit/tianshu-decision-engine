package com.hengshucredit.rule.server.service;

public class FieldValidationViolation {
    private final String field;
    private final Long validationId;
    private final String validationCode;
    private final String validationName;
    private final String message;

    public FieldValidationViolation(String field, Long validationId, String validationCode,
                                    String validationName, String message) {
        this.field = field;
        this.validationId = validationId;
        this.validationCode = validationCode;
        this.validationName = validationName;
        this.message = message;
    }

    public String getField() { return field; }
    public Long getValidationId() { return validationId; }
    public String getValidationCode() { return validationCode; }
    public String getValidationName() { return validationName; }
    public String getMessage() { return message; }
}
