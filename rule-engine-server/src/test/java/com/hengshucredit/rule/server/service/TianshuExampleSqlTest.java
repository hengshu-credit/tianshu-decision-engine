package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TianshuExampleSqlTest {

    @Test
    public void blackUserCountDbVariableUsesJdbcPlaceholder() throws Exception {
        String fixture = readResource("sql/data-tianshu-example.sql");
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

    private String readResource(String path) throws Exception {
        InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        assertTrue("未找到资源: " + path, input != null);
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
