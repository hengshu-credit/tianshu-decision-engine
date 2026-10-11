package com.hengshucredit.rule.server.sql;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

public class GovernanceSchemaSqlTest {

    @Test
    public void schemaDefinesUnifiedGovernancePersistence() throws Exception {
        String sql = readSchema();
        for (String table : List.of(
                "governed_resource",
                "governed_resource_version",
                "governance_approval_request",
                "governance_approval_event",
                "governance_dependency_snapshot")) {
            Assert.assertTrue("missing table " + table,
                    sql.contains("CREATE TABLE IF NOT EXISTS `" + table + "`"));
        }
        Assert.assertTrue(sql.contains(
                "UNIQUE KEY `uk_governed_resource_identity` (`resource_type`, `resource_id`)"));
        Assert.assertTrue(sql.contains(
                "UNIQUE KEY `uk_governed_resource_version_no` (`governed_resource_id`, `version_no`)"));
        Assert.assertTrue(sql.contains(
                "UNIQUE KEY `uk_governance_active_resource` (`active_resource_key`)"));
        Assert.assertTrue(Pattern.compile(
                "`submitted_snapshot_json`\\s+LONGTEXT")
                .matcher(sql).find());
        Assert.assertTrue(sql.contains("`secret_payload_ciphertext` LONGTEXT"));
        Assert.assertTrue(Pattern.compile("`version_selection_json`\\s+LONGTEXT").matcher(sql).find());
    }

    @Test
    public void schemaDefinesListChangeBatchStaging() throws Exception {
        String sql = readSchema();

        Assert.assertTrue(sql.contains(
                "CREATE TABLE IF NOT EXISTS `rule_list_change_batch`"));
        Assert.assertTrue(sql.contains(
                "CREATE TABLE IF NOT EXISTS `rule_list_change_item`"));
        Assert.assertTrue(Pattern.compile(
                "`content_digest`\\s+CHAR\\(64\\)\\s+NOT NULL")
                .matcher(sql).find());
        Assert.assertTrue(sql.contains(
                "UNIQUE KEY `uk_list_change_item_row` (`batch_id`, `source_row`)"));
        Assert.assertFalse(sql.contains("`row_number`"));
        Assert.assertTrue(sql.contains(
                "KEY `idx_list_change_batch_approval` (`approval_request_id`)"));
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
