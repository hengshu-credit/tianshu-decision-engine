package com.hengshucredit.rule.core.trace;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TraceIdGenerator {

    public static final int TRACE_ID_LENGTH = 36;
    public static final String GLOBAL_SCOPE_CODE = "0000";
    private static final long MAX_PROJECT_ID = 1679615L;
    private static final ZoneId TRACE_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final Pattern TYPE_PATTERN = Pattern.compile("[A-Z]{2}");
    private static final Pattern SCOPE_CODE_PATTERN = Pattern.compile("[0-9A-Z]{4}");
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[A-Z]{2}[GP][0-9A-Z]{4}\\d{17}[0-9A-Z]{12}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceIdGenerator() {
    }

    public static String generate(String typeCode, String scopeType, String scopeCode) {
        String normalizedType = normalizeType(typeCode);
        String normalizedScope = normalizeScope(scopeType);
        String normalizedCode = normalizeScopeCode(scopeCode);
        String time = TIME_FORMATTER.format(ZonedDateTime.now(TRACE_ZONE));
        String suffix = randomBase36Suffix();
        return normalizedType + normalizedScope + normalizedCode + time + suffix;
    }

    public static boolean isValid(String traceId) {
        return traceId != null && TRACE_ID_PATTERN.matcher(traceId).matches();
    }

    public static String ruleTypeCode(String modelType) {
        if (modelType == null) {
            throw new IllegalArgumentException("规则类型不能为空");
        }
        switch (modelType.trim().toUpperCase(Locale.ROOT)) {
            case "TABLE":
                return "TB";
            case "TREE":
                return "TR";
            case "FLOW":
                return "DF";
            case "RULE_SET":
                return "RS";
            case "CROSS":
            case "CROSS_TABLE":
                return "CT";
            case "SCORE":
            case "SCORE_CARD":
                return "SC";
            case "CROSS_ADV":
            case "CROSS_TABLE_ADV":
                return "AC";
            case "SCORE_ADV":
            case "SCORE_CARD_ADV":
                return "AS";
            case "SCRIPT":
                return "QL";
            default:
                throw new IllegalArgumentException("未知规则类型: " + modelType);
        }
    }

    public static String moduleTypeCode(String moduleType) {
        if (moduleType == null) {
            throw new IllegalArgumentException("模块类型不能为空");
        }
        switch (moduleType.trim().toUpperCase(Locale.ROOT)) {
            case "DATASOURCE":
            case "API":
                return "AP";
            case "DATABASE":
            case "DB":
                return "DB";
            case "LIST":
                return "LS";
            case "MODEL":
                return "MD";
            case "EXPERIMENT":
                return "EX";
            default:
                throw new IllegalArgumentException("未知模块类型: " + moduleType);
        }
    }

    public static String projectScopeCode(Long projectId) {
        if (projectId == null || projectId <= 0 || projectId > MAX_PROJECT_ID) {
            throw new IllegalArgumentException("项目ID超出四位Base36作用域容量: " + projectId);
        }
        return leftPadBase36(Long.toString(projectId, 36).toUpperCase(Locale.ROOT), 4);
    }

    private static String normalizeType(String typeCode) {
        String value = typeCode == null ? null : typeCode.trim().toUpperCase(Locale.ROOT);
        if (value == null || !TYPE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Trace类型必须是两位大写英文: " + typeCode);
        }
        return value;
    }

    private static String normalizeScope(String scopeType) {
        String value = scopeType == null ? null : scopeType.trim().toUpperCase(Locale.ROOT);
        if (!"G".equals(value) && !"P".equals(value)) {
            throw new IllegalArgumentException("Trace作用域必须是G或P: " + scopeType);
        }
        return value;
    }

    private static String normalizeScopeCode(String scopeCode) {
        String value = scopeCode == null ? null : scopeCode.trim().toUpperCase(Locale.ROOT);
        if (value == null || !SCOPE_CODE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Trace作用域编码必须是四位数字或大写英文: " + scopeCode);
        }
        return value;
    }

    private static String randomBase36Suffix() {
        String value = new BigInteger(62, RANDOM).toString(36).toUpperCase(Locale.ROOT);
        return leftPadBase36(value, 12);
    }

    private static String leftPadBase36(String value, int length) {
        if (value.length() > length) {
            throw new IllegalArgumentException("Base36值超过固定位数: " + value);
        }
        StringBuilder result = new StringBuilder(length);
        for (int i = value.length(); i < length; i++) {
            result.append('0');
        }
        return result.append(value).toString();
    }
}
