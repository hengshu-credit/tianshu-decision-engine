package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.dto.*;
import com.hengshucredit.rule.model.entity.RuleApiDocScenario;
import com.hengshucredit.rule.model.entity.RuleRegressionEvidence;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.RulePreflightValidationService;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.mapper.RuleApiDocScenarioMapper;
import com.hengshucredit.rule.server.mapper.RuleRegressionEvidenceMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Records server execution, never accepts client-provided pass/fail evidence. */
@Service
public class RuleRegressionEvidenceService {
    @Resource private RuleRegressionEvidenceMapper evidenceMapper;
    @Resource private RuleApiDocScenarioMapper scenarioMapper;
    @Resource private RuleRevisionMapper revisionMapper;
    @Resource private com.hengshucredit.rule.server.mapper.RuleDefinitionMapper definitionMapper;
    @Resource private com.hengshucredit.rule.server.mapper.DecisionArtifactMapper artifactMapper;
    @Resource private RuleTestExpectationService expectationService;
    @Resource private ConsoleOperatorResolver operatorResolver;
    @Resource @Lazy private RulePreflightValidationService preflight;

    public record Stamp(String contentDigest, String dependencyDigest, String fieldDigest,
                        String modelDigest, String compiledScript, boolean valid) { }
    public record Run(RuleRegressionEvidence row, Stamp stamp) { }

    @org.springframework.transaction.annotation.Transactional
    public Run begin(Long definitionId, RuleApiDocScenario scenario, RuleTestTargetResolver.Target target,
                     String input, String expectation, String expected, Long projectId) {
        if (scenario == null) return null;
        lockDefinition(definitionId);
        scenario = loadCase(scenario.getId());
        if (scenario == null || !definitionId.equals(scenario.getDefinitionId())) throw new IllegalArgumentException("固定用例已删除或不属于当前规则");
        RuleRegressionEvidence row = new RuleRegressionEvidence();
        row.setDefinitionId(definitionId); row.setScenarioId(scenario.getId()); row.setScenarioName(scenario.getScenarioName());
        row.setScenarioDigest(scenarioDigest(scenario));
        row.setProjectId(target == null ? projectId : target.projectId());
        row.setSourceType(target == null ? "WORKSPACE" : target.identity().getSourceType());
        row.setSourceId(target == null ? null : target.identity().getSourceId());
        row.setExecutionIdentityJson(target == null ? null : CanonicalJson.write(target.identity()));
        row.setRevisionId(target == null || target.identity().getRevisionId() == null ? null : Long.valueOf(target.identity().getRevisionId()));
        row.setState("RUNNING"); row.setEligible(0); row.setCreateTime(LocalDateTime.now()); row.setExecutedBy(actor());
        Stamp stamp = row.getRevisionId() == null ? null : currentStamp(definitionId, row.getRevisionId());
        if (stamp == null || !stamp.valid()) row.setMessage("未绑定可验证的已保存修订，仅保留诊断记录");
        else if (!Objects.equals(row.getScenarioDigest(), scenarioDigest(input, expectation, expected)))
            row.setMessage("使用了临时输入或预期；请保存固定用例后重新执行");
        else if (!Integer.valueOf(1).equals(scenario.getStatus())) row.setMessage("用例未启用，仅保留诊断记录");
        else if (!Objects.equals(stamp.modelDigest(), target.identity().getModelDigest())
                || (stamp.fieldDigest() != null && !Objects.equals(stamp.compiledScript(), target.compiledScript()))
                || (stamp.fieldDigest() != null && !stamp.fieldDigest().equals(RulePreflightValidationService.fieldDigest(target.fields()))))
            row.setMessage("执行目标与当前修订契约不同，请重新选择草稿和执行项目");
        else row.setEligible(1);
        if (stamp != null) { row.setContentDigest(stamp.contentDigest()); row.setDependencyDigest(stamp.dependencyDigest()); }
        insert(row);
        return new Run(row, stamp);
    }

