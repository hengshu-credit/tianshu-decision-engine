package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hengshucredit.rule.model.entity.DecisionArtifactComponent;
import com.hengshucredit.rule.model.entity.ResourceImpactAnalysis;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.mapper.DecisionArtifactComponentMapper;
import com.hengshucredit.rule.server.mapper.ResourceImpactAnalysisMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ResourceImpactAnalysisService {
    private static final int TOKEN_TTL_MINUTES = 10;

    @Resource
    private ResourceImpactAnalysisMapper analysisMapper;
    @Resource
    private RuleLineageService lineageService;
    @Resource
    private RuleRevisionMapper revisionMapper;
    @Resource
    private DecisionArtifactComponentMapper artifactComponentMapper;
    @Resource
    private ConsoleOperatorResolver operatorResolver;

    public ResourceImpactAnalysis analyze(String resourceType, Long resourceId,
                                          String action, String actor) {
        String normalizedType = requiredText(resourceType, "resourceType");
        String normalizedAction = requiredText(action, "action");
        if (resourceId == null) {
            throw new IllegalArgumentException("resourceId must not be null");
        }

        Map<String, Object> report = currentImpactReport(normalizedType, resourceId, normalizedAction);
        String reportJson = CanonicalJson.write(report);
        LocalDateTime now = LocalDateTime.now();

        ResourceImpactAnalysis analysis = new ResourceImpactAnalysis();
        analysis.setAnalysisToken(UUID.randomUUID().toString());
        analysis.setResourceType(normalizedType);
        analysis.setResourceId(resourceId);
        analysis.setAction(normalizedAction);
        analysis.setImpactDigest(Sha256Digests.text(reportJson));
        analysis.setReportJson(reportJson);
        analysis.setStatus("PENDING");
        analysis.setExpiresAt(now.plusMinutes(TOKEN_TTL_MINUTES));
        analysis.setCreateBy(actor == null || actor.isBlank()
                ? ConsoleOperatorResolver.SYSTEM_CONSOLE : actor);
        analysis.setCreateTime(now);
        insertAnalysis(analysis);
        return analysis;
    }

    @Transactional
    public ResourceImpactAnalysis assertCurrent(String token, String resourceType,
                                                Long resourceId, String action) {
        String normalizedToken = requiredToken(token);
        String normalizedType = requiredText(resourceType, "resourceType");
        String normalizedAction = requiredText(action, "action");
        ResourceImpactAnalysis analysis = loadAnalysis(normalizedToken);
        if (analysis == null
                || !"PENDING".equals(analysis.getStatus())
                || !normalizedType.equals(analysis.getResourceType())
                || !normalizedAction.equals(analysis.getAction())
                || resourceId == null
                || !resourceId.equals(analysis.getResourceId())) {
            throw new IllegalStateException("Impact analysis token is invalid or already used");
        }
        if (analysis.getExpiresAt() == null || !analysis.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Impact analysis token has expired");
        }

        String currentDigest = Sha256Digests.text(CanonicalJson.write(
                currentImpactReport(normalizedType, resourceId, normalizedAction)));
        if (!currentDigest.equals(analysis.getImpactDigest())) {
            throw new IllegalStateException("Resource references changed; run impact analysis again");
        }

        analysis.setStatus("CONFIRMED");
        analysis.setConfirmBy(confirmingActor());
        analysis.setConfirmTime(LocalDateTime.now());
        updateAnalysis(analysis);
        return analysis;
    }

    protected Map<String, Object> currentImpactReport(String resourceType, Long resourceId,
                                                      String action) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("resourceType", resourceType);
        report.put("resourceId", resourceId);
        report.put("action", action);
        report.put("lineage", lineageService.graph(resourceType, resourceId, "DOWNSTREAM", null));
        if ("MODEL".equals(resourceType)) {
            report.put("revisionReferences", modelRevisionReferences(resourceId));
            report.put("frozenArtifactReferences", modelArtifactReferences(resourceId));
        }
        return report;
    }

    private List<Map<String, Object>> modelRevisionReferences(Long modelId) {
        if (revisionMapper == null) {
            return List.of();
        }
        List<RuleRevision> revisions = revisionMapper.selectList(
                new LambdaQueryWrapper<RuleRevision>()
                        .in(RuleRevision::getState, "DRAFT", "REVIEW", "APPROVED", "PUBLISHED")
                        .orderByAsc(RuleRevision::getDefinitionId)
                        .orderByAsc(RuleRevision::getRevisionNo));
        List<Map<String, Object>> references = new ArrayList<>();
        for (RuleRevision revision : revisions) {
            if (revision.getModelJson() == null || revision.getModelJson().isBlank()) {
                continue;
            }
            Object modelJson;
            try {
                modelJson = JSON.parse(revision.getModelJson());
            } catch (RuntimeException ignored) {
                continue;
            }
            List<String> paths = OperandDependencyCollector.collectReferences(modelJson).stream()
                    .filter(reference -> "MODEL".equalsIgnoreCase(reference.getRefType()))
                    .filter(reference -> modelId.equals(reference.getRefId()))
                    .map(OperandDependencyCollector.Reference::getPath)
                    .distinct()
                    .sorted()
                    .toList();
            if (!paths.isEmpty()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("definitionId", revision.getDefinitionId());
                item.put("revisionId", revision.getId());
                item.put("revisionNo", revision.getRevisionNo());
                item.put("state", revision.getState());
                item.put("paths", paths);
                references.add(item);
            }
        }
        return references;
    }

    private List<Map<String, Object>> modelArtifactReferences(Long modelId) {
        if (artifactComponentMapper == null) {
            return List.of();
        }
        List<DecisionArtifactComponent> components = artifactComponentMapper.selectList(
                new LambdaQueryWrapper<DecisionArtifactComponent>()
                        .eq(DecisionArtifactComponent::getSourceType, "MODEL")
                        .eq(DecisionArtifactComponent::getSourceId, modelId)
                        .orderByAsc(DecisionArtifactComponent::getArtifactId)
                        .orderByAsc(DecisionArtifactComponent::getComponentId));
        return components.stream()
                .sorted(Comparator.comparing(DecisionArtifactComponent::getArtifactId)
                        .thenComparing(DecisionArtifactComponent::getComponentId))
                .map(component -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("artifactId", component.getArtifactId());
                    item.put("componentId", component.getComponentId());
                    item.put("sourceVersion", component.getSourceVersion());
                    item.put("contentDigest", component.getContentDigest());
                    item.put("immutable", true);
                    return item;
                })
                .toList();
    }

    protected void insertAnalysis(ResourceImpactAnalysis analysis) {
        analysisMapper.insert(analysis);
    }

    protected ResourceImpactAnalysis loadAnalysis(String token) {
        return analysisMapper.selectOne(new LambdaQueryWrapper<ResourceImpactAnalysis>()
                .eq(ResourceImpactAnalysis::getAnalysisToken, token)
                .last("LIMIT 1"));
    }

    protected void updateAnalysis(ResourceImpactAnalysis analysis) {
        int updated = analysisMapper.update(null,
                new LambdaUpdateWrapper<ResourceImpactAnalysis>()
                        .eq(ResourceImpactAnalysis::getId, analysis.getId())
                        .eq(ResourceImpactAnalysis::getStatus, "PENDING")
                        .set(ResourceImpactAnalysis::getStatus, analysis.getStatus())
                        .set(ResourceImpactAnalysis::getConfirmBy, analysis.getConfirmBy())
                        .set(ResourceImpactAnalysis::getConfirmTime, analysis.getConfirmTime()));
        if (updated != 1) {
            throw new IllegalStateException("Impact analysis token was already confirmed");
        }
    }

    protected String confirmingActor() {
        return operatorResolver == null
                ? ConsoleOperatorResolver.SYSTEM_CONSOLE : operatorResolver.resolve();
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String requiredToken(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        return value.trim();
    }
}
