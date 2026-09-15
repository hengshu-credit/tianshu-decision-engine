package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ProjectWorkbenchDTO;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleProject;
import org.junit.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class ProjectWorkbenchServiceTest {

    @Test
    public void fieldCheckUsesActualReferenceIdsIncludingGlobalFields() {
        RuleReferenceIntegrityService integrity = new RuleReferenceIntegrityService();
        org.springframework.test.util.ReflectionTestUtils.setField(integrity, "variableService", new RuleVariableService() {
            public Map<String, String> buildRefScriptNameMap(Long projectId) {
                return Map.of("VARIABLE:7", "globalAge", "VARIABLE:8", "localScore");
            }
        });
        ProjectWorkbenchService service = new ProjectWorkbenchService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "referenceIntegrityService", new RuleReferenceIntegrityService() {
            public java.util.List<AuditReport> scanDefinitions(java.util.List<com.hengshucredit.rule.model.entity.RuleDefinition> definitions) {
                return definitions.stream().map(rule -> integrity.audit(rule.getId(), 9L,
                        "{\"operand\":{\"kind\":\"PATH\",\"refType\":\"VARIABLE\",\"refId\":" + rule.getId() + ",\"code\":\"displayOnly\"}}")).toList();
            }
        });
        var rule = new com.hengshucredit.rule.model.entity.RuleDefinition();
        rule.setId(7L);
        rule.setRuleName("使用全局字段");
        assertEquals("READY", service.checkRuleFieldReferences(java.util.List.of(rule)).getStatus());
        rule.setId(9L);
        assertEquals("BLOCKED", service.checkRuleFieldReferences(java.util.List.of(rule)).getStatus());
        assertEquals("OPTIONAL", service.checkRuleFieldReferences(java.util.List.of()).getStatus());
        assertEquals("UNAVAILABLE", service.checkRuleFieldReferences(null).getStatus());
    }

    @Test
    public void buildChecksExplainsIncompleteProjectWithoutBlockingOptionalResources() {
        RuleProject project = project(0);
        ProjectWorkbenchDTO.Metrics metrics = metrics();
        metrics.setRuleCount(2L);
        metrics.setDraftRuleCount(2L);
        metrics.setPendingApprovalCount(1L);

        Map<String, ProjectWorkbenchDTO.CheckItem> checks = checks(
                new ProjectWorkbenchService().buildChecks(project, metrics, null));

        assertEquals("BLOCKED", checks.get("PROJECT").getStatus());
        assertEquals("UNAVAILABLE", checks.get("FIELD").getStatus());
        assertEquals("OPTIONAL", checks.get("SOURCE").getStatus());
        assertEquals("OPTIONAL", checks.get("MODEL").getStatus());
        assertEquals("READY", checks.get("RULE").getStatus());
        assertEquals("ACTION_REQUIRED", checks.get("TEST").getStatus());
        assertEquals("ATTENTION", checks.get("APPROVAL").getStatus());
        assertEquals("ACTION_REQUIRED", checks.get("PUBLISH").getStatus());
        assertEquals("BLOCKED", checks.get("RUN").getStatus());
    }

    @Test
    public void buildChecksMarksCompleteProjectReadyAndUsesRealRunSummary() {
        RuleProject project = project(1);
        ProjectWorkbenchDTO.Metrics metrics = metrics();
        metrics.setFieldCount(5L);
        metrics.setDataSourceCount(2L);
        metrics.setEnabledDataSourceCount(2L);
        metrics.setModelCount(1L);
        metrics.setRuleCount(2L);
        metrics.setDraftRuleCount(1L);
        metrics.setPublishedRuleCount(1L);
        metrics.setTestScenarioCount(2L);
        metrics.setRecentExecutionCount(12L);
        metrics.setRecentSuccessCount(12L);
        RuleExecutionLog latest = new RuleExecutionLog();
        latest.setRuleCode("RISK_MAIN");
        latest.setSuccess(1);

        Map<String, ProjectWorkbenchDTO.CheckItem> checks = checks(
                new ProjectWorkbenchService().buildChecks(project, metrics, latest));

        assertEquals("READY", checks.get("PROJECT").getStatus());
        assertEquals("UNAVAILABLE", checks.get("FIELD").getStatus());
        assertEquals("READY", checks.get("SOURCE").getStatus());
        assertEquals("READY", checks.get("MODEL").getStatus());
        assertEquals("READY", checks.get("PUBLISH").getStatus());
        assertEquals("READY", checks.get("RUN").getStatus());
        assertEquals("查看最近执行", checks.get("RUN").getActionLabel());
    }

    @Test
    public void buildChecksTreatsOfflineRulesAsExistingProjectRules() {
        RuleProject project = project(1);
        ProjectWorkbenchDTO.Metrics metrics = metrics();
        metrics.setRuleCount(1L);

        Map<String, ProjectWorkbenchDTO.CheckItem> checks = checks(
                new ProjectWorkbenchService().buildChecks(project, metrics, null));

        assertEquals("READY", checks.get("RULE").getStatus());
        assertEquals("ACTION_REQUIRED", checks.get("TEST").getStatus());
        assertEquals("ACTION_REQUIRED", checks.get("PUBLISH").getStatus());
    }

    @Test
    public void sourceCheckRoutesToTheDatasourceTypeThatNeedsAttention() {
        RuleProject project = project(1);
        ProjectWorkbenchDTO.Metrics metrics = metrics();
        metrics.setDataSourceCount(2L);
        metrics.setEnabledDataSourceCount(1L);
        metrics.setExternalDataSourceCount(1L);
        metrics.setEnabledExternalDataSourceCount(1L);
        metrics.setDatabaseDataSourceCount(1L);
        metrics.setEnabledDatabaseDataSourceCount(0L);

        Map<String, ProjectWorkbenchDTO.CheckItem> checks = checks(
                new ProjectWorkbenchService().buildChecks(project, metrics, null));

        assertEquals("ATTENTION", checks.get("SOURCE").getStatus());
        assertEquals("CONFIGURE_DATABASE_SOURCES",
                checks.get("SOURCE").getActionCode());
    }

    @Test
    public void runCheckRequiresAttentionWhenRecentExecutionsFailed() {
        ProjectWorkbenchDTO.Metrics metrics = metrics();
        metrics.setPublishedRuleCount(1L);
        metrics.setRecentExecutionCount(12L);
        metrics.setRecentSuccessCount(10L);
        RuleExecutionLog latest = new RuleExecutionLog();
        latest.setSuccess(1);
        ProjectWorkbenchDTO.CheckItem run = checks(new ProjectWorkbenchService()
                .buildChecks(project(1), metrics, latest)).get("RUN");
        assertEquals("ATTENTION", run.getStatus());
        assertEquals("VIEW_LOGS", run.getActionCode());
        org.junit.Assert.assertTrue(run.getReason().contains("2 次失败"));
    }

    @Test
    public void runCheckDoesNotTreatLatestFailureAsReadyEvenIfCountsLag() {
        ProjectWorkbenchDTO.Metrics metrics = metrics();
        metrics.setPublishedRuleCount(1L);
        metrics.setRecentExecutionCount(1L);
        metrics.setRecentSuccessCount(1L);
        RuleExecutionLog latest = new RuleExecutionLog();
        latest.setSuccess(0);
        ProjectWorkbenchDTO.CheckItem run = checks(new ProjectWorkbenchService()
                .buildChecks(project(1), metrics, latest)).get("RUN");
        assertEquals("ATTENTION", run.getStatus());
        org.junit.Assert.assertTrue(run.getReason().contains("最近一次执行失败"));
    }

    @Test
    public void runCheckDistinguishesMissingEvidenceFromUnpublishedRules() {
        ProjectWorkbenchDTO.Metrics metrics = metrics();
        metrics.setPublishedRuleCount(null);
        assertEquals("UNAVAILABLE", checks(new ProjectWorkbenchService()
                .buildChecks(project(1), metrics, null)).get("RUN").getStatus());
        metrics.setPublishedRuleCount(1L);
        metrics.setRecentExecutionCount(1L);
        assertEquals("UNAVAILABLE", checks(new ProjectWorkbenchService()
                .buildChecks(project(1), metrics, null)).get("RUN").getStatus());
        RuleExecutionLog latest = new RuleExecutionLog();
        latest.setSuccess(1);
        metrics.setRecentSuccessCount(null);
        assertEquals("UNAVAILABLE", checks(new ProjectWorkbenchService()
                .buildChecks(project(1), metrics, latest)).get("RUN").getStatus());
        metrics.setPublishedRuleCount(0L);
        assertEquals("BLOCKED", checks(new ProjectWorkbenchService()
                .buildChecks(project(1), metrics, null)).get("RUN").getStatus());
    }

    private RuleProject project(int status) {
        RuleProject project = new RuleProject();
        project.setId(9L);
        project.setProjectCode("RISK");
        project.setProjectName("风控项目");
        project.setStatus(status);
        return project;
    }

    private ProjectWorkbenchDTO.Metrics metrics() {
        ProjectWorkbenchDTO.Metrics metrics = new ProjectWorkbenchDTO.Metrics();
        metrics.setFieldCount(0L);
        metrics.setDataSourceCount(0L);
        metrics.setEnabledDataSourceCount(0L);
        metrics.setExternalDataSourceCount(0L);
        metrics.setEnabledExternalDataSourceCount(0L);
        metrics.setDatabaseDataSourceCount(0L);
        metrics.setEnabledDatabaseDataSourceCount(0L);
        metrics.setModelCount(0L);
        metrics.setRuleCount(0L);
        metrics.setDraftRuleCount(0L);
        metrics.setPublishedRuleCount(0L);
        metrics.setTestScenarioCount(0L);
        metrics.setPendingApprovalCount(0L);
        metrics.setRecentExecutionCount(0L);
        metrics.setRecentSuccessCount(0L);
        return metrics;
    }

    private Map<String, ProjectWorkbenchDTO.CheckItem> checks(
            java.util.List<ProjectWorkbenchDTO.CheckItem> rows) {
        return rows.stream().collect(Collectors.toMap(
                ProjectWorkbenchDTO.CheckItem::getCode, value -> value));
    }
}
