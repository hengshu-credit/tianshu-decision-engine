package com.hengshucredit.rule.server.sql;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.service.SystemConstantPolicy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class BuiltInConstantSqlTest {

    @Test
    public void defaultSchemaAndUpgradeSeedReadOnlyDecisionConstantsIdempotently() throws Exception {
        String seed = decisionSeed(readResource("sql/schema.sql"));
        Assert.assertEquals(seed, decisionSeed(readResource("sql/migrations/2026-11-11-decision-result-system-constants.sql")));
        JdbcTemplate jdbc = decisionDatabase();
        executeSeed(jdbc, seed);
        var before = jdbc.queryForList("SELECT * FROM rule_variable ORDER BY sort_order");
        Assert.assertEquals(3, before.size());
        String[][] expected = {{"DECISION_PASS", "通过", "0"}, {"DECISION_REVIEW", "人审", "2"}, {"DECISION_REJECT", "拒绝", "1"}};
        for (int i = 0; i < expected.length; i++) {
            var row = before.get(i);
            Assert.assertEquals(expected[i][0], row.get("VAR_CODE"));
            Assert.assertEquals(expected[i][1], row.get("VAR_LABEL"));
            Assert.assertEquals(expected[i][2], row.get("DEFAULT_VALUE"));
            Assert.assertEquals("NUMBER", row.get("VAR_TYPE"));
            Assert.assertEquals("CONSTANT", row.get("VAR_SOURCE"));
            Assert.assertEquals("GLOBAL", row.get("SCOPE"));
            Assert.assertEquals(0L, ((Number) row.get("PROJECT_ID")).longValue());
            Assert.assertEquals(1, ((Number) row.get("STATUS")).intValue());
            Assert.assertEquals(1, ((Number) row.get("SYSTEM_CONSTANT")).intValue());
            var stored = new RuleVariable();
            stored.setSystemConstant(((Number) row.get("SYSTEM_CONSTANT")).intValue() == 1);
            Assert.assertThrows(IllegalArgumentException.class, () -> SystemConstantPolicy.requireMutable(stored));
        }
        executeSeed(jdbc, seed);
        Assert.assertEquals("Repeated startup must preserve IDs, values and timestamps", before,
                jdbc.queryForList("SELECT * FROM rule_variable ORDER BY sort_order"));
    }

    @Test
    public void decisionSeedDoesNotOverwriteOrLockExistingBusinessFields() throws Exception {
        JdbcTemplate jdbc = decisionDatabase();
        jdbc.update("INSERT INTO rule_variable (id, project_id, scope, var_code, var_label, var_type, var_source, default_value, system_constant, status) "
                + "VALUES (41, 0, 'GLOBAL', 'DECISION_PASS', '客户自定义', 'STRING', 'INPUT', 'CUSTOM', 0, 0)");
        jdbc.update("INSERT INTO rule_variable (id, project_id, scope, var_code, var_label, var_type, var_source, default_value, system_constant, status) "
                + "VALUES (42, 9, 'PROJECT', 'DECISION_REVIEW', '项目自定义', 'STRING', 'CONSTANT', 'MANUAL', 0, 1)");
        var existing = jdbc.queryForList("SELECT * FROM rule_variable ORDER BY id");
        executeSeed(jdbc, decisionSeed(readResource("sql/schema.sql")));
        executeSeed(jdbc, decisionSeed(readResource("sql/migrations/2026-11-11-decision-result-system-constants.sql")));
        Assert.assertEquals(existing, jdbc.queryForList("SELECT * FROM rule_variable WHERE id IN (41, 42) ORDER BY id"));
        Assert.assertEquals(Integer.valueOf(4), jdbc.queryForObject("SELECT COUNT(*) FROM rule_variable", Integer.class));
    }

    private JdbcTemplate decisionDatabase() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:decision_constants_" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE rule_variable (id BIGINT AUTO_INCREMENT PRIMARY KEY, system_constant TINYINT DEFAULT 0, "
                + "project_id BIGINT, scope VARCHAR(16), var_code VARCHAR(128), var_label VARCHAR(128), script_name VARCHAR(128), "
                + "var_type VARCHAR(32), var_source VARCHAR(32), default_value VARCHAR(512), value_range VARCHAR(512), "
                + "example_value VARCHAR(256), description VARCHAR(512), sort_order INT DEFAULT 0, status TINYINT DEFAULT 1, "
                + "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "UNIQUE(scope, project_id, var_code))");
        return jdbc;
    }

    private String decisionSeed(String sql) {
        int start = sql.indexOf("-- 决策结果系统常量：");
        int end = sql.indexOf("-- 决策结果系统常量结束。", start);
        Assert.assertTrue("Decision constants must be in default schema and upgrade script", start >= 0 && end > start);
        return sql.substring(start, end).replace("\r\n", "\n");
    }

    private void executeSeed(JdbcTemplate jdbc, String sql) {
        for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
    }

    private static final List<String> SQL_RESOURCES = Arrays.asList(
            "sql/schema.sql", "docker/rule-engine-mysql/data-tianshu-example.sql");
    private static final List<String> CONSTANT_CODES = Arrays.asList(
            "NULL_VALUE", "EMPTY_STRING", "EMPTY_LIST", "EMPTY_MAP",
            "TRUE_VALUE", "FALSE_VALUE", "ZERO", "ONE", "NEGATIVE_ONE",
            "POSITIVE_INFINITY", "NEGATIVE_INFINITY");
    private static final List<String> OBSOLETE_CODES = Arrays.asList(
            "EMPTY_OBJECT", "NULL_STRING", "NULL_NUMBER", "NULL_OBJECT", "NULL_LIST", "NULL_MAP");

    @Test
    public void allInitializationSqlContainsCanonicalBuiltInConstants() throws Exception {
        for (String resource : SQL_RESOURCES) {
            String sql = readResource(resource);
            for (String code : CONSTANT_CODES) {
                Assert.assertTrue(resource + " missing " + code, sql.contains("'" + code + "'"));
            }
            Assert.assertFalse(resource + " must not seed EMPTY_OBJECT",
                    Pattern.compile("(?m)^\\s*\\([^\\r\\n]*'EMPTY_OBJECT'").matcher(sql).find());
            for (String obsoleteCode : OBSOLETE_CODES) {
                if (resource.startsWith("docker/")) {
                    Assert.assertFalse(resource + " must not delete obsolete " + obsoleteCode,
                            sql.contains("'" + obsoleteCode + "'"));
                } else {
                    Assert.assertFalse(resource + " must not seed obsolete " + obsoleteCode,
                            sql.contains("'" + obsoleteCode + "'"));
                }
            }
            Assert.assertFalse(resource + " must store a real empty string",
                    Pattern.compile("EMPTY_STRING[^\\r\\n]*'\\\"\\\"'").matcher(sql).find());
            if (resource.startsWith("docker/")) {
                Assert.assertTrue(resource + " must explicitly skip existing records",
                        sql.contains("INSERT INTO rule_variable") && sql.contains("WHERE NOT EXISTS"));
            } else {
                Assert.assertTrue(resource + " must use the variable unique index for idempotence",
                        sql.toLowerCase().contains("on duplicate key update")
                                && sql.contains("UNIQUE KEY `uk_scope_project_var`"));
            }
        }
    }

    @Test
    public void tianshuExamplePreservesExistingDataExceptExactLegacySeedReferences() throws Exception {
        String sql = readResource("docker/rule-engine-mysql/data-tianshu-example.sql");
        Assert.assertTrue(sql.startsWith("SET NAMES utf8mb4;"));
        Assert.assertTrue(sql.contains("SET character_set_connection = utf8mb4;"));
        Assert.assertTrue(sql.contains("SET collation_connection = utf8mb4_unicode_ci;"));
        Assert.assertFalse(Pattern.compile("(?im)^\\s*(DELETE|REPLACE|TRUNCATE|CREATE|ALTER|DROP)\\b")
                .matcher(sql).find());
        Assert.assertFalse(sql.toUpperCase().contains("ON DUPLICATE KEY UPDATE"));
        var updates = Pattern.compile("(?im)^UPDATE (\\w+)").matcher(sql);
        int count = 0;
        while (updates.find()) {
            count++;
            Assert.assertEquals("rule_variable", updates.group(1));
            String update = sql.substring(updates.start(), sql.indexOf(';', updates.start()));
            Assert.assertTrue(update.contains("source_config = CAST("));
            Assert.assertTrue(update.contains("`create_time` <=>"));
            Assert.assertTrue(update.contains("`update_time` <=>"));
            Assert.assertTrue(update.contains("`var_label` <=>"));
            Assert.assertTrue(update.contains("WHERE id = @example_variable_"));
        }
        Assert.assertEquals("Only the three original dependency-specific seeds may be repaired", 3, count);
        Assert.assertTrue(sql.contains("START TRANSACTION;"));
        Assert.assertTrue(sql.contains("COMMIT;"));
        Assert.assertTrue(sql.contains("GET_LOCK(@example_init_lock, 120)"));
    }

    private String readResource(String name) throws Exception {
        if (name.startsWith("docker/")) {
            Path cwd = Paths.get("").toAbsolutePath().normalize();
            Path root = Files.isDirectory(cwd.resolve("rule-engine-server")) ? cwd : cwd.getParent();
            return Files.readString(root.resolve(name), StandardCharsets.UTF_8);
        }
        InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
        Assert.assertNotNull("Missing SQL resource " + name, input);
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
