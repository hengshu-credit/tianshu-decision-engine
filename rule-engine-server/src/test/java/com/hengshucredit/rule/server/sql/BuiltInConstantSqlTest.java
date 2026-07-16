package com.hengshucredit.rule.server.sql;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class BuiltInConstantSqlTest {

    private static final List<String> SQL_RESOURCES = Arrays.asList(
            "sql/data-example.sql", "sql/data-tianshu-example.sql");
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
                Assert.assertTrue(resource + " must clean obsolete " + obsoleteCode,
                        Pattern.compile("(?s)DELETE FROM.{0,500}'" + obsoleteCode + "'").matcher(sql).find());
            }
            Assert.assertFalse(resource + " must store a real empty string",
                    Pattern.compile("EMPTY_STRING[^\\r\\n]*'\\\"\\\"'").matcher(sql).find());
            Assert.assertTrue(resource + " must use idempotent upsert",
                    sql.toUpperCase().contains("ON DUPLICATE KEY UPDATE"));
        }
    }

    private String readResource(String name) throws Exception {
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