    @org.springframework.transaction.annotation.Transactional
    public String complete(Run run, RuleResult execution, TestExpectationResult validation) {
        if (run == null) return null;
        RuleRegressionEvidence row = run.row();
        lockDefinition(row.getDefinitionId());
        row.setState("COMPLETED"); row.setSuccess(execution.isSuccess() ? 1 : 0);
        row.setValidationStatus(validation.getStatus()); row.setTraceId(execution.getTraceId());
        row.setExecutionIdentityJson(CanonicalJson.write(execution.getExecutionIdentity()));
        String details = CanonicalJson.write(validation);
        if (details.length() > 262144) details = CanonicalJson.write(Map.of("status", validation.getStatus(),
                "message", "报告详情超过上限，仅保留校验结论；请查看本次执行结果", "truncated", true));
        row.setValidationJson(details);
        if (Integer.valueOf(1).equals(row.getEligible())) {
            Stamp after = currentStamp(row.getDefinitionId(), row.getRevisionId());
            RuleApiDocScenario current = loadCase(row.getScenarioId());
            if (after == null || !after.valid() || !Objects.equals(run.stamp().contentDigest(), after.contentDigest())
                    || !Objects.equals(run.stamp().dependencyDigest(), after.dependencyDigest())
                    || current == null || !Objects.equals(row.getScenarioDigest(), scenarioDigest(current))
                    || execution.getExecutionIdentity() == null
                    || !Objects.equals(String.valueOf(row.getRevisionId()), execution.getExecutionIdentity().getRevisionId())) {
                row.setState("STALE"); row.setMessage("执行期间内容、依赖或用例发生变化，本记录不能用于发布");
            } else row.setMessage(validation.getMessage());
        }
        row.setCompletedAt(LocalDateTime.now()); completeUpdate(row);
        return String.valueOf(row.getId());
    }

    @org.springframework.transaction.annotation.Transactional
    public void fail(Run run, RuntimeException failure) {
        if (run == null) return;
        RuleRegressionEvidence row = run.row();
        lockDefinition(row.getDefinitionId());
        row.setState("ERROR"); row.setSuccess(0); row.setValidationStatus("NOT_EVALUATED");
        row.setMessage("测试未完成，请重新执行：" + failure.getClass().getSimpleName());
        row.setCompletedAt(LocalDateTime.now()); completeUpdate(row);
    }

    public RuleRegressionGate check(Long definitionId, Long revisionId, String contentDigest, String dependencyDigest) {
        List<RuleApiDocScenario> scenarios = enabledCases(definitionId);
        RuleRegressionGate gate = new RuleRegressionGate(); gate.setRequiredCount(scenarios.size());
        Map<Long, RuleRegressionEvidence> latest = new LinkedHashMap<>();
        if (!scenarios.isEmpty() && revisionId != null) latestEvidence(definitionId, revisionId).forEach(row -> latest.put(row.getScenarioId(), row));
        for (RuleApiDocScenario scenario : scenarios) {
            RuleRegressionEvidence row = latest.get(scenario.getId());
            String status;
            if (row == null) { status = "MISSING"; gate.setMissingCount(gate.getMissingCount() + 1); }
            else if ("STALE".equals(row.getState()) || !Objects.equals(row.getContentDigest(), contentDigest)
                    || !Objects.equals(row.getDependencyDigest(), dependencyDigest)
                    || !Objects.equals(row.getScenarioDigest(), scenarioDigest(scenario))) {
                status = "STALE"; gate.setStaleCount(gate.getStaleCount() + 1);
            } else if ("COMPLETED".equals(row.getState()) && Integer.valueOf(1).equals(row.getSuccess())
                    && "PASSED".equals(row.getValidationStatus())) {
                status = "PASSED"; gate.setPassedCount(gate.getPassedCount() + 1);
            } else {
                status = "RUNNING".equals(row.getState()) ? "RUNNING" : row.getValidationStatus();
                gate.setFailedCount(gate.getFailedCount() + 1);
            }
            gate.getCases().add(new RuleRegressionGate.CaseStatus(String.valueOf(scenario.getId()), scenario.getScenarioName(),
                    row == null ? null : String.valueOf(row.getId()), status));
        }
        gate.setAllowed(gate.getPassedCount() == gate.getRequiredCount());
        gate.setMessage(scenarios.isEmpty() ? "暂无启用的固定用例，保留原发布流程；建议补充边界、无命中及失败样例"
                : gate.isAllowed() ? "全部 " + scenarios.size() + " 个固定用例已通过，内容与依赖证据有效"
                : "固定用例回归未全部通过：未测 " + gate.getMissingCount() + "，已过期 " + gate.getStaleCount()
                    + "，未通过或未完成 " + gate.getFailedCount() + "；请在规则测试页选择此草稿并执行固定用例");
        return gate;
    }

