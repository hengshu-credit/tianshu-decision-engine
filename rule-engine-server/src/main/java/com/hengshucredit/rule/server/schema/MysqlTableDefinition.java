package com.hengshucredit.rule.server.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Separates MySQL table elements; literals and nested SQL expressions remain verbatim. */
final class MysqlTableDefinition {
    private static final String ID = "(`(?:``|[^`])+`|[A-Za-z_][A-Za-z0-9_$]*)";
    private static final Pattern HEAD = Pattern.compile("(?is)^CREATE\\s+(?:TEMPORARY\\s+)?TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?" + ID + "\\s*\\(");
    private static final Pattern COLUMN = Pattern.compile("^" + ID + "\\s+");
    private static final Pattern INDEX = Pattern.compile("(?i)^(?:(?:UNIQUE|FULLTEXT|SPATIAL)\\s+)?(?:KEY|INDEX)\\s+" + ID + "\\s*\\(");
    private static final Pattern FOREIGN = Pattern.compile("(?i)^CONSTRAINT\\s+" + ID + "\\s+FOREIGN\\s+KEY\\s*\\(");
    private static final Pattern OPTIONS = Pattern.compile("(?is)\\s*(?:(ENGINE|(?:DEFAULT\\s+)?(?:CHARSET|CHARACTER\\s+SET)|COLLATE)\\s*=?\\s*(\\w+)"
            + "|(COMMENT)\\s*=?\\s*('(?:''|\\\\.|[^'\\\\])*'))");
    final String name;
    final Map<String, String> columns = new LinkedHashMap<>();
    final Map<String, String> indexes = new LinkedHashMap<>();
    final Map<String, String> foreignKeys = new LinkedHashMap<>();
    final String options;
    final String partition;

    private MysqlTableDefinition(String sql, Matcher head, boolean live) {
        name = unquote(head.group(1));
        int start = head.end(), depth = 0, end = -1;
        for (int i = start; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (quoted(c)) i = quoteEnd(sql, i);
            else if (c == '(') depth++;
            else if ((c == ',' || c == ')') && depth == 0) {
                element(sql.substring(start, i).trim(), live);
                start = i + 1;
                if (c == ')') { end = start; break; }
            } else if (c == ')') depth--;
        }
        if (end < 0) throw new IllegalArgumentException("表定义缺少结束括号: " + name);
        String tail = sql.substring(end).trim();
        int part = keyword(tail, "PARTITION");
        options = (part < 0 ? tail : tail.substring(0, part)).trim();
        partition = part < 0 ? "" : tail.substring(part).trim();
    }

    static MysqlTableDefinition parse(String sql) { return parse(sql, false); }
    static MysqlTableDefinition parse(String sql, boolean live) {
        Matcher head = HEAD.matcher(sql);
        if (!head.find()) throw new IllegalArgumentException("无法识别目标库中的建表定义");
        return new MysqlTableDefinition(sql, head, live);
    }

    private void element(String value, boolean live) {
        Matcher m;
        if (value.matches("(?is)^PRIMARY\\s+KEY\\s*\\(.*")) put(indexes, "PRIMARY", value);
        else if ((m = INDEX.matcher(value)).find()) put(indexes, m.group(1), value);
        else if ((m = FOREIGN.matcher(value)).find()) put(foreignKeys, m.group(1), value);
        else if (value.matches("(?is)^(?:CONSTRAINT|CHECK|FOREIGN|UNIQUE|KEY|INDEX|FULLTEXT|SPATIAL)\\b.*")) {
            if (!live) throw new IllegalArgumentException("不支持的 schema 约束: " + name + ": " + value);
        } else if ((m = COLUMN.matcher(value)).find()) put(columns, m.group(1), value);
        else throw new IllegalArgumentException("不支持的表约束，请先适配结构比对: " + name + ": " + value);
    }

