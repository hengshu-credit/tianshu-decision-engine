package com.hengshucredit.rule.server.sql;

import org.junit.Assert;
import org.junit.Test;

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
