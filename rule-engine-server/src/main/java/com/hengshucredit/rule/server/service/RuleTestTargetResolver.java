package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.model.dto.RuleTestExecutionIdentity;
import com.hengshucredit.rule.model.dto.RuleTestExecutionSource;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService;
import com.hengshucredit.rule.server.artifact.PublishedRuleFieldSnapshotResolver;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.hengshucredit.rule.server.mapper.DecisionArtifactMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** Resolves one test's script, fields and attribution together; never substitutes another revision. */
@Service
public class RuleTestTargetResolver {
    @Resource private RuleDefinitionService definitionService;
    @Resource private RuleRevisionMapper revisionMapper;
    @Resource private DecisionArtifactMapper artifactMapper;
    @Resource private RuleCompileService compileService;
    @Resource private RuleFieldAnalyzer fieldAnalyzer;
    @Resource private PublishedRuleFieldSnapshotResolver fieldSnapshotResolver;
    @Resource private ArtifactRuntimeSnapshotService runtimeSnapshots;
    @Resource private RuleReferenceNamespaceGuard namespaceGuard;
    @Resource private RuleVersionBindingService versionBindings;
    @Resource private com.hengshucredit.rule.server.mapper.RulePublishedMapper publishedMapper;

    public record Target(RuleDefinition definition, Long projectId, String modelJson, String modelType,
                         String compiledScript, RuleFieldAnalyzer.ResolvedFields fields,
                         RulePublished published, ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot,
                         RuleTestExecutionIdentity identity) { }

