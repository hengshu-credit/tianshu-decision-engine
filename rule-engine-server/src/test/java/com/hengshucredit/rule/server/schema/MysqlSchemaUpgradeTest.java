package com.hengshucredit.rule.server.schema;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.Assert.*;

public class MysqlSchemaUpgradeTest {
    @Test public void readsEveryTableAndKeepsQuotedSqlAndNestedExpressions() throws Exception {
        var script = new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8);
        var tables = MysqlTableDefinition.statements(script).stream().filter(s -> s.startsWith("CREATE TABLE"))
                .map(MysqlTableDefinition::parse).toList();
        assertEquals(script.lines().filter(s -> s.startsWith("CREATE TABLE IF NOT EXISTS")).count(), tables.size());
        var log = tables.stream().filter(t -> t.name.equals("rule_execution_log")).findFirst().orElseThrow();
        assertTrue(log.partition.contains("PARTITION p_future"));
        assertTrue(log.indexes.get("idx_rule_set_stats_pending").contains("`rule_set_stats_version`"));
        var sql = "-- 示例;\nCREATE TABLE `a` (`c` varchar(30) DEFAULT 'x,);''z' COMMENT '分号; --',"
                + " `d` int GENERATED ALWAYS AS (if((length(`c`)>2),1,0)) STORED COMMENT '生成值',"
                + " KEY `i` (`c`(3) DESC)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表;';";
        var parts = MysqlTableDefinition.statements(sql);
        assertEquals(1, parts.size());
        var t = MysqlTableDefinition.parse(parts.get(0));
        assertEquals(List.of("c", "d"), List.copyOf(t.columns.keySet()));
        assertTrue(t.columns.get("c").contains("'x,);''z'"));
        assertTrue(t.indexes.get("i").endsWith("(`c`(3) DESC)"));
        assertEquals(-1, MysqlTableDefinition.keyword("COMMENT='分区 PARTITION'", "PARTITION"));
    }
    @Test public void allowsOnlyLosslessWidening() {
        assertTrue(MysqlSchemaUpgrade.canWiden("int unsigned", "bigint unsigned"));
        assertTrue(MysqlSchemaUpgrade.canWiden("decimal(8,2)", "decimal(12,4)"));
        assertTrue(MysqlSchemaUpgrade.canWiden("varchar(64)", "varchar(128)"));
        assertFalse(MysqlSchemaUpgrade.canWiden("decimal(8,2)", "decimal(8,4)"));
        assertFalse(MysqlSchemaUpgrade.canWiden("varchar(128)", "varchar(64)"));
        assertFalse(MysqlSchemaUpgrade.canWiden("bigint unsigned", "bigint"));
        assertFalse(MysqlSchemaUpgrade.canWiden("json", "text"));
    }
}
