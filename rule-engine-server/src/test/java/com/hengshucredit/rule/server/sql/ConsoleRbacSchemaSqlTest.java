package com.hengshucredit.rule.server.sql;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ConsoleRbacSchemaSqlTest {

    @Test
    public void schemaDefinesCompleteConsoleAuthorizationGraph() throws Exception {
        String sql = readSchema();
        List<String> tables = List.of(
                "console_user",
                "console_role",
                "console_permission",
                "console_user_role",
                "console_role_permission",
                "console_user_permission_override",
                "console_security_audit_log",
                "console_account_token");

        for (String table : tables) {
            Assert.assertTrue("missing table " + table,
                    sql.contains("CREATE TABLE IF NOT EXISTS `" + table + "`"));
        }
        Assert.assertTrue(sql.contains(
                "UNIQUE KEY `uk_console_user_username` (`username`)"));
        Assert.assertTrue(sql.contains(
                "UNIQUE KEY `uk_console_user_role` (`user_id`, `role_id`)"));
        Assert.assertTrue(sql.contains(
                "UNIQUE KEY `uk_console_role_permission` (`role_id`, `permission_id`)"));
        Assert.assertTrue(sql.contains(
                "UNIQUE KEY `uk_console_user_permission_override` (`user_id`, `permission_id`)"));
        Assert.assertTrue(sql.contains(
                "`effect` VARCHAR(8) NOT NULL COMMENT 'ALLOW or DENY'"));
        Assert.assertTrue(sql.contains("`email`              VARCHAR(256)"));
        Assert.assertTrue(sql.contains("`registration_status` VARCHAR(16)"));
        Assert.assertTrue(sql.contains(
                "UNIQUE KEY `uk_console_account_token_hash` (`token_hash`)"));
    }

    private static String readSchema() throws Exception {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path modulePath = cwd.resolve("src/main/resources/sql/schema.sql");
        Path path = Files.isRegularFile(modulePath)
                ? modulePath
                : cwd.resolve("rule-engine-server/src/main/resources/sql/schema.sql");
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
