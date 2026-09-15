package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.RuleVersionBindingMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionVersionMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import com.hengshucredit.rule.server.artifact.RuleSchemaCompatibilityService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;

/** Checks the effective version graph, not unrelated unpublished drafts. */
@Service
public class RulePublicationValidator {
    @Resource private RuleVersionBindingMapper bindings;
    @Resource private RuleDefinitionVersionMapper snapshots;
    @Resource private RuleRevisionMapper revisions;

    public String previewCycle(Long definitionId, String modelJson) {
        RuleRevision candidate = new RuleRevision(); candidate.setDefinitionId(definitionId);
        candidate.setPublishMode("NEW"); candidate.setModelJson(modelJson);
        List<RuleVersionBinding> active = bindings.selectList(new LambdaQueryWrapper<RuleVersionBinding>().eq(RuleVersionBinding::getStatus, 1));
        Map<Long, RuleDefinitionVersion> history = new HashMap<>();
        for (RuleVersionBinding binding : active) {
            RuleDefinitionVersion snapshot = snapshots.selectById(binding.getSnapshotId());
            if (snapshot != null) history.put(snapshot.getId(), snapshot);
        }
        try { validateGraph(candidate, active, history); return null; }
        catch (IllegalStateException invalid) { return invalid.getMessage(); }
    }

    public void validate(RuleRevision candidate) {
        // Current reads serialize activation checks, so simultaneous publications cannot introduce a cycle.
        List<RuleVersionBinding> active = bindings.selectList(new LambdaQueryWrapper<RuleVersionBinding>()
                .eq(RuleVersionBinding::getStatus, 1).orderByAsc(RuleVersionBinding::getId).last("FOR UPDATE"));
        Map<Long, RuleDefinitionVersion> history = new HashMap<>();
        for (RuleVersionBinding binding : active) {
            RuleDefinitionVersion snapshot = snapshots.selectOne(new LambdaQueryWrapper<RuleDefinitionVersion>()
                    .eq(RuleDefinitionVersion::getDefinitionId, binding.getDefinitionId())
                    .eq(RuleDefinitionVersion::getId, binding.getSnapshotId()).last("FOR UPDATE"));
            if (snapshot != null) history.put(snapshot.getId(), snapshot);
        }
        Set<Long> affected = validateGraph(candidate, active, history);
        if (!affected.isEmpty()) {
            RuleVersionBinding prior = "OVERWRITE".equals(candidate.getPublishMode())
                    ? active.stream().filter(b -> Objects.equals(b.getId(), candidate.getTargetVersionId())).findFirst().orElse(null)
                    : active.stream().filter(b -> b.getDefinitionId().equals(candidate.getDefinitionId())).max(Comparator.comparingInt(RuleVersionBinding::getVersionNo)).orElse(null);
            RuleDefinitionVersion old = prior == null ? null : history.get(prior.getSnapshotId());
            RuleRevision previous = old == null || old.getRevisionId() == null ? null : revisions.selectById(old.getRevisionId());
            if (previous == null || previous.getInputSchemaJson() == null || previous.getOutputSchemaJson() == null)
                throw new IllegalStateException("存在调用方，但旧版本缺少可验证的字段契约，请先补全历史版本制品");
            if (new RuleSchemaCompatibilityService().compare(previous.getInputSchemaJson(), previous.getOutputSchemaJson(),
                    candidate.getInputSchemaJson(), candidate.getOutputSchemaJson()).hasBreakingChanges())
                throw new IllegalStateException("发布会破坏调用方字段契约，请先调整依赖规则；受影响规则: " + affected);
        }
    }

    Set<Long> validateGraph(RuleRevision candidate, List<RuleVersionBinding> active, Map<Long, RuleDefinitionVersion> history) {
        Map<Long, RuleVersionBinding> byId = new HashMap<>(), latest = new HashMap<>();
        Map<String, String> models = new LinkedHashMap<>();
        Map<String, Long> definitions = new HashMap<>();
        for (RuleVersionBinding binding : active) {
            byId.put(binding.getId(), binding);
            latest.merge(binding.getDefinitionId(), binding, (a,b) -> a.getVersionNo() > b.getVersionNo() ? a : b);
            RuleDefinitionVersion snapshot = history.get(binding.getSnapshotId());
            if (snapshot != null) { models.put(binding.getId().toString(), snapshot.getModelJson()); definitions.put(binding.getId().toString(), binding.getDefinitionId()); }
        }
        String candidateKey = "OVERWRITE".equals(candidate.getPublishMode()) ? String.valueOf(candidate.getTargetVersionId()) : "candidate";
        models.put(candidateKey, candidate.getModelJson()); definitions.put(candidateKey, candidate.getDefinitionId());
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        Set<Long> affected = new LinkedHashSet<>();
        for (Map.Entry<String, String> node : models.entrySet()) {
            Set<String> edges = new LinkedHashSet<>();
            List<OperandDependencyCollector.Reference> references;
            try { references = OperandDependencyCollector.collectReferences(JSON.parse(node.getValue())); }
            catch (RuntimeException error) { graph.put(node.getKey(), Set.of("missing:model")); continue; }
            for (OperandDependencyCollector.Reference ref : references) {
                if (!"RULE".equals(ref.getRefType()) || ref.getRefId() == null) continue;
                String target;
                if ("FIXED".equals(ref.getVersionMode())) {
                    RuleVersionBinding binding = byId.get(ref.getVersionBindingId());
                    if (binding == null || !binding.getDefinitionId().equals(ref.getRefId())) { edges.add("missing:fixed"); continue; }
                    target = binding.getId().toString();
                } else if (candidate.getDefinitionId().equals(ref.getRefId()) && !"OVERWRITE".equals(candidate.getPublishMode())) {
                    target = candidateKey;
                } else {
                    RuleVersionBinding binding = latest.get(ref.getRefId());
                    if (binding == null) { edges.add("missing:latest"); continue; }
                    target = binding.getId().toString();
                }
                edges.add(target);
                if (target.equals(candidateKey) && !node.getKey().equals(candidateKey)) affected.add(definitions.get(node.getKey()));
            }
            graph.put(node.getKey(), edges);
        }
        visit(candidateKey, graph, new HashSet<>(), new HashSet<>());
        return affected;
    }
    private void visit(String node, Map<String, Set<String>> graph, Set<String> visiting, Set<String> visited) {
        if (!graph.containsKey(node)) throw new IllegalStateException("被引用规则版本不存在或缺少有效配置");
        if (visiting.contains(node)) throw new IllegalStateException("发布后的版本引用形成循环");
        if (visited.contains(node)) return;
        visiting.add(node);
        for (String next : graph.getOrDefault(node, Collections.emptySet())) visit(next, graph, visiting, visited);
        visiting.remove(node); visited.add(node);
    }
}
