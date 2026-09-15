package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.ProjectWorkbenchDTO;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.RuleApiDocScenario;
import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.GovernanceApprovalRequestMapper;
import com.hengshucredit.rule.server.mapper.RuleApiDocScenarioMapper;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleExecutionLogMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Service
public class ProjectWorkbenchService {

    @Resource
    private RuleProjectMapper projectMapper;
    @Resource
    private RuleVariableMapper variableMapper;
    @Resource
    private RuleExternalDatasourceMapper externalDatasourceMapper;
    @Resource
    private RuleDbDatasourceMapper dbDatasourceMapper;
    @Resource
    private RuleModelMapper modelMapper;
    @Resource
    private RuleDefinitionMapper definitionMapper;
    @Resource
    private RuleApiDocScenarioMapper scenarioMapper;
    @Resource
    private GovernanceApprovalRequestMapper approvalRequestMapper;
    @Resource
    private RuleExecutionLogMapper executionLogMapper;
    @Resource
    private RuleReferenceIntegrityService referenceIntegrityService;

    public ProjectWorkbenchDTO getWorkbench(Long projectId) {
        RuleProject project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null || Integer.valueOf(-1).equals(project.getStatus())) {
            throw new IllegalArgumentException("项目不存在");
        }

        ProjectWorkbenchDTO result = new ProjectWorkbenchDTO();
        result.setProject(toSummary(project));
        ProjectWorkbenchDTO.Metrics metrics = new ProjectWorkbenchDTO.Metrics();
        result.setMetrics(metrics);

        metrics.setFieldCount(safeCount(result, "业务字段",
                () -> variableMapper.selectCount(
                        new LambdaQueryWrapper<RuleVariable>()
                                .eq(RuleVariable::getScope, "PROJECT")
                                .eq(RuleVariable::getProjectId, projectId)
                                .ne(RuleVariable::getStatus, -1))));

        Long externalCount = safeCount(result, "外数数据源",
                () -> externalDatasourceMapper.selectCount(
                        new LambdaQueryWrapper<RuleExternalDatasource>()
                                .eq(RuleExternalDatasource::getScope, "PROJECT")
                                .eq(RuleExternalDatasource::getProjectId, projectId)
                                .ne(RuleExternalDatasource::getStatus, -1)));
        Long databaseCount = safeCount(result, "数据库数据源",
                () -> dbDatasourceMapper.selectCount(
                        new LambdaQueryWrapper<RuleDbDatasource>()
                                .eq(RuleDbDatasource::getScope, "PROJECT")
                                .eq(RuleDbDatasource::getProjectId, projectId)
                                .ne(RuleDbDatasource::getStatus, -1)));
        metrics.setExternalDataSourceCount(externalCount);
        metrics.setDatabaseDataSourceCount(databaseCount);
        metrics.setDataSourceCount(sum(externalCount, databaseCount));

        Long enabledExternalCount = safeCount(result, "启用的外数数据源",
                () -> externalDatasourceMapper.selectCount(
                        new LambdaQueryWrapper<RuleExternalDatasource>()
                                .eq(RuleExternalDatasource::getScope, "PROJECT")
                                .eq(RuleExternalDatasource::getProjectId, projectId)
                                .eq(RuleExternalDatasource::getStatus, 1)));
        Long enabledDatabaseCount = safeCount(result, "启用的数据库数据源",
                () -> dbDatasourceMapper.selectCount(
                        new LambdaQueryWrapper<RuleDbDatasource>()
                                .eq(RuleDbDatasource::getScope, "PROJECT")
                                .eq(RuleDbDatasource::getProjectId, projectId)
                                .eq(RuleDbDatasource::getStatus, 1)));
        metrics.setEnabledExternalDataSourceCount(enabledExternalCount);
        metrics.setEnabledDatabaseDataSourceCount(enabledDatabaseCount);
        metrics.setEnabledDataSourceCount(sum(
                enabledExternalCount, enabledDatabaseCount));

