package com.hengshucredit.rule.server.service;

import java.math.BigDecimal;
import java.util.Map;

/** 数据库变量与在线查询共用的查询限制；0 使用 JDBC 的不限制语义。 */
public record DatabaseQueryOptions(int maxRows, int queryTimeoutSeconds) {
    public DatabaseQueryOptions {
        if (maxRows < 0 || queryTimeoutSeconds < 0) {
            throw new IllegalArgumentException("查询行数和超时必须是大于或等于 0 的整数");
        }
    }

    public static DatabaseQueryOptions from(Map<String, Object> config, int defaultRows) {
        return new DatabaseQueryOptions(integer(config.get("maxRows"), defaultRows, "最多返回行数"),
                integer(config.get("queryTimeoutSeconds"), 5, "查询超时秒数"));
    }

    private static int integer(Object value, int fallback, String label) {
        if (value == null) return fallback;
        try {
            int parsed = new BigDecimal(String.valueOf(value)).intValueExact();
            if (parsed >= 0) return parsed;
        } catch (NumberFormatException | ArithmeticException ignored) {
            // 与前端输入范围保持一致，禁止截断小数或整数溢出。
        }
        throw new IllegalArgumentException(label + "必须是 0 至 2147483647 的整数");
    }
}