    public Target resolve(Long definitionId, RuleTestExecutionSource source, String modelJson,
                          String modelType, Long executionProjectId) {
        if (source == null) throw new IllegalArgumentException("请明确选择测试来源");
        source.validate(modelJson);
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null || Integer.valueOf(-1).equals(definition.getStatus()))
            throw new IllegalArgumentException("规则不存在或已删除，无法执行测试");
        if (executionProjectId != null && (executionProjectId <= 0
                || !definitionService.isDefinitionAvailableInProject(definitionId, executionProjectId)))
            throw new IllegalArgumentException("当前规则未关联到所选项目，无法按该项目执行测试");
        Long projectId = executionProjectId == null ? definition.getProjectId() : executionProjectId;
        if ("LATEST".equals(source.getSourceType()) || "BUSINESS_VERSION".equals(source.getSourceType())) {
            RulePublished latest = publishedMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RulePublished>()
                    .eq(RulePublished::getDefinitionId, definitionId).eq(RulePublished::getStatus, 1).last("LIMIT 1"));
            if (latest == null) throw new IllegalArgumentException("规则没有可执行的生产版本");
            Long bindingId = "LATEST".equals(source.getSourceType())
                    ? versionBindings.requireBusinessVersion(definitionId, latest.getVersion()).getId() : Long.valueOf(source.getSourceId());
            RuleDefinitionVersion frozen = versionBindings.resolveSnapshot(definitionId, bindingId);
            RuleTestExecutionSource selected = new RuleTestExecutionSource(); selected.setSourceType("VERSION"); selected.setSourceId(String.valueOf(frozen.getId()));
            Target target = resolve(definitionId, selected, null, null, executionProjectId);
            target.identity().setSourceType(source.getSourceType()); target.identity().setSourceId(source.getSourceId());
            return target;
        }
        RuleTestExecutionIdentity identity = new RuleTestExecutionIdentity();
        identity.setDefinitionId(String.valueOf(definitionId));
        identity.setSourceType(source.getSourceType());
        identity.setSourceId(source.getSourceId() == null ? null : String.valueOf(source.getSourceId()));
        identity.setBaseSourceType(source.getBaseSourceType()); identity.setBaseSourceId(source.getBaseSourceId());

        if ("WORKSPACE".equals(source.getSourceType())) {
            RuleDefinitionContent content = definitionService.getContent(definitionId);
            if (content == null || !Integer.valueOf(1).equals(content.getCompileStatus()))
                throw new IllegalArgumentException("当前已保存内容尚未编译成功，请先编译");
            namespaceGuard.requireLocalWorkspace(definition, content);
            RuleFieldAnalyzer.ResolvedFields fields = new RuleFieldAnalyzer.ResolvedFields(
                    definitionService.listInputFields(definitionId), definitionService.listOutputFields(definitionId));
            return target(definition, projectId, content.getModelJson(), definition.getModelType(), content.getCompiledScript(), fields, null, null, identity);
        }
        if ("PREVIEW".equals(source.getSourceType())) {
            namespaceGuard.requireLocalNamespace(definition, source.getBaseSourceType(), Long.valueOf(source.getBaseSourceId()));
            return draftTarget(definition, projectId,
                    modelJson, modelType == null || modelType.isBlank() ? definition.getModelType() : modelType, identity);
        }

        RuleDefinitionVersion version = null;
        Long sourceId = Long.valueOf(source.getSourceId());
        Long revisionId = sourceId;
        if ("VERSION".equals(source.getSourceType())) {
            version = definitionService.getVersionById(definitionId, sourceId);
            if (version == null) throw new IllegalArgumentException("指定版本快照不存在或不属于当前规则");
            revisionId = version.getRevisionId();
        }
        RuleRevision revision = revisionId == null ? null : revisionMapper.selectById(revisionId);
        if (revision == null || !definitionId.equals(revision.getDefinitionId()) || "DELETED".equals(revision.getState()))
            throw new IllegalArgumentException("指定修订不存在、已删除或不属于当前规则");
        identity.setRevisionId(String.valueOf(revision.getId()));
        identity.setRevisionState(revision.getState());
        identity.setRevisionNo(revision.getRevisionNo());
        if ("DRAFT".equals(revision.getState())) {
            if (version != null) throw new IllegalArgumentException("已发布快照不能绑定可变草稿");
            if (source.getLockVersion() == null || !source.getLockVersion().equals(String.valueOf(revision.getLockVersion())))
                throw new RuleGovernanceException(409, "TEST_DRAFT_LOCK_CONFLICT", "草稿已变化，请重新加载测试字段后重试", List.of());
            namespaceGuard.requireLocalNamespace(definition, revision);
            identity.setLockVersion(revision.getLockVersion());
            return draftTarget(definition, projectId, revision.getModelJson(), definition.getModelType(), identity);
        }
        if (source.getLockVersion() != null)
            throw new RuleGovernanceException(409, "TEST_DRAFT_STATE_CHANGED", "草稿状态已变化，请重新选择测试来源", List.of());
        Long artifactId = revision.getArtifactId();
        DecisionArtifact artifact = artifactId == null ? null : artifactMapper.selectById(artifactId);
        if (version != null && (!Objects.equals(version.getArtifactId(), artifactId)
                || artifact == null || !Objects.equals(version.getArtifactDigest(), artifact.getArtifactDigest())))
            throw new IllegalArgumentException("历史版本快照与修订制品不一致，不能改用当前规则执行");
        RuleFieldAnalyzer.ResolvedFields fields = fieldSnapshotResolver.resolveRevision(revision, artifact);
        var error = fields.getDiagnostics().stream().filter(issue -> "ERROR".equals(issue.getSeverity())).findFirst();
        if (error.isPresent()) throw new IllegalArgumentException("所选修订无法执行：" + error.get().getMessage());
        ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot = runtimeSnapshots.load(artifactId, definitionId, projectId);
        if (snapshot.getCompiledScript() == null || snapshot.getCompiledScript().isBlank()
                || snapshot.getModelJson() == null || snapshot.getModelJson().isBlank())
            throw new IllegalArgumentException("所选修订的制品缺少完整执行内容，不能改用当前规则执行");
        identity.setArtifactId(String.valueOf(artifactId));
        identity.setArtifactDigest(artifact.getArtifactDigest());
        RulePublished published = new RulePublished();
        published.setDefinitionId(definitionId); published.setRuleCode(definition.getRuleCode());
        published.setRevisionId(revision.getId()); published.setArtifactId(artifactId); published.setArtifactDigest(artifact.getArtifactDigest());
        published.setModelJson(snapshot.getModelJson()); published.setModelType(snapshot.getModelType()); published.setCompiledScript(snapshot.getCompiledScript());
        published.setOpenApiConfigJson(version == null ? revision.getOpenApiConfigJson() : version.getOpenApiConfigJson());
        if (version != null) {
            published.setVersion(RuleVersionBindingService.businessVersion(version));
            identity.setVersionNo(published.getVersion());
            published.setVersionBindingId(version.getVersionBindingId()); published.setBindingGeneration(version.getBindingGeneration());
        }
        return target(definition, projectId, snapshot.getModelJson(), snapshot.getModelType(), snapshot.getCompiledScript(), fields, published, snapshot, identity);
    }

    private Target draftTarget(RuleDefinition definition, Long projectId, String modelJson, String modelType,
                               RuleTestExecutionIdentity identity) {
        modelType = modelType == null ? definition.getModelType() : modelType.trim().toUpperCase(java.util.Locale.ROOT);
        CompileResult compiled = compileService.compilePreview(definition.getId(), modelJson, modelType);
        if (!compiled.isSuccess()) throw new IllegalArgumentException(compiled.getErrorMessage());
        RuleFieldAnalyzer.ResolvedFields fields = fieldAnalyzer.resolveFields(definition.getId(), modelJson, modelType, projectId);
        var error = fields.getDiagnostics().stream().filter(issue -> "ERROR".equals(issue.getSeverity())).findFirst();
        if (error.isPresent()) throw new IllegalArgumentException(error.get().getMessage());
        return target(definition, projectId, modelJson, modelType, compiled.getCompiledScript(), fields, null, null, identity);
    }

    private Target target(RuleDefinition definition, Long projectId, String modelJson, String modelType, String compiledScript,
                          RuleFieldAnalyzer.ResolvedFields fields, RulePublished published,
                          ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot, RuleTestExecutionIdentity identity) {
        identity.setModelType(modelType);
        identity.setModelDigest(modelJson == null ? null : Sha256Digests.bytes(modelJson.getBytes(StandardCharsets.UTF_8)));
        return new Target(definition, projectId, modelJson, modelType, compiledScript, fields, published, snapshot, identity);
    }
}
