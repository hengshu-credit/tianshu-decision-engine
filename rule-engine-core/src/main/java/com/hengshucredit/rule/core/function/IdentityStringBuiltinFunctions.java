package com.hengshucredit.rule.core.function;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 为 QLExpress SCRIPT 函数提供受控的身份证、日期和字符串能力。
 *
 * <p>QLExpress 运行在 isolation 安全策略下，脚本不能直接调用任意 JVM 方法。
 * 本类仅注册确定性、无副作用的业务辅助函数，供函数管理中的 SCRIPT 模板调用。</p>
 */
public class IdentityStringBuiltinFunctions {

    private static final Pattern ID_CARD_18 = Pattern.compile("^\\d{17}[0-9Xx]$");
    private static final Pattern ID_CARD_15 = Pattern.compile("^\\d{15}$");
    private static final DateTimeFormatter YMD_COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YMD_DASH = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter YMD_SLASH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter YMD_HMS_DASH = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter YMD_HMS_SLASH = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter YMD_T_HMS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * 身份证提取性别：女 0、男 1、未知 -1。
     * 支持中国居民身份证 15 位和 18 位格式；格式或出生日期无效时返回 -1。
     */
    public Integer idCardGenderValue(Object idCard) {
        String normalized = normalizeIdCard(idCard);
        if (normalized == null || extractBirthDate(normalized) == null) {
            return -1;
        }
        int genderIndex = normalized.length() == 18 ? 16 : 14;
        char genderCode = normalized.charAt(genderIndex);
        if (!Character.isDigit(genderCode)) {
            return -1;
        }
        return ((genderCode - '0') % 2 == 0) ? 0 : 1;
    }

    /**
     * 身份证提取出生日期，返回 {@link java.sql.Date}；无法识别或日期非法时返回 null。
     */
    public Date idCardBirthDateValue(Object idCard) {
        String normalized = normalizeIdCard(idCard);
        LocalDate birthDate = normalized == null ? null : extractBirthDate(normalized);
        return birthDate == null ? null : Date.valueOf(birthDate);
    }

    /**
     * 提取字符串前 N 位；N 大于等于长度时返回原字符串，N 小于等于 0 时返回空字符串。
     */
    public String leftStringValue(Object text, Object length) {
        if (text == null || length == null) {
            return null;
        }
        String value = String.valueOf(text);
        int count = toInt(length);
        if (count <= 0) {
            return "";
        }
        return value.substring(0, Math.min(count, value.length()));
    }

    /**
     * 提取字符串后 N 位；N 大于等于长度时返回原字符串，N 小于等于 0 时返回空字符串。
     */
    public String rightStringValue(Object text, Object length) {
        if (text == null || length == null) {
            return null;
        }
        String value = String.valueOf(text);
        int count = toInt(length);
        if (count <= 0) {
            return "";
        }
        int start = Math.max(0, value.length() - count);
        return value.substring(start);
    }

    /**
     * 身份证计算年龄。
     *
     * <p>currentTime 为空时使用系统当前日期；支持 Date、LocalDate、LocalDateTime、
     * 毫秒/秒时间戳和 yyyy-MM-dd、yyyy/MM/dd、yyyyMMdd、带时分秒的日期字符串。</p>
     *
     * <p>mode 为 YEAR/0/按年相减时，返回当前年份减出生年份；
     * mode 为 YMD/EXACT/1/按年月日相减或为空时，按完整年月日计算周岁。</p>
     *
     * @return 身份证或当前日期无效、出生日期晚于当前日期时返回 -1
     */
    public Integer idCardAgeValue(Object idCard, Object currentTime, Object mode) {
        String normalized = normalizeIdCard(idCard);
        LocalDate birthDate = normalized == null ? null : extractBirthDate(normalized);
        if (birthDate == null) {
            return -1;
        }

        LocalDate currentDate = currentTime == null ? LocalDate.now() : toLocalDate(currentTime);
        if (currentDate == null || birthDate.isAfter(currentDate)) {
            return -1;
        }

        if (isYearDiffMode(mode)) {
            return currentDate.getYear() - birthDate.getYear();
        }

        int age = currentDate.getYear() - birthDate.getYear();
        if (currentDate.getMonthValue() < birthDate.getMonthValue()
                || (currentDate.getMonthValue() == birthDate.getMonthValue()
                && currentDate.getDayOfMonth() < birthDate.getDayOfMonth())) {
            age--;
        }
        return age;
    }

    /**
     * 字符串与正则完整匹配：匹配返回 1，不匹配、空值或非法正则返回 0。
     */
    public Integer regexMatchValue(Object text, Object regex) {
        if (text == null || regex == null) {
            return 0;
        }
        try {
            return Pattern.compile(String.valueOf(regex))
                    .matcher(String.valueOf(text))
                    .matches() ? 1 : 0;
        } catch (PatternSyntaxException e) {
            return 0;
        }
    }

    private static String normalizeIdCard(Object idCard) {
        if (idCard == null) {
            return null;
        }
        String value = String.valueOf(idCard).trim();
        if (ID_CARD_18.matcher(value).matches() || ID_CARD_15.matcher(value).matches()) {
            return value.toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private static LocalDate extractBirthDate(String idCard) {
        String birthText = idCard.length() == 18
                ? idCard.substring(6, 14)
                : "19" + idCard.substring(6, 12);
        try {
            return LocalDate.parse(birthText, YMD_COMPACT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return new BigDecimal(String.valueOf(value).trim()).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean isYearDiffMode(Object mode) {
        if (mode == null) {
            return false;
        }
        String value = String.valueOf(mode).trim().toUpperCase(Locale.ROOT);
        return "YEAR".equals(value)
                || "YEAR_DIFF".equals(value)
                || "YEAR_ONLY".equals(value)
                || "0".equals(value)
                || "按年相减".equals(value);
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).toLocalDate();
        }
        if (value instanceof java.util.Date) {
            return Instant.ofEpochMilli(((java.util.Date) value).getTime())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        }
        if (value instanceof Number) {
            long timestamp = ((Number) value).longValue();
            if (Math.abs(timestamp) < 100_000_000_000L) {
                timestamp *= 1000L;
            }
            return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
        }

        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        LocalDate parsed = parseLocalDate(text, YMD_DASH);
        if (parsed != null) return parsed;
        parsed = parseLocalDate(text, YMD_SLASH);
        if (parsed != null) return parsed;
        parsed = parseLocalDate(text, YMD_COMPACT);
        if (parsed != null) return parsed;
        parsed = parseLocalDateTime(text, YMD_HMS_DASH);
        if (parsed != null) return parsed;
        parsed = parseLocalDateTime(text, YMD_HMS_SLASH);
        if (parsed != null) return parsed;
        return parseLocalDateTime(text, YMD_T_HMS);
    }

    private static LocalDate parseLocalDate(String text, DateTimeFormatter formatter) {
        try {
            return LocalDate.parse(text, formatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate parseLocalDateTime(String text, DateTimeFormatter formatter) {
        try {
            return LocalDateTime.parse(text, formatter).toLocalDate();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
