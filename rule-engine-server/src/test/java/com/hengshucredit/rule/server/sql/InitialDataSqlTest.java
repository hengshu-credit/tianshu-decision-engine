package com.hengshucredit.rule.server.sql;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InitialDataSqlTest {

    private static final Pattern DATA_MANIPULATION = Pattern.compile(
            "(?im)^\\s*(INSERT|DELETE|UPDATE|REPLACE|TRUNCATE|DROP|ALTER)\\b");

    @Test
    public void initialDataOnlyInsertsMissingRowsAndDoesNotUseNumericIds() throws Exception {
        String sql = read(repositoryRoot().resolve(
                "rule-engine-server/src/main/resources/sql/initial-data.sql"));
        Matcher statements = DATA_MANIPULATION.matcher(sql);
        while (statements.find()) {
            String statement = sql.substring(statements.start(), statementEnd(sql, statements.start()));
            Assert.assertEquals("initial-data.sql 只允许插入", "INSERT", statements.group(1).toUpperCase());
            Assert.assertFalse("initial-data.sql 不应删除或替换数据", statement.matches("(?is).*\\b(DELETE|REPLACE|TRUNCATE|DROP|ALTER)\\b.*"));
        }

        Assert.assertTrue(sql.contains("ON DUPLICATE KEY UPDATE id = id"));
        Assert.assertTrue("变量初始化必须跳过任意 GLOBAL 同编码记录",
                sql.contains("NOT EXISTS (SELECT 1 FROM rule_variable existing")
                        && sql.contains("existing.scope = 'GLOBAL' AND existing.var_code"));
        int variableStart = sql.indexOf("INSERT INTO rule_variable (");
        int variableEnd = sql.indexOf("-- 枚举选项", variableStart);
        Assert.assertTrue(variableStart >= 0 && variableEnd > variableStart);
        Assert.assertFalse("变量初始化不能只依赖 project_id 唯一键",
                sql.substring(variableStart, variableEnd).contains("ON DUPLICATE KEY UPDATE"));
        Assert.assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM rule_variable_option"));
        Assert.assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM rule_data_object_field"));
        Assert.assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM rule_data_object_field_option"));
        Assert.assertFalse("对象字段不能依赖固定对象 ID",
                Pattern.compile("(?is)INSERT INTO rule_data_object_field \\(.*?\\)\\s*VALUES\\s*\\s*\\(0,\\s*'GLOBAL',\\s*\\d+").matcher(sql).find());
        Assert.assertFalse("对象字段选项不能依赖固定字段 ID",
                Pattern.compile("(?is)INSERT INTO rule_data_object_field_option \\(.*?\\)\\s*VALUES\\s*\\s*\\(\\d+").matcher(sql).find());
    }

    @Test
    public void initialDataContainsTheExpectedBuiltInResourceKinds() throws Exception {
        String sql = read(repositoryRoot().resolve(
                "rule-engine-server/src/main/resources/sql/initial-data.sql"));
        Assert.assertTrue(sql.contains("INSERT INTO rule_data_object"));
        Assert.assertTrue(sql.contains("INSERT INTO rule_variable"));
        Assert.assertTrue(sql.contains("INSERT INTO rule_variable_option"));
        Assert.assertTrue(sql.contains("INSERT INTO rule_data_object_field"));
        Assert.assertTrue(sql.contains("INSERT INTO rule_data_object_field_option"));
        Assert.assertTrue(sql.contains("'contact'"));
        Assert.assertTrue(sql.contains("'credit_id'"));
        Assert.assertTrue(sql.contains("'authorization_for_contracts_execution'"));
    }

    private static int statementEnd(String sql, int start) {
        int end = sql.indexOf(';', start);
        Assert.assertTrue("初始化 SQL 语句必须以分号结束", end > start);
        return end;
    }

    private static Path repositoryRoot() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        return Files.isDirectory(cwd.resolve("rule-engine-server")) ? cwd : cwd.getParent();
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
