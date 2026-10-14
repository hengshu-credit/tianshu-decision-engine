package com.hengshucredit.rule.server.sql;

import org.junit.Assert;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DatabaseInitializationSqlTest {

    private static final Pattern DATA_MANIPULATION = Pattern.compile(
            "(?im)^\\s*(INSERT|DELETE|UPDATE|REPLACE)\\b");
    private static final Pattern EXAMPLE_INSERT = Pattern.compile(
            "(?im)^INSERT(?:\\s+IGNORE)?\\s+INTO\\s+`?([a-z0-9_]+)`?");
    private static final Pattern TABLE_BLOCK = Pattern.compile(
            "(?is)CREATE TABLE IF NOT EXISTS\\s+`([^`]+)`\\s*\\((.*?)\\)\\s*ENGINE=");
    private static final Pattern MOJIBAKE = Pattern.compile(
            "(?:Ã[\\u0080-\\u024F]|Â[\\u0080-\\u024F]|"
                    + "â[\\u0080-\\u024F\\u2000-\\u20FF]|"
                    + "[åæçèéä][\\u0080-\\u024F\\u2000-\\u20FF]|"
                    + "�|锟斤拷|烫烫烫|屯屯屯)");
    @Test
    public void schemaContainsOnlyIdempotentConstantInserts() throws Exception {
        String schema = read(sqlDirectory().resolve("schema.sql"));
        String withoutComments = schema
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)--.*$", "");
        Matcher matcher = DATA_MANIPULATION.matcher(withoutComments);
        while (matcher.find()) {
            Assert.assertEquals("schema.sql may only insert missing system constants",
                    "INSERT", matcher.group(1).toUpperCase());
            int statementEnd = withoutComments.indexOf(';', matcher.start());
            Assert.assertTrue("schema.sql constant insert must be terminated", statementEnd > matcher.start());
            String statement = withoutComments.substring(matcher.start(), statementEnd);
            Assert.assertTrue("schema.sql must only seed rule_variable constants",
                    statement.toLowerCase().contains("insert into `rule_variable`"));
            Assert.assertTrue("schema.sql constant insert must use the variable unique index",
                    statement.toLowerCase().contains("on duplicate key update")
                            && schema.contains("UNIQUE KEY `uk_scope_project_var`"));
        }
    }

    @Test
    public void canonicalInitializationSqlContainsNoMojibake() throws Exception {
        for (Path sql : Arrays.asList(sqlDirectory().resolve("schema.sql"),
                repositoryRoot().resolve("docker/rule-engine-mysql/data-tianshu-example.sql"))) {
            Matcher matcher = MOJIBAKE.matcher(read(sql));
            Assert.assertFalse(sql.getFileName() + " contains mojibake near "
                    + (matcher.find() ? matcher.group() : ""), matcher.reset().find());
        }
    }

    @Test
    public void schemaDoesNotCreateOrAlterDatabaseAccounts() throws Exception {
        String schema = read(sqlDirectory().resolve("schema.sql")).toUpperCase();
        Assert.assertFalse(schema.contains("CREATE USER"));
        Assert.assertFalse(schema.contains("ALTER USER"));
        Assert.assertFalse(schema.contains("GRANT ALL PRIVILEGES"));
    }

    @Test
    public void schemaUsesTheSelectedDatabaseAndIdempotentCompatibilityGuards() throws Exception {
        String schema = read(sqlDirectory().resolve("schema.sql"));
        Assert.assertTrue(schema.startsWith(
                "SET NAMES utf8mb4;\nSET character_set_connection = utf8mb4;"));
        Assert.assertTrue("schema must guard legacy log columns before creating views",
                schema.contains("@schema_log_view_sql = IF(")
                        && schema.contains("column_name IN ('trace_id', 'attempt_no')"));
        Assert.assertTrue("schema must include both logical read views",
                schema.contains("VIEW `rule_execution_log_current`")
                        && schema.contains("VIEW `rule_execution_statistics_current`"));
        Assert.assertFalse("schema must not create procedures or change the SQL delimiter",
                schema.contains("CREATE PROCEDURE") || schema.contains("DELIMITER"));
        Assert.assertFalse("schema must not force the default database",
                schema.contains("CREATE DATABASE") || schema.matches("(?is).*\\nUSE\\s+.*"));
        Assert.assertFalse("schema objects must use the connection database",
                schema.contains("rule_engine."));
    }

    @Test
    public void dockerDelegatesSchemaToServerWithoutAutomaticDataImport() throws Exception {
        Path root = repositoryRoot();
        for (String file : Arrays.asList("docker-compose.yml", "docker-compose.full.yml", "docker-compose.mysql.yml")) {
            String compose = read(root.resolve("docker").resolve(file));
            Assert.assertFalse(file + " must not execute SQL in MySQL entrypoint", compose.contains("/docker-entrypoint-initdb.d/"));
            Assert.assertFalse(file + " must not use a separate SQL initializer", compose.contains("mysql-init:"));
            Assert.assertFalse(file + " must not replay snapshots", compose.contains("MYSQL_INIT_LOAD_SNAPSHOT"));
            Map<?, ?> configuration = new Yaml().load(compose);
            Map<?, ?> services = (Map<?, ?>) configuration.get("services");
            if (!file.equals("docker-compose.mysql.yml")) {
                Map<?, ?> server = (Map<?, ?>) services.get("server");
                Map<?, ?> environment = (Map<?, ?>) server.get("environment");
                Assert.assertEquals("${SPRING_SQL_INIT_MODE:-always}", environment.get("SPRING_SQL_INIT_MODE"));
                if (file.equals("docker-compose.full.yml")) {
                    Map<?, ?> dependencies = (Map<?, ?>) server.get("depends_on");
                    for (String service : Arrays.asList("mysql", "redis")) {
                        Assert.assertTrue(services.containsKey(service));
                        Assert.assertEquals("service_healthy", ((Map<?, ?>) dependencies.get(service)).get("condition"));
                    }
                    Assert.assertTrue(compose.contains("example-data-import:"));
                    Assert.assertTrue(compose.contains("profiles: [example-data]"));
                    Assert.assertTrue(compose.contains("/init/data-tianshu-example.sql"));
                }
            } else {
                Assert.assertTrue(compose.contains("example-data-import:"));
                Assert.assertTrue(compose.contains("profiles: [example-data]"));
                Assert.assertTrue(compose.contains("/init/data-tianshu-example.sql"));
            }
        }
    }

    @Test
    public void tianshuExampleContainsOnlyBusinessConfigurationData() throws Exception {
        String sql = read(repositoryRoot().resolve("docker/rule-engine-mysql/data-tianshu-example.sql"));
        Set<String> tables = collectTables(EXAMPLE_INSERT, sql);
        Set<String> forbidden = new HashSet<>(Arrays.asList(
                "rule_execution_log", "rule_runtime_call_log", "rule_list_record_log", "rule_function"));
        Assert.assertTrue("sample must include field validation rules",
                tables.contains("rule_field_validation"));
        Assert.assertTrue("sample must include models", tables.contains("rule_model"));
        Assert.assertTrue("sample must include lists", tables.contains("rule_list_library"));
        Assert.assertTrue("sample must include external API configuration",
                tables.contains("rule_external_datasource") && tables.contains("rule_external_api_config"));
        Assert.assertTrue("sample must include split experiment configuration",
                tables.contains("rule_experiment") && tables.contains("rule_experiment_group"));
        Assert.assertTrue("sample must include billing configuration",
                tables.contains("rule_billing_config"));
        Assert.assertTrue("sample must not include runtime logs or unrelated metadata",
                Collections.disjoint(tables, forbidden));
    }

    @Test
    public void realOnnxAssetsAreOnlyEnabledByTheExplicitIntegrationProfile() throws Exception {
        String pom = read(repositoryRoot().resolve("rule-engine-server/pom.xml"));

        Assert.assertEquals("the ignored repository asset directory must not be a default test resource", 1,
                occurrences(pom, "${project.basedir}/../assets"));
        Assert.assertTrue(pom.contains("<id>onnx-integration</id>"));
        Assert.assertTrue(pom.contains("<tianshu.onnx.integration>true</tianshu.onnx.integration>"));
        Assert.assertTrue(pom.contains("<tianshu.onnx.integration>"
                + "${tianshu.onnx.integration}</tianshu.onnx.integration>"));
    }

    @Test
    public void schemaCreatesConsoleUserPreferenceTable() throws Exception {
        String schema = read(sqlDirectory().resolve("schema.sql"));
        Matcher matcher = TABLE_BLOCK.matcher(schema);
        String tableBody = null;
        while (matcher.find()) {
            if ("console_user_preference".equals(matcher.group(1))) {
                tableBody = matcher.group(2);
                break;
            }
        }

        Assert.assertNotNull("console_user_preference table missing", tableBody);
        Assert.assertTrue(tableBody.matches(
                "(?is).*`preference_value`\\s+JSON\\s+NOT NULL.*"));
        Assert.assertTrue(schema.contains(
                "UNIQUE KEY `uk_console_user_preference` (`user_id`, `preference_key`)"));
        Assert.assertTrue(schema.contains(
                "KEY `idx_console_user_preference_user` (`user_id`)"));
    }

    @Test
    public void executionLogSupportsLargeJsonPayloadsAndUpgradesExistingSchema() throws Exception {
        String schema = read(sqlDirectory().resolve("schema.sql"));
        Matcher matcher = TABLE_BLOCK.matcher(schema);
        String tableBody = null;
        while (matcher.find()) {
            if ("rule_execution_log".equals(matcher.group(1))) {
                tableBody = matcher.group(2);
                break;
            }
        }

        Assert.assertNotNull("rule_execution_log table missing", tableBody);
        Assert.assertTrue(tableBody.matches("(?is).*`input_params`\\s+LONGTEXT.*"));
        Assert.assertTrue(tableBody.matches("(?is).*`output_result`\\s+LONGTEXT.*"));
        Assert.assertFalse(schema.contains("MODIFY COLUMN `input_params`"));
        Assert.assertFalse(schema.contains("MODIFY COLUMN `output_result`"));
    }

    @Test
    public void schemaCreatesApiDocumentationScenarioTable() throws Exception {
        String schema = read(sqlDirectory().resolve("schema.sql"));
        Matcher matcher = TABLE_BLOCK.matcher(schema);
        String tableBody = null;
        while (matcher.find()) {
            if ("rule_api_doc_scenario".equals(matcher.group(1))) {
                tableBody = matcher.group(2);
                break;
            }
        }

        Assert.assertNotNull("rule_api_doc_scenario table missing", tableBody);
        Assert.assertTrue(tableBody.matches("(?is).*`request_json`\\s+LONGTEXT\\s+NOT NULL.*"));
        Assert.assertTrue(tableBody.matches("(?is).*`response_json`\\s+LONGTEXT\\s+NOT NULL.*"));
        Assert.assertTrue(schema.contains(
                "UNIQUE KEY `uk_api_doc_scenario_name` (`definition_id`, `scenario_name`)"));
        Assert.assertTrue(schema.contains(
                "KEY `idx_api_doc_scenario_export` (`definition_id`, `status`, `include_in_doc`, `sort_order`)"));
        Assert.assertFalse(schema.contains("MODIFY COLUMN `request_json`"));
        Assert.assertFalse(schema.contains("MODIFY COLUMN `response_json`"));
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static Set<String> collectTables(Pattern pattern, String sql) {
        Set<String> tables = new HashSet<>();
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }

    private static Path sqlDirectory() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path modulePath = cwd.resolve("src/main/resources/sql");
        if (Files.isDirectory(modulePath)) {
            return modulePath;
        }
        return cwd.resolve("rule-engine-server/src/main/resources/sql");
    }

    private static Path repositoryRoot() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        return Files.isDirectory(cwd.resolve("rule-engine-server")) ? cwd : cwd.getParent();
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
