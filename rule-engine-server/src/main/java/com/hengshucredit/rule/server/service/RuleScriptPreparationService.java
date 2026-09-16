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

    public QLExpressEngine.PreparedScript prepareArtifact(Long artifactId, Long definitionId, Long projectId) {
        ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot = snapshots.load(artifactId, definitionId, projectId);
        registerFunctions(snapshot.getFunctions());
        return engine.prepare(snapshot.getCompiledScript(), constantNames(snapshot.getVariables()));
    }

    public QLExpressEngine.PreparedScript prepareProject(String script, Long projectId) {
        registerFunctions(functionService.listByProject(projectId));
        List<RuleVariable> variables = projectId != null && projectId > 0
                ? variableService.listByProject(projectId, null) : variableService.listGlobalOnly();
        return engine.prepare(script, constantNames(variables));
    }

    private void registerFunctions(List<RuleFunction> functions) {
        functionRegistrar.registerJavaFunctions(functions, engine.getRunner());
        functionRegistrar.registerBeanFunctions(functions, engine.getRunner());
        functionRegistrar.registerServerFunctions(engine.getRunner());
        runtimeInvoker.register(engine.getRunner());
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
        int count = 0;
        int failed = 0;
        for (RulePublished published : publishedMapper.selectList(
                new LambdaQueryWrapper<RulePublished>().eq(RulePublished::getStatus, 1))) {
            if (warm(published)) count++; else failed++;
            for (RuleVersionBinding binding : bindingMapper.selectList(new LambdaQueryWrapper<RuleVersionBinding>()
                    .eq(RuleVersionBinding::getDefinitionId, published.getDefinitionId())
                    .eq(RuleVersionBinding::getStatus, 1))) {
                if (java.util.Objects.equals(binding.getVersionNo(), published.getVersion())) continue;
                RuleDefinitionVersion version = versionMapper.selectById(binding.getSnapshotId());
                if (version == null || version.getArtifactId() == null || version.getRevisionId() == null) {
                    log.info("Skipping non-executable legacy binding {} without an artifact", binding.getId());
                    continue;
                }
                if (warm(versionService.resolvePublished(published, binding.getId()))) count++; else failed++;
            }
        }
        log.info("QLExpress prepared {} active rule versions before readiness; {} rejected", count, failed);
    }

    private boolean warm(RulePublished published) {
        try {
            preparePublished(published);
            return true;
        } catch (RuntimeException invalid) {
            log.error("QLExpress warmup rejected rule id={} version={}: {}",
                    published.getDefinitionId(), published.getVersion(), invalid.getMessage());
            return false;
        }
    }
}
