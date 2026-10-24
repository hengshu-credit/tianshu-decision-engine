package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TianshuExampleSqlTest {

    @Test
    public void blacklistVariableUsesTypedReferencesAndCurrentListConfiguration() throws Exception {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path root = Files.isDirectory(cwd.resolve("rule-engine-server")) ? cwd : cwd.getParent();
        String fixture = Files.readString(root.resolve("docker/rule-engine-mysql/data-tianshu-example.sql"),
                StandardCharsets.UTF_8);
        int start = fixture.indexOf("-- rule_variable: black_hit");
        int end = fixture.indexOf("SET @example_variable_194", start);
        assertTrue(start >= 0 && end > start);
        String sourceConfig = fixture.substring(start, end);
        assertTrue(sourceConfig.contains("\"queryOperands\":[{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\""));
        assertTrue(sourceConfig.contains("\"combinationMode\":\"ANY_FIELD_ANY_LIST\""));
        assertTrue(sourceConfig.contains("CAST(@example_list_library_1 AS UNSIGNED)"));
        assertTrue(sourceConfig.contains("CAST(@example_variable_7 AS UNSIGNED)"));
        assertFalse(sourceConfig.contains("\"listId\""));
        assertFalse(sourceConfig.contains("\"queryField\""));
    }

    @Test
    public void blackUserCountDbVariableUsesJdbcPlaceholder() throws Exception {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path root = Files.isDirectory(cwd.resolve("rule-engine-server")) ? cwd : cwd.getParent();
        String fixture = Files.readString(root.resolve("docker/rule-engine-mysql/data-tianshu-example.sql"),
                StandardCharsets.UTF_8);
        String sourceConfig = extractBlackUserCountSourceConfig(fixture);

        assertTrue(sourceConfig.contains("item_content = ?"));
        assertFalse(sourceConfig.contains("item_content = %s"));
        assertTrue(sourceConfig.contains("\"params\": [{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":7"));
        assertFalse(sourceConfig.contains("$.mobile_no"));
        assertTrue("The old export ID must be replaced with the current database variable ID",
                sourceConfig.contains("CAST(@example_variable_7 AS UNSIGNED)"));
        assertTrue("The SQL list identity must also follow the imported library",
                sourceConfig.contains("CAST(@example_list_library_1 AS CHAR)"));
    }

    private String extractBlackUserCountSourceConfig(String fixture) {
        String marker = "-- rule_variable: black_user_count";
        int start = fixture.indexOf(marker);
        assertTrue("未找到 black_user_count DB 变量配置", start >= 0);
        int configStart = start + marker.length();
        int configEnd = fixture.indexOf("SET @example_variable_196", configStart);
        assertTrue("未找到 black_user_count source_config 结束位置", configEnd > configStart);
        return fixture.substring(configStart, configEnd);
    }

}