    private static void put(Map<String, String> parts, String key, String value) {
        if (parts.putIfAbsent(unquote(key), value) != null)
            throw new IllegalArgumentException("重复字段或约束: " + key);
    }

    String create(String table, boolean temporary, String tableOptions) {
        List<String> parts = new ArrayList<>(columns.values());
        parts.addAll(indexes.values());
        // Foreign keys are applied after every referenced table and key exists.
        return "CREATE " + (temporary ? "TEMPORARY " : "") + "TABLE " + id(table)
                + " (" + String.join(",\n", parts) + ") " + tableOptions + (temporary ? "" : " " + partition);
    }

    static String id(String identifier) { return "`" + identifier.replace("`", "``") + "`"; }
    static String unquote(String token) {
        return token.startsWith("`") ? token.substring(1, token.length() - 1).replace("``", "`") : token;
    }

    record Storage(String engine, String charset, String collation, String commentLiteral) { }

    Storage storage() {
        String engine = null, charset = null, collation = null, comment = "''";
        var matcher = OPTIONS.matcher(options);
        for (int pos = 0; pos < options.length(); pos = matcher.end()) {
            if (!matcher.region(pos, options.length()).lookingAt())
                throw new IllegalArgumentException("不支持的 schema 表选项: " + name + " " + options.substring(pos));
            if (matcher.group(3) != null) comment = matcher.group(4);
            else if ("ENGINE".equalsIgnoreCase(matcher.group(1))) engine = matcher.group(2);
            else if ("COLLATE".equalsIgnoreCase(matcher.group(1))) collation = matcher.group(2);
            else charset = matcher.group(2);
        }
        if (!"InnoDB".equalsIgnoreCase(engine) || charset == null)
            throw new IllegalArgumentException(name + ": 自动升级要求显式声明 InnoDB 和字符集");
        return new Storage(engine, charset, collation, comment);
    }

    /** Split the packaged script, not SHOW CREATE: ordinary comments may contain arbitrary SQL. */
    static List<String> statements(String script) {
        List<String> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            if (quoted(c)) {
                int end = quoteEnd(script, i);
                sql.append(script, i, end + 1); i = end;
            } else if (c == '#' || (script.startsWith("--", i) && i + 2 < script.length() && Character.isWhitespace(script.charAt(i + 2)))) {
                int end = script.indexOf('\n', i);
                i = end < 0 ? script.length() : end; sql.append('\n');
            } else if (script.startsWith("/*", i)) {
                int end = script.indexOf("*/", i + 2);
                if (end < 0 || script.startsWith("/*!", i)) throw new IllegalArgumentException("不支持的 schema 块注释或条件 SQL");
                i = end + 1; sql.append(' ');
            } else if (c == ';') {
                addStatement(result, sql);
            } else sql.append(c);
        }
        addStatement(result, sql);
        return result;
    }

    private static void addStatement(List<String> out, StringBuilder sql) {
        String value = sql.toString().trim();
        if (!value.isEmpty()) out.add(value);
        sql.setLength(0);
    }

    static int keyword(String text, String word) {
        for (int i = 0; i < text.length(); i++) {
            if (quoted(text.charAt(i))) i = quoteEnd(text, i);
            else if (text.regionMatches(true, i, word, 0, word.length())
                    && (i == 0 || !wordChar(text.charAt(i - 1)))
                    && (i + word.length() == text.length() || !wordChar(text.charAt(i + word.length())))) return i;
        }
        return -1;
    }

    private static boolean wordChar(char c) { return Character.isLetterOrDigit(c) || c == '_'; }
    private static boolean quoted(char c) { return c == '\'' || c == '"' || c == '`'; }

    private static int quoteEnd(String text, int start) {
        char q = text.charAt(start);
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && q != '`') i++;
            else if (c == q) {
                if (i + 1 >= text.length() || text.charAt(i + 1) != q) return i;
                i++;
            }
        }
        throw new IllegalArgumentException("schema 包含未闭合的字符串或标识符");
    }
}