        metrics.setModelCount(safeCount(result, "模型",
                () -> modelMapper.selectCount(
                        new LambdaQueryWrapper<RuleModel>()
                                .eq(RuleModel::getScope, "PROJECT")
                                .eq(RuleModel::getProjectId, projectId)
                                .ne(RuleModel::getStatus, -1))));

        List<RuleDefinition> rules = safeLoad(result, "项目规则",
                () -> definitionMapper.selectList(projectRuleQuery(projectId)));
        if (rules != null) {
            metrics.setRuleCount((long) rules.size());
            metrics.setDraftRuleCount(rules.stream()
                    .filter(value -> Integer.valueOf(0).equals(value.getStatus()))
                    .count());
            metrics.setPublishedRuleCount(rules.stream()
                    .filter(value -> Integer.valueOf(1).equals(value.getStatus()))
                    .count());
            metrics.setTestScenarioCount(countScenarios(result, rules));
        }

        metrics.setPendingApprovalCount(safeCount(result, "待审批事项",
                () -> approvalRequestMapper.selectCount(
                        new LambdaQueryWrapper<GovernanceApprovalRequest>()
                                .eq(GovernanceApprovalRequest::getProjectId, projectId)
                                .eq(GovernanceApprovalRequest::getStatus, "PENDING"))));

        LocalDateTime recentBegin = LocalDateTime.now().minusHours(24);
        metrics.setRecentExecutionCount(safeCount(result, "最近执行次数",
                () -> executionLogMapper.selectCount(
                        new LambdaQueryWrapper<RuleExecutionLog>()
                                .eq(RuleExecutionLog::getProjectCode,
                                        project.getProjectCode())
                                .ge(RuleExecutionLog::getCreateTime, recentBegin))));
        metrics.setRecentSuccessCount(safeCount(result, "最近成功次数",
                () -> executionLogMapper.selectCount(
                        new LambdaQueryWrapper<RuleExecutionLog>()
                                .eq(RuleExecutionLog::getProjectCode,
                                        project.getProjectCode())
                                .eq(RuleExecutionLog::getSuccess, 1)
                                .ge(RuleExecutionLog::getCreateTime, recentBegin))));
        metrics.setRecentSuccessRate(successRate(
                metrics.getRecentExecutionCount(),
                metrics.getRecentSuccessCount()));

