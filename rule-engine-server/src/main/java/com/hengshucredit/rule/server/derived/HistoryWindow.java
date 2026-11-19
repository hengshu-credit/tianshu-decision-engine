package com.hengshucredit.rule.server.derived;

import com.alibaba.fastjson.JSONObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Function;

/** entryCutoff 决定哪些进件已发生；from、before 则属于本次选择的统计时间轴。 */
record HistoryWindow(LocalDateTime entryCutoff, LocalDateTime from, LocalDateTime before, String fieldKey, String nullPolicy) {
    static HistoryWindow resolve(JSONObject config, LocalDateTime entry, Function<JSONObject, Object> input) {
        String unit = config.getString("windowUnit");
        if ("ALL".equals(unit)) return new HistoryWindow(entry, null, entry, null, null);
        JSONObject field = DerivedVariableConfig.windowField(config);
        String policy = config.getString("nullTimePolicy");
        LocalDateTime anchor = entry;
        if (field != null) {
            Object value = input.apply(field);
            if (empty(value) && "INCLUDE".equals(policy)) return new HistoryWindow(entry, null, entry, null, policy);
            anchor = empty(value) && "ENTRY".equals(policy) ? entry : time(value);
        }
        LocalDateTime start = anchor == null ? null : DerivedVariableService.historyStart(anchor, config.getLongValue("window"), unit);
        return new HistoryWindow(entry, start, anchor, field == null ? null : DerivedVariableConfig.fieldKey(field), policy);
    }

    boolean matches(HistoryQuery.Row row) {
        if (fieldKey == null) return true; // 进件时间窗口已由查询施加。
        if (before == null) return false;
        Object value = row.fields().get(fieldKey);
        LocalDateTime at;
        if (empty(value)) {
            if ("INCLUDE".equals(nullPolicy)) return true;
            at = "ENTRY".equals(nullPolicy) ? row.occurredAt() : null;
        } else {
            at = time(value);
        }
        return at != null && !at.isBefore(from) && at.isBefore(before);
    }

    private static boolean empty(Object value) {
        return value == null || value instanceof String text && text.isBlank();
    }

    private static LocalDateTime time(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof LocalDate date) return date.atStartOfDay();
        if (value instanceof java.util.Date date) return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());
        if (value instanceof Number number) {
            try {
                long millis = new java.math.BigDecimal(number.toString()).longValueExact();
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
            } catch (ArithmeticException | NumberFormatException invalid) { return null; }
        }
        if (!(value instanceof CharSequence)) return null;
        String text = value.toString().trim().replace(' ', 'T');
        try {
            if (text.length() == 10) return LocalDate.parse(text).atStartOfDay();
            var parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(text, OffsetDateTime::from, LocalDateTime::from);
            return parsed instanceof OffsetDateTime offset ? LocalDateTime.ofInstant(offset.toInstant(), ZoneId.systemDefault()) : (LocalDateTime) parsed;
        } catch (DateTimeParseException invalid) { return null; }
    }
}
