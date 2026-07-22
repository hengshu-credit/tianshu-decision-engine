package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleLifecycleEvent;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.artifact.DecisionArtifactService;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleLifecycleEventMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RuleArtifactMigrationService {
    private static final String MIGRATION_ACTOR = "SYSTEM_MIGRATION";

    @Resource
    private RuleDefinitionMapper definitionMapper;
    @Resource
    private RuleDefinitionContentMapper contentMapper;
    @Resource
    private RulePublishedMapper publishedMapper;
    @Resource
    private RuleRevisionMapper revisionMapper;
    @Resource
    private RuleLifecycleEventMapper eventMapper;
    @Resource
    private DecisionArtifactService artifactService;

    public MigrationReport migrateAll() {
        MigrationReport report = new MigrationReport();
        for (RuleDefinition definition : loadDefinitions()) {
            RuleRevision existing = loadExistingRevision(definition.getId());
            RulePublished published = loadPublished(definition.getId());
            if (existing != null && !canResume(existing, published)) {
                report.skipped++;
                continue;
            }
            migrateDefinition(definition, published, existing, report);
        }
        return report;
    }

    private boolean canResume(RuleRevision revision, RulePublished published) {
        return revision != null
                && MIGRATION_ACTOR.equals(revision.getCreateBy())
                && published != null
                && Integer.valueOf(1).equals(published.getStatus())
                && published.getArtifactId() == null
                && !"PUBLISHED".equals(revision.getState());
    }

    private void migrateDefinition(RuleDefinition definition, RulePublished published,
                                   RuleRevision existingRevision, MigrationReport report) {
        RuleDefinitionContent content = loadContent(definition.getId());
        boolean online = published != null && Integer.valueOf(1).equals(published.getStatus());
        RuleRevision revision = existingRevision == null
                ? migrationRevision(definition, content, published, online) : existingRevision;
        if (existingRevision == null) {
            insertRevision(revision);
        } else {
            revision.setState("REVIEW");
            revision.setUpdateBy(MIGRATION_ACTOR);
            revision.setUpdateTime(LocalDateTime.now());
            updateRevision(revision);
        }

        if (!online) {
            insertMigrationEvent(revision, "MIGRATE_DRAFT", "Legacy draft revision created");
            report.draftCreated++;
            return;
        }

        try {
            DecisionArtifact artifact = buildArtifact(revision.getId(), definition.getId());
            DecisionArtifactService.RuntimeProjection projection =
                    loadRuntimeProjection(artifact, published);
            verifySameRuntimeProjection(projection, published);
            LocalDateTime now = LocalDateTime.now();
            revision.setState("PUBLISHED");
            revision.setArtifactId(artifact.getId());
            revision.setApproveBy(MIGRATION_ACTOR);
            revision.setApproveTime(now);
            revision.setPublishBy(MIGRATION_ACTOR);
            revision.setPublishTime(now);
            revision.setUpdateBy(MIGRATION_ACTOR);
            revision.setUpdateTime(now);
            updateRevision(revision);

            published.setRevisionId(revision.getId());
            published.setArtifactId(artifact.getId());
            published.setArtifactDigest(artifact.getArtifactDigest());
            updatePublished(published);
            insertMigrationEvent(revision, "MIGRATE_PUBLISHED_ARTIFACT",
                    artifact.getArtifactDigest());
            report.publishedArtifactCreated++;
        } catch (RuntimeException error) {
            revision.setState("DRAFT");
            revision.setArtifactId(null);
            revision.setUpdateBy(MIGRATION_ACTOR);
            revision.setUpdateTime(LocalDateTime.now());
            updateRevision(revision);
            String details = error.getMessage() == null ? error.getClass().getSimpleName()
                    : error.getMessage();
            insertMigrationEvent(revision, "MIGRATE_UNRESOLVED", details);
            report.unresolved++;
            report.issues.add("definitionId=" + definition.getId() + ": " + details);
        }
    }

    private RuleRevision migrationRevision(RuleDefinition definition,
                                           RuleDefinitionContent content,
                                           RulePublished published,
                                           boolean online) {
        RuleRevision revision = new RuleRevision();
        revision.setDefinitionId(definition.getId());
        Integer version = published == null ? definition.getCurrentVersion() : published.getVersion();
        revision.setRevisionNo(version == null || version <= 0 ? 1 : version);
        revision.setState(online ? "REVIEW" : "DRAFT");
        revision.setModelJson(published != null && published.getModelJson() != null
                ? published.getModelJson() : content == null ? "{}" : content.getModelJson());
        revision.setCompiledScript(published != null && published.getCompiledScript() != null
                ? published.getCompiledScript() : content == null ? null : content.getCompiledScript());
        revision.setCompiledType(published != null && published.getCompiledType() != null
                ? published.getCompiledType() : content == null ? null : content.getCompiledType());
        revision.setOpenApiConfigJson(content == null ? null : content.getOpenApiConfigJson());
        revision.setLockVersion(0);
        revision.setCreateBy(MIGRATION_ACTOR);
        revision.setCreateTime(LocalDateTime.now());
        revision.setUpdateBy(MIGRATION_ACTOR);
        revision.setUpdateTime(LocalDateTime.now());
        return revision;
    }

    protected List<RuleDefinition> loadDefinitions() {
        return definitionMapper.selectList(new LambdaQueryWrapper<RuleDefinition>()
                .and(wrapper -> wrapper.isNull(RuleDefinition::getStatus)
                        .or().ne(RuleDefinition::getStatus, -1))
                .orderByAsc(RuleDefinition::getId));
    }

    protected RuleRevision loadExistingRevision(Long definitionId) {
        return revisionMapper.selectOne(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId)
                .orderByDesc(RuleRevision::getRevisionNo)
                .last("LIMIT 1"));
    }

    protected RuleDefinitionContent loadContent(Long definitionId) {
        return contentMapper.selectOne(new LambdaQueryWrapper<RuleDefinitionContent>()
                .eq(RuleDefinitionContent::getDefinitionId, definitionId)
                .last("LIMIT 1"));
    }

    protected RulePublished loadPublished(Long definitionId) {
        return publishedMapper.selectOne(new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId, definitionId)
                .eq(RulePublished::getStatus, 1)
                .last("LIMIT 1"));
    }

    protected void insertRevision(RuleRevision revision) {
        revisionMapper.insert(revision);
    }

    protected void updateRevision(RuleRevision revision) {
        revisionMapper.updateById(revision);
    }

    protected DecisionArtifact buildArtifact(Long revisionId, Long definitionId) {
        return artifactService.buildApprovedArtifact(revisionId, MIGRATION_ACTOR);
    }

    protected DecisionArtifactService.RuntimeProjection loadRuntimeProjection(
            DecisionArtifact artifact, RulePublished published) {
        return artifactService.loadRuntimeProjection(artifact.getId());
    }

    private void verifySameRuntimeProjection(DecisionArtifactService.RuntimeProjection projection,
                                             RulePublished published) {
        if (projection == null
                || !same(projection.getModelJson(), published.getModelJson())
                || !same(projection.getCompiledScript(), published.getCompiledScript())) {
            throw new IllegalStateException("迁移制品运行投影与当前线上规则不一致，已保留原运行版本");
        }
    }

    private boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    protected void updatePublished(RulePublished published) {
        publishedMapper.updateById(published);
    }

    protected void insertMigrationEvent(RuleRevision revision, String action, String details) {
        RuleLifecycleEvent event = new RuleLifecycleEvent();
        event.setDefinitionId(revision.getDefinitionId());
        event.setRevisionId(revision.getId());
        event.setAction(action);
        event.setToState(revision.getState());
        event.setActor(MIGRATION_ACTOR);
        event.setRequestSource("MIGRATION");
        event.setArtifactDigest("PUBLISHED".equals(revision.getState()) ? details : null);
        event.setDetailsJson(details);
        event.setCreateTime(LocalDateTime.now());
        eventMapper.insert(event);
    }

    @Data
    public static class MigrationReport {
        private int draftCreated;
        private int publishedArtifactCreated;
        private int unresolved;
        private int skipped;
        private List<String> issues = new ArrayList<>();
    }
}