        RuleExecutionLog latest = safeLoad(result, "最近一次执行",
                () -> {
                    List<RuleExecutionLog> rows = executionLogMapper.selectList(
                            new LambdaQueryWrapper<RuleExecutionLog>()
                                    .eq(RuleExecutionLog::getProjectCode,
                                            project.getProjectCode())
                                    .orderByDesc(RuleExecutionLog::getCreateTime)
                                    .orderByDesc(RuleExecutionLog::getId)
                                    .last("LIMIT 1"));
                    return rows == null || rows.isEmpty() ? null : rows.get(0);
                });
        result.setRecentExecution(toRecentExecution(latest));
        result.setChecks(buildChecks(project, metrics, latest));
        ProjectWorkbenchDTO.CheckItem fieldCheck = checkRuleFieldReferences(rules);
        result.getChecks().replaceAll(item -> "FIELD".equals(item.getCode()) ? fieldCheck : item);
        return result;
    }

    ProjectWorkbenchDTO.CheckItem checkRuleFieldReferences(List<RuleDefinition> rules) {
        if (rules == null) return unavailable("FIELD", "检查规则字段引用");
        if (rules.isEmpty()) {
            return check("FIELD", "检查规则字段引用", "OPTIONAL",
                    "尚未创建规则；可按需使用项目字段、全局字段或无需字段的规则。",
                    "CONFIGURE_RULES", "创建规则");
        }
        try {
            for (RuleReferenceIntegrityService.AuditReport audit : referenceIntegrityService.scanDefinitions(rules)) {
                if (!audit.isValid()) {
                    var issue = audit.getIssues().get(0);
                    String name = rules.stream().filter(rule -> rule.getId().equals(audit.getDefinitionId()))
                            .map(RuleDefinition::getRuleName).findFirst().orElse("未命名规则");
                    return check("FIELD", "检查规则字段引用", "BLOCKED",
                            "规则「" + name + "」：" + issue.getMessage()
                                    + "（" + issue.getPath() + "）。请修复规则中的引用。",
                            "CONFIGURE_RULES", "检查规则引用");
                }
            }
        } catch (RuntimeException error) {
            log.warn("Project rule reference check failed", error);
            return unavailable("FIELD", "检查规则字段引用");
        }
        return check("FIELD", "检查规则字段引用", "READY",
                "已检查 " + rules.size() + " 条规则的稳定字段引用；可使用已启用的项目或全局字段。",
                "CONFIGURE_RULES", "查看规则");
    }

    List<ProjectWorkbenchDTO.CheckItem> buildChecks(
            RuleProject project, ProjectWorkbenchDTO.Metrics metrics,
            RuleExecutionLog latest) {
        List<ProjectWorkbenchDTO.CheckItem> checks = new ArrayList<>();
        checks.add(Integer.valueOf(1).equals(project.getStatus())
                ? check("PROJECT", "启用项目", "READY",
                "项目已启用，可以继续配置和验证。", null, null)
                : check("PROJECT", "启用项目", "BLOCKED",
                "项目当前处于停用状态，线上调用不会开放。",
                "MANAGE_PROJECT", "调整项目状态"));

        // 资源数量仅用于概览；实际引用检查完成前不得推断规则字段已就绪。
        checks.add(unavailable("FIELD", "检查规则字段引用"));

        checks.add(sourceCheck(metrics));

        Long modelCount = metrics.getModelCount();
        checks.add(modelCount == null
                ? unavailable("MODEL", "接入预测模型")
                : modelCount > 0
                ? check("MODEL", "接入预测模型", "READY",
                "已配置 " + modelCount + " 个项目模型。",
                "CONFIGURE_MODELS", "查看模型")
                : check("MODEL", "接入预测模型", "OPTIONAL",
                "当前没有项目模型；纯规则项目可以跳过。",
                "CONFIGURE_MODELS", "按需接入"));

        Long totalRules = metrics.getRuleCount();
        Long publishedRules = metrics.getPublishedRuleCount();
        checks.add(totalRules == null
                ? unavailable("RULE", "设计决策规则")
                : totalRules > 0
                ? check("RULE", "设计决策规则", "READY",
                "项目已有 " + totalRules + " 条可管理规则。",
                "CONFIGURE_RULES", "查看规则")
                : check("RULE", "设计决策规则", "ACTION_REQUIRED",
                "尚未创建或关联任何规则。",
                "CONFIGURE_RULES", "创建规则"));

        Long scenarios = metrics.getTestScenarioCount();
        checks.add(scenarios == null
                ? unavailable("TEST", "准备测试场景")
                : totalRules != null && totalRules == 0
                ? check("TEST", "准备测试场景", "BLOCKED",
                "需要先完成规则设计，才能保存可复用测试场景。",
                "CONFIGURE_RULES", "先创建规则")
                : scenarios > 0
                ? check("TEST", "准备测试场景", "READY",
                "已保存 " + scenarios + " 个测试场景。",
                "TEST_RULES", "执行测试")
                : check("TEST", "准备测试场景", "ACTION_REQUIRED",
                "尚未保存测试场景，发布前建议覆盖主要业务分支。",
                "TEST_RULES", "补充测试"));

        Long pending = metrics.getPendingApprovalCount();
        checks.add(pending == null
                ? unavailable("APPROVAL", "处理审批事项")
                : pending > 0
                ? check("APPROVAL", "处理审批事项", "ATTENTION",
                "当前有 " + pending + " 条申请等待审批。",
                "REVIEW_APPROVALS", "处理审批")
                : check("APPROVAL", "处理审批事项", "READY",
                "当前没有等待处理的审批。",
                "REVIEW_APPROVALS", "查看记录"));

        checks.add(publishedRules == null
                ? unavailable("PUBLISH", "发布规则")
                : publishedRules > 0
                ? check("PUBLISH", "发布规则", "READY",
                "已有 " + publishedRules + " 条规则处于发布状态。",
                "CONFIGURE_RULES", "查看生命周期")
                : totalRules != null && totalRules == 0
                ? check("PUBLISH", "发布规则", "BLOCKED",
                "需要先创建规则并完成测试。",
                "CONFIGURE_RULES", "先创建规则")
                : check("PUBLISH", "发布规则", "ACTION_REQUIRED",
                "规则尚未发布，线上调用不会获得生效版本。",
                "CONFIGURE_RULES", "进入生命周期"));

        checks.add(runCheck(metrics, latest));
        return checks;
    }

    private ProjectWorkbenchDTO.CheckItem runCheck(
            ProjectWorkbenchDTO.Metrics metrics, RuleExecutionLog latest) {
        String title = "检查执行结果";
        Long published = metrics.getPublishedRuleCount();
        if (published == null) return unavailable("RUN", title);
        if (published == 0) {
            return check("RUN", title, "BLOCKED",
                    "发布规则后才能核对生效版本的执行情况。",
                    "CONFIGURE_RULES", "先发布规则");
        }
        Long recent = metrics.getRecentExecutionCount();
        if (recent == null) return unavailable("RUN", title);
        if (recent == 0) {
            return check("RUN", title, "ACTION_REQUIRED",
                    "规则已发布，但最近 24 小时没有执行记录。",
                    "TEST_RULES", "执行一次验证");
        }
        if (latest != null && Integer.valueOf(0).equals(latest.getSuccess())) {
            return check("RUN", title, "ATTENTION",
                    "最近一次执行失败，请查看日志定位原因。",
                    "VIEW_LOGS", "排查执行失败");
        }
        Long success = metrics.getRecentSuccessCount();
        if (success == null || latest == null || latest.getSuccess() == null) {
            return unavailable("RUN", title);
        }
        if (success < recent) {
            return check("RUN", title, "ATTENTION",
                    "最近 24 小时共有 " + recent + " 次执行，其中 "
                            + (recent - success) + " 次失败，请查看日志。",
                    "VIEW_LOGS", "排查执行失败");
        }
        return check("RUN", title, "READY",
                "最近 24 小时已有 " + recent + " 次执行，当前未发现失败记录；调用来源与修订请查看日志。",
                "VIEW_LOGS", "查看最近执行");
    }

    private ProjectWorkbenchDTO.CheckItem sourceCheck(
            ProjectWorkbenchDTO.Metrics metrics) {
        Long total = metrics.getDataSourceCount();
        Long enabled = metrics.getEnabledDataSourceCount();
        if (total == null || enabled == null) {
            return unavailable("SOURCE", "接入业务数据");
        }
        if (total == 0) {
            return check("SOURCE", "接入业务数据", "OPTIONAL",
                    "当前没有外数或数据库数据源；仅使用请求输入时可以跳过。",
                    "CONFIGURE_EXTERNAL_SOURCES", "按需接入");
        }
        if (enabled.equals(total)) {
            return check("SOURCE", "接入业务数据", "READY",
                    "已启用 " + enabled + " 个数据源。",
                    sourceAction(metrics), "查看数据源");
        }
        return check("SOURCE", "接入业务数据", "ATTENTION",
                "已配置 " + total + " 个数据源，其中 " + enabled + " 个启用。",
                sourceAction(metrics), "检查数据源");
    }

    private String sourceAction(ProjectWorkbenchDTO.Metrics metrics) {
        Long external = metrics.getExternalDataSourceCount();
        Long enabledExternal = metrics.getEnabledExternalDataSourceCount();
        if (external != null && enabledExternal != null
                && enabledExternal < external) {
            return "CONFIGURE_EXTERNAL_SOURCES";
        }
        Long database = metrics.getDatabaseDataSourceCount();
        Long enabledDatabase = metrics.getEnabledDatabaseDataSourceCount();
        if (database != null && enabledDatabase != null
                && enabledDatabase < database) {
            return "CONFIGURE_DATABASE_SOURCES";
        }
        if (external != null && external > 0) {
            return "CONFIGURE_EXTERNAL_SOURCES";
        }
        if (database != null && database > 0) {
            return "CONFIGURE_DATABASE_SOURCES";
        }
        return "CONFIGURE_EXTERNAL_SOURCES";
    }

    private LambdaQueryWrapper<RuleDefinition> projectRuleQuery(Long projectId) {
        return new LambdaQueryWrapper<RuleDefinition>()
                .ne(RuleDefinition::getStatus, -1)
                .and(value -> value
                        .eq(RuleDefinition::getProjectId, projectId)
                        .or()
                        .exists("SELECT 1 FROM rule_definition_ref rdr "
                                + "WHERE rdr.definition_id = rule_definition.id "
                                + "AND rdr.project_id = " + projectId));
    }

    private Long countScenarios(ProjectWorkbenchDTO result,
                                List<RuleDefinition> rules) {
        List<Long> ids = rules.stream()
                .map(RuleDefinition::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return 0L;
        }
        return safeCount(result, "测试场景",
                () -> scenarioMapper.selectCount(
                        new LambdaQueryWrapper<RuleApiDocScenario>()
                                .in(RuleApiDocScenario::getDefinitionId, ids)
                                .eq(RuleApiDocScenario::getStatus, 1)));
    }

    private ProjectWorkbenchDTO.ProjectSummary toSummary(RuleProject project) {
        ProjectWorkbenchDTO.ProjectSummary summary =
                new ProjectWorkbenchDTO.ProjectSummary();
        summary.setId(project.getId());
        summary.setProjectCode(project.getProjectCode());
        summary.setProjectName(project.getProjectName());
        summary.setDescription(project.getDescription());
        summary.setStatus(project.getStatus());
        return summary;
    }

    private ProjectWorkbenchDTO.RecentExecution toRecentExecution(
            RuleExecutionLog latest) {
        if (latest == null) return null;
        ProjectWorkbenchDTO.RecentExecution value =
                new ProjectWorkbenchDTO.RecentExecution();
        value.setTraceId(latest.getTraceId());
        value.setRuleCode(latest.getRuleCode());
        value.setRuleVersion(latest.getRuleVersion());
        value.setSuccess(latest.getSuccess());
        value.setExecuteTimeMs(latest.getExecuteTimeMs());
        value.setSource(latest.getSource());
        value.setCreateTime(latest.getCreateTime());
        return value;
    }

    private Long safeCount(ProjectWorkbenchDTO result, String label,
                           Supplier<Long> loader) {
        return safeLoad(result, label, loader);
    }

    private <T> T safeLoad(ProjectWorkbenchDTO result, String label,
                           Supplier<T> loader) {
        try {
            return loader.get();
        } catch (RuntimeException error) {
            log.warn("Project workbench metric failed: {}", label, error);
            result.getWarnings().add(label + "暂时无法读取");
            return null;
        }
    }

    private Long sum(Long left, Long right) {
        if (left == null || right == null) return null;
        return left + right;
    }

    private Double successRate(Long total, Long success) {
        if (total == null || success == null || total == 0) return null;
        return Math.round(success * 1000.0 / total) / 10.0;
    }

    private ProjectWorkbenchDTO.CheckItem unavailable(
            String code, String title) {
        return check(code, title, "UNAVAILABLE",
                "该项状态暂时无法读取，请稍后重试。",
                "REFRESH_WORKBENCH", "重新加载");
    }

    private ProjectWorkbenchDTO.CheckItem check(
            String code, String title, String status, String reason,
            String actionCode, String actionLabel) {
        return new ProjectWorkbenchDTO.CheckItem(
                code, title, status, reason, actionCode, actionLabel);
    }
}
