package com.hengshucredit.rule.server.sql;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/** 从正式 schema 建立用例所需的表及其外键父表，保留约束与生成列表达式。 */
public final class TestSchemaTables {
    private static final Pattern TABLE = Pattern.compile(
            "(?ms)^CREATE TABLE IF NOT EXISTS `(\\w+)` \\(.*?^\\) ENGINE=InnoDB.*?;");
    private static final Pattern PARENT = Pattern.compile("REFERENCES `(\\w+)`");

    private TestSchemaTables() {}

    public static void create(JdbcTemplate jdbc, Collection<String> tables) throws IOException {
        String schema = new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8);
        Map<String, String> definitions = new HashMap<>();
        var matcher = TABLE.matcher(schema);
        while (matcher.find()) definitions.put(matcher.group(1), matcher.group());
        boolean h2 = Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                connection -> "H2".equals(connection.getMetaData().getDatabaseProductName())));
        Set<String> visited = new HashSet<>();
        for (String table : tables) create(jdbc, table, definitions, visited, h2);
    }

    private static void create(JdbcTemplate jdbc, String table, Map<String, String> definitions,
                               Set<String> visited, boolean h2) {
        if (!visited.add(table)) return;
        String ddl = definitions.get(table);
        if (ddl == null) throw new IllegalArgumentException("schema.sql 缺少测试表 " + table);
        var parents = PARENT.matcher(ddl);
        while (parents.find()) create(jdbc, parents.group(1), definitions, visited, h2);
        if (h2) {
            // H2 的索引名库内唯一，生成列使用 AS (...)，模型编码范围表达式用 CASEWHEN。
            ddl = ddl.replaceFirst("(?s)\\) ENGINE=.*", ")")
                    .replace(") STORED COMMENT", ") COMMENT")
                    .replace("AS (IF(", "AS (CASEWHEN(")
                    .replaceAll("(?i)(\\b(?:KEY|INDEX)\\s+`)([^`]+)(`)", "$1" + table + "_$2$3");
            if (table.equals("rule_external_api_config") || table.equals("rule_external_datasource")) {
                // 凭据持久化的 H2 夹具按文本往返 JSON，MySQL 集成用例覆盖原生 JSON。
                ddl = ddl.replaceAll("(?i)(`\\s+)JSON\\b", "$1LONGTEXT");
            }
        }
        jdbc.execute(ddl);
    }
}
