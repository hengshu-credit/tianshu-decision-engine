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
    public void blackUserCountDbVariableUsesJdbcPlaceholder() throws Exception {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path root = Files.isDirectory(cwd.resolve("rule-engine-server")) ? cwd : cwd.getParent();
        String fixture = Files.readString(root.resolve("docker/rule-engine-mysql/data-tianshu-example.sql"),
                StandardCharsets.UTF_8);
        String sourceConfig = extractBlackUserCountSourceConfig(fixture);

        assertTrue(sourceConfig.contains("item_content = ?"));
        assertFalse(sourceConfig.contains("item_content = %s"));
        assertTrue(sourceConfig.contains("\"params\": [\"$.mobile_no\"]"));
    }

    private String extractBlackUserCountSourceConfig(String fixture) {
        String marker = "'black_user_count','黑名单人数','black_user_count','NUMBER','DB','";
        int start = fixture.indexOf(marker);
        assertTrue("未找到 black_user_count DB 变量配置", start >= 0);
        int configStart = start + marker.length();
        int configEnd = fixture.indexOf("','','','',0,1", configStart);
        assertTrue("未找到 black_user_count source_config 结束位置", configEnd > configStart);
        return fixture.substring(configStart, configEnd);
    }

}
