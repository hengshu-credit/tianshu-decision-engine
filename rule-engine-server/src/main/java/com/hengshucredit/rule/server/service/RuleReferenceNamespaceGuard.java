package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.entity.RuleDefinitionVersion;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.ArtifactDeployment;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.server.artifact.PublishedRuleFieldSnapshotResolver;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.server.mapper.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Guards recompilation in the current environment against foreign artifact reference IDs. */
@Service
public class RuleReferenceNamespaceGuard {
    @Resource private RuleRevisionMapper revisionMapper;
    @Resource private RuleDefinitionVersionMapper versionMapper;
    @Resource private DecisionArtifactMapper artifactMapper;
    @Resource private RuleDefinitionContentMapper contentMapper;
    @Resource private ArtifactDeploymentMapper deploymentMapper;
    @Resource private PublishedRuleFieldSnapshotResolver snapshotResolver;

    public record NamespaceProof(boolean imported, RuleRevision frozenSource) { }

    private static final class Traversal {
        final Set<Long> path = new HashSet<>();
        final Set<Long> completed = new HashSet<>();
        RuleRevision frozenSource;
        NamespaceProof proof() { return new NamespaceProof(frozenSource != null, frozenSource); }
    }

    public void requireLocalNamespace(RuleDefinition definition, RuleRevision revision) {
        requireLocal(inspectNamespace(definition, revision));
    }

    public NamespaceProof inspectNamespace(RuleDefinition definition, RuleRevision revision) {
        requireDefinition(definition);
        Traversal traversal = new Traversal();
        visitRevision(definition.getId(), revision, traversal);
        return traversal.proof();
    }

    public void requireLocalNamespace(RuleDefinition definition, String sourceType, Long sourceId) {
        requireLocal(inspectNamespace(definition, sourceType, sourceId));
    }

    /** Read-only proof for a historical catalog; never resolves source IDs against the live catalog. */
    public NamespaceProof inspectNamespace(RuleDefinition definition, String sourceType, Long sourceId) {
        requireDefinition(definition);
        if (sourceId == null || sourceId <= 0) throw unverifiable();
        Traversal traversal = new Traversal();
        switch (sourceType == null ? "" : sourceType) {
            case "REVISION" -> visitRevision(definition.getId(), loadRevision(sourceId), traversal);
            case "VERSION" -> visitVersion(definition.getId(), sourceId, traversal);
            case "WORKSPACE" -> {
                if (!definition.getId().equals(sourceId)) throw unverifiable();
                return inspectWorkspace(definition, loadContent(definition.getId()));
            }
            default -> throw unverifiable();
        }
        return traversal.proof();
    }

    /** Check the exact content already loaded by the caller, not a second, potentially newer projection. */
    public void requireLocalWorkspace(RuleDefinition definition, RuleDefinitionContent content) {
        requireLocal(inspectWorkspace(definition, content));
    }

    private NamespaceProof inspectWorkspace(RuleDefinition definition, RuleDefinitionContent content) {
        requireDefinition(definition);
        if (content != null && !definition.getId().equals(content.getDefinitionId())) throw unverifiable();
        List<RuleRevision> candidates = content == null || content.getModelJson() == null
                ? List.of() : matchingWorkspaceRevisions(definition.getId(), content.getModelJson());
        Traversal traversal = new Traversal();
        for (RuleRevision candidate : candidates) {
            // Matching text is only a way to find possible provenance, never proof of a local namespace.
            // All matches must be safe, including imported twins with identical numeric IDs and JSON.
            visitRevision(definition.getId(), candidate, traversal);
        }
        if (candidates.isEmpty() && hasImportedDeployment(definition.getId())) throw unverifiable();
        return traversal.proof();
    }

    private void visitRevision(Long definitionId, RuleRevision revision, Traversal traversal) {
        if (revision == null || !definitionId.equals(revision.getDefinitionId())) throw unverifiable();
        if (revision.getArtifactId() == null && revision.getSourceType() == null && revision.getSourceId() == null
                && revision.getBaseRevisionId() == null && revision.getBaseArtifactId() == null
                && hasImportedDeployment(definitionId)) throw unverifiable();
        Long revisionId = revision.getId();
        if (revisionId != null && traversal.completed.contains(revisionId)) return;
        if (traversal.path.size() >= 256 || revisionId != null && (!positive(revisionId) || !traversal.path.add(revisionId))) throw unverifiable();
        if (revision.getArtifactId() != null) verifyArtifact(definitionId, revision, traversal);
        String sourceType = revision.getSourceType();
        Long sourceId = revision.getSourceId();
        if (sourceType != null || sourceId != null) {
            if (!positive(sourceId)) throw unverifiable();
            if ("REVISION".equals(sourceType)) visitRevision(definitionId, loadRevision(sourceId), traversal);
            else if ("VERSION".equals(sourceType)) visitVersion(definitionId, sourceId, traversal);
            else throw unverifiable();
        }
        RuleRevision base = null;
        if (revision.getBaseRevisionId() != null) {
            if (!positive(revision.getBaseRevisionId())) throw unverifiable();
            base = loadRevision(revision.getBaseRevisionId());
            visitRevision(definitionId, base, traversal);
        }
        if (revision.getBaseArtifactId() != null) {
            if (!positive(revision.getBaseArtifactId())) throw unverifiable();
            if (base == null) base = findArtifactRevision(definitionId, revision.getBaseArtifactId());
            if (base == null || !revision.getBaseArtifactId().equals(base.getArtifactId())) throw unverifiable();
            visitRevision(definitionId, base, traversal);
        }
        if (revisionId != null) { traversal.path.remove(revisionId); traversal.completed.add(revisionId); }
    }

