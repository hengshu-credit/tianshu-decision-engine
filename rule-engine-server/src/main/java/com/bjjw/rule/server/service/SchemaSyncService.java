package com.bjjw.rule.server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regenerates specific DDL blocks in schema.sql to keep them
 * in sync with the current table structure definitions.
 */
@Service
public class SchemaSyncService {

    private static final Logger log = LoggerFactory.getLogger(SchemaSyncService.class);

    @Value("${rule.schema.path:#{null}}")
    private String schemaPath;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    private static final Pattern TABLE_BLOCK_PATTERN = Pattern.compile(
            "(-- =+\\s*\\n-- \\d+\\. %s -[^\n]*\\n-- =+\\s*\\n)(CREATE TABLE IF NOT EXISTS `%s`[\\s\\S]*?;)",
            Pattern.MULTILINE);

    private static final List<String> REF_TYPE_TABLES = Arrays.asList(
            "rule_definition_input_field",
            "rule_definition_output_field",
            "rule_model_input_field",
            "rule_model_output_field"
    );

    @PostConstruct
    public void ensureRuntimeSchema() {
        if (jdbcTemplate == null) return;
        try {
            ensureRefTypeColumns();
            ensureModelFieldForeignKeysRemoved();
            ensureDataObjectFieldUniqueKey();
        } catch (Exception e) {
            log.warn("运行时数据库结构同步失败，请检查 sql/schema.sql 与当前数据库: {}", e.getMessage());
        }
    }

    /**
     * Read the current schema.sql and return its content.
     */
    public String readSchema() throws IOException {
        Path path = resolveSchemaPath();
        if (path != null && Files.exists(path)) {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
        ClassPathResource resource = new ClassPathResource("sql/schema.sql");
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    /**
     * Replace a specific table's CREATE TABLE block in schema.sql.
     */
    public void updateTableBlock(String tableName, String newCreateStatement) throws IOException {
        Path path = resolveSchemaPath();
        if (path == null || !Files.exists(path)) return;

        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        String escaped = Pattern.quote(tableName);
        Pattern pattern = Pattern.compile(
                "(CREATE TABLE IF NOT EXISTS `" + escaped + "` \\([\\s\\S]*?\\)[^;]*;)");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            content = content.substring(0, matcher.start()) + newCreateStatement + content.substring(matcher.end());
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Trigger a full schema sync — re-read and update all managed table blocks.
     * Currently this just verifies the file is readable; the actual DDL is maintained
     * by the schema.sql file directly, which was updated at migration time.
     */
    public String syncAndGetStatus() throws IOException {
        String content = readSchema();
        boolean hasDataObject = content.contains("rule_data_object");
        boolean hasObjectField = content.contains("rule_data_object_field");

        StringBuilder sb = new StringBuilder();
        sb.append("rule_data_object: ").append(hasDataObject ? "OK" : "MISSING").append("; ");
        sb.append("rule_data_object_field: ").append(hasObjectField ? "OK" : "MISSING");
        return sb.toString();
    }

    private void ensureRefTypeColumns() {
        for (String table : REF_TYPE_TABLES) {
            if (!tableExists(table)) continue;
            if (!columnExists(table, "ref_type")) {
                jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD COLUMN `ref_type` VARCHAR(32) DEFAULT NULL COMMENT '引用类型：VARIABLE/CONSTANT/DATA_OBJECT/MODEL' AFTER `var_id`");
            }
            if (!indexExists(table, "idx_ref_type_var_id")) {
                jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD INDEX `idx_ref_type_var_id` (`ref_type`, `var_id`)");
            }
        }
    }

    private void ensureModelFieldForeignKeysRemoved() {
        dropForeignKeyIfExists("rule_model_input_field", "fk_input_var");
        dropForeignKeyIfExists("rule_model_output_field", "fk_output_var");
    }

    private void ensureDataObjectFieldUniqueKey() {
        String table = "rule_data_object_field";
        if (!tableExists(table)) return;
        List<String> expected = Arrays.asList("object_id", "parent_field_id", "var_code");
        if (indexExists(table, "uk_object_var_code")) {
            List<String> actual = indexColumns(table, "uk_object_var_code");
            if (!sameColumns(actual, expected)) {
                jdbcTemplate.execute("ALTER TABLE `" + table + "` DROP INDEX `uk_object_var_code`");
            }
        }
        if (!indexExists(table, "uk_object_var_code")) {
            jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD UNIQUE KEY `uk_object_var_code` (`object_id`, `parent_field_id`, `var_code`)");
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                new Object[]{tableName},
                Integer.class);
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                new Object[]{tableName, columnName},
                Integer.class);
        return count != null && count > 0;
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                new Object[]{tableName, indexName},
                Integer.class);
        return count != null && count > 0;
    }

    private List<String> indexColumns(String tableName, String indexName) {
        if (!indexExists(tableName, indexName)) return Collections.emptyList();
        return jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ? ORDER BY SEQ_IN_INDEX",
                new Object[]{tableName, indexName},
                String.class);
    }

    private boolean sameColumns(List<String> actual, List<String> expected) {
        if (actual == null || actual.size() != expected.size()) return false;
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).equalsIgnoreCase(actual.get(i))) return false;
        }
        return true;
    }

    private void dropForeignKeyIfExists(String tableName, String constraintName) {
        if (!tableExists(tableName)) return;
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ? AND CONSTRAINT_TYPE = 'FOREIGN KEY'",
                new Object[]{tableName, constraintName},
                Integer.class);
        if (count != null && count > 0) {
            jdbcTemplate.execute("ALTER TABLE `" + tableName + "` DROP FOREIGN KEY `" + constraintName + "`");
        }
    }

    private Path resolveSchemaPath() {
        if (schemaPath != null && !schemaPath.isEmpty()) {
            return Paths.get(schemaPath);
        }
        String userDir = System.getProperty("user.dir");
        Path candidate = Paths.get(userDir, "src", "main", "resources", "sql", "schema.sql");
        if (Files.exists(candidate)) return candidate;
        candidate = Paths.get(userDir, "rule-engine-server", "src", "main", "resources", "sql", "schema.sql");
        if (Files.exists(candidate)) return candidate;
        return null;
    }
}
