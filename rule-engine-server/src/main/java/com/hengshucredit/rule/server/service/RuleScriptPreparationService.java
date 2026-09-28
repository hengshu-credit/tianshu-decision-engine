package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionVersion;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.entity.RuleVersionBinding;
import com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService;
import com.hengshucredit.rule.server.health.RuleWarmupStatus;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionVersionMapper;
import com.hengshucredit.rule.server.mapper.RuleVersionBindingMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Prepares rules before activation and warms active/latest and fixed versions on startup. */
@Service
public class RuleScriptPreparationService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RuleScriptPreparationService.class);
    @Resource private QLExpressEngine engine;
    @Resource private FunctionRegistrar functionRegistrar;
    @Resource @Lazy private RuleRuntimeInvoker runtimeInvoker;
    @Resource @Lazy private RuleDefinitionService definitionService;
    @Resource private RuleVariableService variableService;
    @Resource private RuleFunctionService functionService;
    @Resource private ArtifactRuntimeSnapshotService snapshots;
    @Resource private RulePublishedMapper publishedMapper;
    @Resource private RuleVersionBindingMapper bindingMapper;
    @Resource private RuleDefinitionVersionMapper versionMapper;
    @Resource private RuleVersionBindingService versionService;
    @Resource private RuleWarmupStatus warmupStatus = new RuleWarmupStatus();
    private String lastWarmupFailureMessage;

    public QLExpressEngine.PreparedScript prepareArtifact(Long artifactId, Long definitionId, Long projectId) {
        ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot = snapshots.load(artifactId, definitionId, projectId);
        runtimeInvoker.register(engine.getRunner());
        var bindings = functionRegistrar.prepareFunctions(snapshot.getFunctions(), engine.getRunner());
        var prepared = engine.prepare(snapshot.getCompiledScript(), constantNames(snapshot.getVariables()));
        functionRegistrar.validateFunctionBindings(prepared.functionNames(), bindings, engine.getRunner());
        return prepared;
    }

    public QLExpressEngine.PreparedScript prepareProject(String script, Long projectId) {
        runtimeInvoker.register(engine.getRunner());
        var bindings = functionRegistrar.prepareFunctions(functionService.listByProject(projectId), engine.getRunner());
        List<RuleVariable> variables = projectId != null && projectId > 0
                ? variableService.listByProject(projectId, null) : variableService.listGlobalOnly();
        var prepared = engine.prepare(script, constantNames(variables));
        functionRegistrar.validateFunctionBindings(prepared.functionNames(), bindings, engine.getRunner());
        return prepared;
    }

    static Set<String> constantNames(List<RuleVariable> variables) {
        Set<String> names = new LinkedHashSet<>();
        for (RuleVariable variable : variables == null ? Collections.<RuleVariable>emptyList() : variables) {
            if (!"CONSTANT".equals(variable.getVarSource())) continue;
            String name = variable.getScriptName();
            if (name == null || name.isBlank()) name = variable.getVarCode();
            if (name != null && !name.isBlank()) names.add(name.trim().split("\\.", 2)[0]);
        }
        return names;
    }

    private void preparePublished(RulePublished published) {
        RuleDefinition definition = definitionService.getById(published.getDefinitionId());
        if (definition == null) throw new IllegalStateException("预热规则缺少定义: " + published.getDefinitionId());
        if (published.getArtifactId() == null) {
            prepareProject(published.getCompiledScript(), definition.getProjectId());
        } else {
            prepareArtifact(published.getArtifactId(), published.getDefinitionId(), definition.getProjectId());
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        warmupPublishedRules();
    }

    /**
     * 只预热已上线的发布记录及仍处于生产绑定状态的固定版本；草稿、审核中版本和下线版本不进入目标集。
     * 启动流程和管理端修复后重试共用，结果可直接用于 readiness/运维页面。
     */
    public synchronized java.util.Map<String, Object> warmupPublishedRules() {
        List<RulePublished> publishedRules = publishedMapper.selectList(
                new LambdaQueryWrapper<RulePublished>().eq(RulePublished::getStatus, 1));
        List<WarmupCandidate> targets = collectWarmupTargets(publishedRules);
        if (warmupStatus.getState() == com.hengshucredit.rule.server.health.RuleWarmupState.NOT_STARTED) {
            warmupStatus.start(targets.size());
        } else {
            warmupStatus.retry(targets.size());
        }
        int count = 0;
        int failed = 0;
        for (WarmupCandidate target : targets) {
            if (target.failure() != null) {
                failed++;
                warmupStatus.recordFailure(target.definitionId(), target.version(),
                        target.revisionId(), target.artifactDigest(), target.failure());
            } else if (warm(target.published())) {
                count++;
                warmupStatus.recordPrepared();
            } else {
                failed++;
                RulePublished published = target.published();
                warmupStatus.recordFailure(published.getDefinitionId(), published.getVersion(),
                        published.getRevisionId(), published.getArtifactDigest(),
                        new IllegalStateException(lastWarmupFailureMessage == null
                                ? "规则发布版本预热失败" : lastWarmupFailureMessage));
            }
        }
        warmupStatus.complete();
        log.info("QLExpress prepared {} active rule versions before readiness; {} rejected", count, failed);
        return warmupStatus.details();
    }

    private List<WarmupCandidate> collectWarmupTargets(List<RulePublished> publishedRules) {
        List<WarmupCandidate> targets = new java.util.ArrayList<>();
        for (RulePublished published : publishedRules) {
            targets.add(new WarmupCandidate(published, published.getDefinitionId(), published.getVersion(),
                    published.getRevisionId(), published.getArtifactDigest(), null));
            for (RuleVersionBinding binding : bindingMapper.selectList(new LambdaQueryWrapper<RuleVersionBinding>()
                    .eq(RuleVersionBinding::getDefinitionId, published.getDefinitionId())
                    .eq(RuleVersionBinding::getStatus, 1))) {
                if (java.util.Objects.equals(binding.getVersionNo(), published.getVersion())) continue;
                RuleDefinitionVersion version;
                try {
                    version = versionMapper.selectById(binding.getSnapshotId());
                } catch (RuntimeException invalid) {
                    targets.add(new WarmupCandidate(null, published.getDefinitionId(), binding.getVersionNo(),
                            null, null, invalid));
                    log.error("QLExpress warmup could not load rule id={} binding={} version={}: {}",
                            published.getDefinitionId(), binding.getId(), binding.getVersionNo(), invalid.getMessage());
                    continue;
                }
                if (version == null || version.getArtifactId() == null || version.getRevisionId() == null) {
                    log.info("Skipping non-executable legacy binding {} without an artifact", binding.getId());
                    continue;
                }
                try {
                    RulePublished selected = versionService.resolvePublished(published, binding.getId());
                    if (selected == null) {
                        targets.add(new WarmupCandidate(null, published.getDefinitionId(), binding.getVersionNo(),
                                version.getRevisionId(), version.getArtifactDigest(),
                                new IllegalStateException("固定版本未解析到发布制品")));
                    } else {
                        targets.add(new WarmupCandidate(selected, published.getDefinitionId(), binding.getVersionNo(),
                                selected.getRevisionId(), selected.getArtifactDigest(), null));
                    }
                } catch (RuntimeException invalid) {
                    targets.add(new WarmupCandidate(null, published.getDefinitionId(), binding.getVersionNo(),
                            version.getRevisionId(), version.getArtifactDigest(), invalid));
                    log.error("QLExpress warmup rejected rule id={} binding={} version={}: {}",
                            published.getDefinitionId(), binding.getId(), binding.getVersionNo(), invalid.getMessage());
                }
            }
        }
        return targets;
    }

    private boolean warm(RulePublished published) {
        lastWarmupFailureMessage = null;
        try {
            preparePublished(published);
            return true;
        } catch (RuntimeException invalid) {
            lastWarmupFailureMessage = invalid.getMessage();
            log.error("QLExpress warmup rejected rule id={} version={}: {}",
                    published.getDefinitionId(), published.getVersion(), invalid.getMessage());
            return false;
        }
    }

    private record WarmupCandidate(RulePublished published, Long definitionId, Integer version,
                                   Long revisionId, String artifactDigest, Throwable failure) { }
}
