package com.hengshucredit.rule.server.service;

import java.math.BigDecimal;
import java.util.Map;

/** 数据库变量与在线查询共用的查询限制；行数、内容和超时为0时分别表示不设该项限制。 */
public record DatabaseQueryOptions(int maxRows, int queryTimeoutSeconds, int maxContentBytes) {
    public DatabaseQueryOptions(int maxRows, int queryTimeoutSeconds) {
        this(maxRows, queryTimeoutSeconds, 0);
    }

    public DatabaseQueryOptions {
        if (maxRows < 0 || queryTimeoutSeconds < 0 || maxContentBytes < 0) {
            throw new IllegalArgumentException("查询行数、内容大小和超时必须是大于或等于 0 的整数");
        }
    }

    public static DatabaseQueryOptions from(Map<String, Object> config, int defaultRows) {
        return new DatabaseQueryOptions(integer(config.get("maxRows"), defaultRows, "最多返回行数"),
                integer(config.get("queryTimeoutSeconds"), 0, "查询超时秒数"),
                integer(config.get("maxContentBytes"), 0, "查询内容大小"));
    }

    int timeoutWithinBudget(int remainingMillis) {
        int remainingSeconds = (int) Math.max(1L, (remainingMillis + 999L) / 1000L);
        return queryTimeoutSeconds == 0 ? remainingSeconds : Math.min(queryTimeoutSeconds, remainingSeconds);
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