    public void appendGate(Long definitionId, RulePreflightReport report) {
        RuleRegressionGate gate = report.getRegression() == null
                ? check(definitionId, report.getRevisionId(), report.getContentDigest(), report.getDependencyDigest()) : report.getRegression();
        report.setRegression(gate);
        report.getWarnings().removeIf(issue -> List.of("NO_REGRESSION_CASES", "REGRESSION_PENDING").contains(issue.getCode()));
        if (!gate.isAllowed()) report.getErrors().add(RuleValidationIssue.error("REGRESSION_REQUIRED", "$", gate.getMessage()));
        else if (gate.getRequiredCount() == 0) report.getWarnings().add(RuleValidationIssue.warning("NO_REGRESSION_CASES", "$", gate.getMessage()));
        report.setValid(report.getErrors().isEmpty());
    }

    public void appendSummary(Long definitionId, RulePreflightReport report) {
        RuleRegressionGate gate = check(definitionId, report.getRevisionId(), report.getContentDigest(), report.getDependencyDigest());
        report.setRegression(gate);
    }

    public void requirePublication(RuleRevision revision) {
        Stamp stamp = currentStamp(revision.getDefinitionId(), revision.getId());
        if (stamp == null || !stamp.valid()) throw new IllegalArgumentException("当前修订未通过配置检查，请先修复编译、字段和依赖问题");
        RuleRegressionGate gate = check(revision.getDefinitionId(), revision.getId(), stamp.contentDigest(), stamp.dependencyDigest());
        if (!gate.isAllowed()) throw new IllegalArgumentException(gate.getMessage());
    }

