package com.hengshucredit.rule.server.sql;

import com.hengshucredit.rule.model.enums.RuleRevisionState;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DecisionArtifactSchemaSqlTest {

    @Test
    public void lifecycleAllowsOnlyGovernedTransitions() {
        Assert.assertTrue(RuleRevisionState.DRAFT.canTransitionTo(RuleRevisionState.REVIEW));
        Assert.assertTrue(RuleRevisionState.REVIEW.canTransitionTo(RuleRevisionState.DRAFT));
        Assert.assertTrue(RuleRevisionState.REVIEW.canTransitionTo(RuleRevisionState.APPROVED));
        Assert.assertTrue(RuleRevisionState.APPROVED.canTransitionTo(RuleRevisionState.PUBLISHED));
        Assert.assertTrue(RuleRevisionState.PUBLISHED.canTransitionTo(RuleRevisionState.OFFLINE));
        Assert.assertFalse(RuleRevisionState.DRAFT.canTransitionTo(RuleRevisionState.PUBLISHED));
        Assert.assertFalse(RuleRevisionState.APPROVED.canTransitionTo(RuleRevisionState.DRAFT));
        Assert.assertFalse(RuleRevisionState.OFFLINE.canTransitionTo(RuleRevisionState.PUBLISHED));
    }

    @Test
    public void schemaContainsLifecycleArtifactAuditAndDeploymentContracts() throws Exception {
        String sql = readSchema();

        Assert.assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `rule_revision`"));
        Assert.assertTrue(sql.contains(
                "UNIQUE KEY `uk_rule_revision` (`definition_id`, `revision_no`)"));
        Assert.assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `rule_lifecycle_event`"));
        Assert.assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `decision_artifact`"));
        Assert.assertTrue(sql.contains(
                "UNIQUE KEY `uk_artifact_digest` (`artifact_digest`)"));
        Assert.assertTrue(sql.contains(
                "CREATE TABLE IF NOT EXISTS `decision_artifact_component`"));
        Assert.assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `artifact_deployment`"));
        Assert.assertTrue(sql.contains(
                "CREATE TABLE IF NOT EXISTS `artifact_resource_binding`"));
        Assert.assertTrue(sql.contains(
                "CREATE TABLE IF NOT EXISTS `resource_impact_analysis`"));
        Assert.assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `rule_publish_outbox`"));
    }

    @Test
    public void existingTablesUpgradeWithRevisionArtifactAndModelDigestColumns() throws Exception {
        String sql = readSchema();

        Assert.assertTrue(sql.contains("ADD COLUMN `revision_id` BIGINT"));
        Assert.assertTrue(sql.contains("ADD COLUMN `artifact_digest` CHAR(64)"));
        Assert.assertTrue(sql.contains("ADD COLUMN `model_digest` CHAR(64)"));
        Assert.assertTrue(sql.contains("ADD COLUMN `input_schema_json` LONGTEXT"));
        Assert.assertTrue(sql.contains("ADD COLUMN `output_schema_json` LONGTEXT"));
        Assert.assertTrue(sql.contains("ADD COLUMN `validation_report_json` LONGTEXT"));
        Assert.assertTrue(sql.contains("ADD COLUMN `runtime_constraints_json` LONGTEXT"));
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
