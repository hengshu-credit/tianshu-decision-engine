package com.hengshucredit.rule.server.schema;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Partition changes can move or discard audit rows. Compare their effective bounds, never repartition at startup. */
final class MysqlPartitionContract {
    private static final Pattern HASH = Pattern.compile("(?is)^PARTITION\\s+BY\\s+HASH\\s*\\((.+?)\\)\\s+PARTITIONS\\s+(\\d+)$");
    private static final Pattern RANGE = Pattern.compile("(?is)^PARTITION\\s+BY\\s+RANGE\\s*\\((.+?)\\)\\s*\\((.*)\\)$");
    private static final Pattern BOUND = Pattern.compile("(?is)\\s*PARTITION\\s+`?(\\w+)`?\\s+VALUES\\s+LESS\\s+THAN\\s*"
            + "(?:\\(\\s*(TO_DAYS\\s*\\(\\s*'\\d{4}-\\d{2}-\\d{2}'\\s*\\)|-?\\d+|MAXVALUE)\\s*\\)|(MAXVALUE))"
            + "(?:\\s+COMMENT\\s+'(?:''|[^'])*')?\\s*(?:,|$)");
    private record Part(String method, String expression, String name, String bound) { }

    static void verify(Connection conn, MysqlTableDefinition target, boolean partitioned) throws SQLException {
        if (target.partition.isEmpty() && !partitioned) return;
        if (target.partition.isEmpty() || !partitioned) throw MysqlSchemaUpgrade.blocked(target.name, "分区方式不同");
        List<Part> parts = new ArrayList<>();
        try (var s = conn.prepareStatement("""
                SELECT partition_method,partition_expression,partition_name,partition_description,subpartition_name
                FROM information_schema.partitions WHERE table_schema=DATABASE() AND table_name=?
                ORDER BY partition_ordinal_position
                """)) {
            s.setString(1, target.name);
            try (var r = s.executeQuery()) {
                while (r.next()) {
                    if (r.getString(5) != null) throw MysqlSchemaUpgrade.blocked(target.name, "不能自动修改子分区");
                    parts.add(new Part(r.getString(1), r.getString(2), r.getString(3), r.getString(4)));
                }
            }
        }
        var hash = HASH.matcher(target.partition);
        var range = RANGE.matcher(target.partition);
        boolean ok;
        if (hash.matches()) {
            ok = parts.size() == Integer.parseInt(hash.group(2)) && strategy(parts, "HASH", hash.group(1));
        } else if (range.matches()) {
            String body = range.group(2).trim();
            List<String> names = new ArrayList<>(), bounds = new ArrayList<>();
            var bound = BOUND.matcher(body);
            int next = 0;
            while (bound.find() && bound.start() == next) {
                next = bound.end();
                names.add(bound.group(1));
                String expression = bound.group(2) != null ? bound.group(2) : bound.group(3);
                bounds.add("MAXVALUE".equalsIgnoreCase(expression) ? "'MAXVALUE'" : expression);
            }
            if (next != body.length() || names.isEmpty()) throw MysqlSchemaUpgrade.blocked(target.name, "不支持的 RANGE 分区边界");
            ok = parts.size() == names.size() && strategy(parts, "RANGE", range.group(1));
            if (ok) try (var s = conn.createStatement(); var r = s.executeQuery("SELECT " + String.join(",", bounds))) {
                r.next();
                for (int i = 0; i < parts.size(); i++) {
                    ok &= names.get(i).equalsIgnoreCase(parts.get(i).name) && r.getString(i + 1).equals(parts.get(i).bound);
                }
            }
        } else throw MysqlSchemaUpgrade.blocked(target.name, "不支持自动比对的分区定义");
        if (!ok) throw MysqlSchemaUpgrade.blocked(target.name, "分区键、数量或边界不一致，未修改现有分区");
    }

    private static boolean strategy(List<Part> parts, String method, String expression) {
        return !parts.isEmpty() && method.equalsIgnoreCase(parts.get(0).method)
                && normalize(expression).equals(normalize(parts.get(0).expression));
    }
    private static String normalize(String expression) {
        // MySQL normalizes keyword case and identifier quotes. Never fold case inside a string literal.
        var tokens = Pattern.compile("'(?:''|\\\\.|[^'\\\\])*'|`(?:``|[^`])*`|\\S").matcher(expression);
        var normalized = new StringBuilder();
        while (tokens.find()) {
            String token = tokens.group();
            normalized.append(token.startsWith("'") ? token : MysqlTableDefinition.unquote(token).toLowerCase(Locale.ROOT));
        }
        return normalized.toString();
    }
}
