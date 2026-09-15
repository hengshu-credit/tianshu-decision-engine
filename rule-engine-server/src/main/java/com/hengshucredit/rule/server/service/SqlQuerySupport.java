package com.hengshucredit.rule.server.service;

import java.util.Map;
import java.util.regex.Pattern;

/** JDBC 查询与数据库变量共用的词法检查；契约语料同时由前端测试执行。 */
public final class SqlQuerySupport {
    private static final Pattern DOLLAR_QUOTE = Pattern.compile("^\\$(?:[A-Za-z_][A-Za-z0-9_]*)?\\$");
    private static final Pattern SELECT = Pattern.compile("^select\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WRITE_OR_LOCK = Pattern.compile(
            "\\binto\\b|\\bfor\\s+(?:update|share)\\b|\\block\\s+in\\s+share\\s+mode\\b", Pattern.CASE_INSENSITIVE);

    private SqlQuerySupport() { }

    public record Analysis(String code, int placeholderCount, boolean invalid, int terminator) { }

    public static Analysis analyze(String sql) {
        String text = sql == null ? "" : sql;
        StringBuilder code = new StringBuilder();
        int count = 0;
        int terminator = -1;
        boolean invalid = false;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
            if ((current == '-' && next == '-') || current == '#') {
                while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r') i++;
                code.append(' ');
            } else if (current == '/' && next == '*') {
                if (text.startsWith("/*!", i) || text.regionMatches(true, i, "/*M!", 0, 4)) invalid = true;
                int end = text.indexOf("*/", i + 2);
                if (end < 0) { invalid = true; break; }
                if (text.substring(i + 2, end).contains("/*")) invalid = true;
                i = end + 1;
                code.append(' ');
            } else {
                var dollar = current == '$' ? DOLLAR_QUOTE.matcher(text).region(i, text.length()) : null;
                boolean isDollar = dollar != null && dollar.lookingAt();
                boolean isOracle = (current == 'q' || current == 'Q') && next == '\'' && i + 2 < text.length();
                if (isDollar || isOracle) {
                    char opener = isOracle ? text.charAt(i + 2) : '\0';
                    String ending = isDollar ? dollar.group() : Map.of('[', ']', '(', ')', '{', '}', '<', '>').getOrDefault(opener, opener) + "'";
                    int start = i + (isDollar ? ending.length() : 3);
                    int end = text.indexOf(ending, start);
                    if (end < 0) { invalid = true; break; }
                    i = end + ending.length() - 1;
                    code.append(" literal ");
                } else if (current == '\'' || current == '"' || current == '`' || current == '[') {
                    char ending = current == '[' ? ']' : current;
                    boolean closed = false;
                    for (i++; i < text.length(); i++) {
                        char value = text.charAt(i);
                        char after = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
                        if (value == ending && after == ending) { i++; continue; }
                        if (value == '\\' && current != '[') {
                            if (after == ending || after == '\\') invalid = true;
                            i++;
                        } else if (value == ending) { closed = true; break; }
                    }
                    if (!closed) invalid = true;
                    code.append(" literal ");
                } else {
                    code.append(current);
                    if (current == '?') count++;
                    if (current == ';') terminator = i;
                }
            }
        }
        return new Analysis(code.toString().trim(), count, invalid, terminator);
    }

    public static boolean isReadOnlySelect(String sql) {
        Analysis analysis = analyze(sql);
        return !analysis.invalid() && SELECT.matcher(analysis.code()).find()
                && analysis.code().matches("(?s)^[^;]*;?$")
                && !WRITE_OR_LOCK.matcher(analysis.code()).find();
    }

    public static String executableSql(String sql) {
        if (!isReadOnlySelect(sql)) throw new IllegalArgumentException("只允许单条只读 SELECT 查询，请检查引号、注释和锁定语句");
        int terminator = analyze(sql).terminator();
        return terminator < 0 ? sql : sql.substring(0, terminator) + sql.substring(terminator + 1);
    }
}