    private void visitVersion(Long definitionId, Long versionId, Traversal traversal) {
        RuleDefinitionVersion version = positive(versionId) ? loadVersion(versionId) : null;
        if (version == null || !versionId.equals(version.getId()) || !definitionId.equals(version.getDefinitionId())) throw unverifiable();
        if (version.getRevisionId() == null) {
            // Pre-artifact local snapshots are supported, but cannot establish provenance in an imported workspace.
            if (version.getArtifactId() != null || version.getArtifactDigest() != null || hasImportedDeployment(definitionId)) throw unverifiable();
            return;
        }
        RuleRevision revision = loadRevision(version.getRevisionId());
        if (revision == null || !Objects.equals(version.getArtifactId(), revision.getArtifactId())) throw unverifiable();
        if (version.getArtifactId() != null) {
            DecisionArtifact artifact = loadArtifact(version.getArtifactId());
            if (artifact == null || !Objects.equals(version.getArtifactDigest(), artifact.getArtifactDigest())) throw unverifiable();
        } else if (version.getArtifactDigest() != null) throw unverifiable();
        visitRevision(definitionId, revision, traversal);
    }

    private void verifyArtifact(Long definitionId, RuleRevision revision, Traversal traversal) {
        if (!positive(revision.getArtifactId()) || !positive(revision.getId())) throw unverifiable();
        DecisionArtifact artifact = loadArtifact(revision.getArtifactId());
        if (artifact == null) throw unverifiable();
        RuleFieldAnalyzer.ResolvedFields fields = verifyRevision(revision, artifact);
        if (fields == null || fields.getDiagnostics().stream().anyMatch(issue -> "ERROR".equals(issue.getSeverity()))) throw unverifiable();
        // Package verification above validates source metadata and (for imports) the target deployment.
        // Equal numbers in a foreign manifest are not a local ownership proof.
        if (!definitionId.equals(artifact.getDefinitionId()) || !revision.getId().equals(artifact.getRevisionId())) {
            if (traversal.frozenSource != null && !artifact.getId().equals(traversal.frozenSource.getArtifactId())) throw unverifiable();
            traversal.frozenSource = revision;
        }
    }

    private void requireLocal(NamespaceProof proof) {
        if (proof.imported()) throw failure("REFERENCE_NAMESPACE_MIGRATION_REQUIRED",
                "该配置沿用导入制品的源环境引用 ID，不能按当前环境编译或保存。请先使用离线配置迁移完成引用映射；如需恢复运行，请重新部署原制品。");
    }

    private void requireDefinition(RuleDefinition definition) {
        if (definition == null || !positive(definition.getId())) throw unverifiable();
    }
    private boolean positive(Long id) { return id != null && id > 0; }
    private RuleGovernanceException unverifiable() {
        return failure("REFERENCE_NAMESPACE_UNVERIFIABLE",
                "无法完整验证该配置的引用来源，不能按当前环境编译或保存。请核对来源修订，或使用离线配置迁移完成引用映射。");
    }
    private RuleGovernanceException failure(String code, String message) {
        return new RuleGovernanceException(409, code, message, List.of(RuleValidationIssue.error(code, "$.source", message)));
    }

    protected RuleRevision loadRevision(Long id) { return revisionMapper.selectById(id); }
    protected RuleDefinitionVersion loadVersion(Long id) { return versionMapper.selectById(id); }
    protected DecisionArtifact loadArtifact(Long id) { return artifactMapper.selectById(id); }
    protected RuleDefinitionContent loadContent(Long id) {
        return contentMapper.selectOne(new LambdaQueryWrapper<RuleDefinitionContent>()
                .eq(RuleDefinitionContent::getDefinitionId, id));
    }
    protected List<RuleRevision> matchingWorkspaceRevisions(Long definitionId, String modelJson) {
        return revisionMapper.selectList(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId).eq(RuleRevision::getModelJson, modelJson));
    }
    protected RuleRevision findArtifactRevision(Long definitionId, Long artifactId) {
        return revisionMapper.selectOne(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId).eq(RuleRevision::getArtifactId, artifactId)
                .orderByDesc(RuleRevision::getId).last("LIMIT 1"));
    }
    protected boolean hasImportedDeployment(Long definitionId) {
        return deploymentMapper.selectCount(new LambdaQueryWrapper<ArtifactDeployment>()
                .eq(ArtifactDeployment::getTargetDefinitionId, definitionId)
                .eq(ArtifactDeployment::getStatus, "DEPLOYED")) > 0;
    }
    protected RuleFieldAnalyzer.ResolvedFields verifyRevision(RuleRevision revision, DecisionArtifact artifact) {
        return snapshotResolver.resolveRevision(revision, artifact);
    }
}