    public IPage<RuleRegressionEvidence> history(Long definitionId, int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("报告分页参数无效");
        return evidenceMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<RuleRegressionEvidence>()
                .select(RuleRegressionEvidence.class, field -> !"validationJson".equals(field.getProperty()))
                .eq(RuleRegressionEvidence::getDefinitionId, definitionId).orderByDesc(RuleRegressionEvidence::getId));
    }

    public RuleRegressionEvidence detail(Long definitionId, Long reportId) {
        RuleRegressionEvidence row = evidenceMapper.selectOne(new LambdaQueryWrapper<RuleRegressionEvidence>()
                .eq(RuleRegressionEvidence::getDefinitionId, definitionId).eq(RuleRegressionEvidence::getId, reportId));
        if (row == null) throw new IllegalArgumentException("报告不存在或不可访问");
        return row;
    }

    public RuleRegressionGate checkRevision(Long definitionId, Long revisionId) {
        Stamp stamp = currentStamp(definitionId, revisionId);
        if (stamp == null || !stamp.valid()) throw new IllegalArgumentException("当前修订未通过配置检查，请先修复编译、字段和依赖问题");
        return check(definitionId, revisionId, stamp.contentDigest(), stamp.dependencyDigest());
    }

    protected Stamp currentStamp(Long definitionId, Long revisionId) {
        RuleRevision revision = revisionMapper.selectById(revisionId);
        if (revision == null || !definitionId.equals(revision.getDefinitionId()) || "DELETED".equals(revision.getState())) return null;
        if (List.of("APPROVED", "PUBLISHED", "OFFLINE").contains(revision.getState()) && revision.getContentDigest() != null) {
            var artifact = artifactMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.hengshucredit.rule.model.entity.DecisionArtifact>()
                    .select("id", "definition_id", "revision_id", "validation_report_json", "manifest_json").eq("id", revision.getArtifactId()));
            if (artifact == null || !definitionId.equals(artifact.getDefinitionId()) || !revisionId.equals(artifact.getRevisionId())) return null;
            Map<String, Object> manifest = CanonicalJson.readMap(artifact.getManifestJson());
            if (!(manifest.get("metadata") instanceof Map<?, ?> metadata)
                    || !Objects.equals(revision.getContentDigest(), metadata.get("contentDigest"))) return null;
            return new Stamp(revision.getContentDigest(), (String) metadata.get("dependencyDigest"), null,
                    Sha256Digests.text(revision.getModelJson()), revision.getCompiledScript(),
                    Boolean.TRUE.equals(CanonicalJson.readMap(artifact.getValidationReportJson()).get("valid")));
        }
        RulePreflightReport report = preflight.validatePreview(revision, false);
        return new Stamp(report.getContentDigest(), report.getDependencyDigest(), report.getFieldDigest(),
                Sha256Digests.text(revision.getModelJson()), report.getCompiledScript(), report.isValid());
    }

    public String scenarioDigest(RuleApiDocScenario scenario) {
        return scenarioDigest(scenario.getRequestJson(), scenario.getExpectationJson(), scenario.getResponseJson());
    }

    public String scenarioDigest(String input, String expectation, String expected) {
        return Sha256Digests.text(CanonicalJson.write(Map.of(
                "input", expectationService.readJsonObject(input, "请求报文"),
                "expectation", expectationService.readJsonObject(expectation == null || expectation.isBlank()
                        ? "{\"version\":1,\"mode\":\"SNAPSHOT\"}" : expectation, "预期配置"),
                "expected", expectationService.readJsonObject(expected, "预期结果"))));
    }

    protected List<RuleApiDocScenario> enabledCases(Long definitionId) {
        return scenarioMapper.selectList(new LambdaQueryWrapper<RuleApiDocScenario>().eq(RuleApiDocScenario::getDefinitionId, definitionId)
                .eq(RuleApiDocScenario::getStatus, 1).orderByAsc(RuleApiDocScenario::getSortOrder).orderByAsc(RuleApiDocScenario::getId).last("FOR UPDATE"));
    }
    protected RuleApiDocScenario loadCase(Long id) { return scenarioMapper.selectById(id); }
    protected List<RuleRegressionEvidence> latestEvidence(Long definitionId, Long revisionId) { return evidenceMapper.latestByCase(definitionId, revisionId); }
    protected String actor() { return operatorResolver.resolve(); }
    protected void lockDefinition(Long id) {
        var definition = definitionMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.hengshucredit.rule.model.entity.RuleDefinition>()
                .select("id").eq("id", id).ne("status", -1).last("FOR UPDATE"));
        if (definition == null) throw new IllegalArgumentException("规则不存在或不可访问");
    }
    protected void insert(RuleRegressionEvidence row) {
        evidenceMapper.insert(row);
        if (Integer.valueOf(1).equals(row.getEligible())) evidenceMapper.pinLatest(row.getDefinitionId(), row.getRevisionId(), row.getScenarioId(), row.getId());
    }
    protected void completeUpdate(RuleRegressionEvidence row) {
        int updated = evidenceMapper.update(row, new LambdaUpdateWrapper<RuleRegressionEvidence>()
                .eq(RuleRegressionEvidence::getId, row.getId()).eq(RuleRegressionEvidence::getState, "RUNNING"));
        if (updated != 1) throw new IllegalStateException("回归报告状态已变化，请重新加载报告");
    }
}
