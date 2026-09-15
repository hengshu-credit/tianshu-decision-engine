package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.RuleDefinitionVersionMapper;
import com.hengshucredit.rule.server.mapper.RuleVersionBindingMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RuleVersionBindingService {
    @Resource private RuleVersionBindingMapper bindingMapper;
    @Resource private RuleDefinitionVersionMapper versionMapper;
    @Resource private com.hengshucredit.rule.server.artifact.PublishedRuleFieldSnapshotResolver fieldSnapshotResolver;

    /** Caller holds the definition row lock. Existing immutable history is never rewritten. */
    public void ensureLegacyBindings(Long definitionId) {
        Set<Integer> known = new HashSet<>();
        bindings(definitionId).forEach(value -> known.add(value.getVersionNo()));
        Map<Integer, RuleDefinitionVersion> latest = new TreeMap<>();
        for (RuleDefinitionVersion snapshot : history(definitionId)) {
            int business = businessVersion(snapshot);
            RuleDefinitionVersion prior = latest.get(business);
            if (prior == null || snapshot.getVersion() > prior.getVersion()) latest.put(business, snapshot);
        }
        for (Map.Entry<Integer, RuleDefinitionVersion> entry : latest.entrySet()) {
            if (known.contains(entry.getKey())) continue;
            RuleVersionBinding value = new RuleVersionBinding();
            value.setDefinitionId(definitionId); value.setVersionNo(entry.getKey());
            value.setGeneration(1L); value.setSnapshotId(entry.getValue().getId());
            value.setStatus(1); value.setUpdateTime(LocalDateTime.now()); insertBinding(value);
        }
    }

    public static int businessVersion(RuleDefinitionVersion snapshot) {
        return snapshot.getBusinessVersion() == null ? snapshot.getVersion() : snapshot.getBusinessVersion();
    }

    /** Called only inside the publication transaction after locking the definition. */
    @Transactional
    public RuleVersionBinding activate(RuleRevision revision, RuleDefinitionVersion snapshot) {
        ensureLegacyBindings(revision.getDefinitionId());
        List<RuleVersionBinding> existing = bindings(revision.getDefinitionId());
        boolean overwrite = "OVERWRITE".equals(revision.getPublishMode());
        if (revision.getPublishMode() != null && !overwrite && !"NEW".equals(revision.getPublishMode()))
            throw new IllegalArgumentException("未知发布方式");
        RuleVersionBinding binding;
        long previousGeneration = 0;
        if (overwrite) {
            binding = existing.stream().filter(item -> Objects.equals(item.getId(), revision.getTargetVersionId()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("覆盖目标不属于当前规则"));
            previousGeneration = binding.getGeneration();
            if (!Objects.equals(revision.getTargetGeneration(), previousGeneration))
                throw new IllegalStateException("覆盖目标已变化，请重新提交发布审批");
        } else {
            binding = new RuleVersionBinding();
            binding.setDefinitionId(revision.getDefinitionId());
            binding.setVersionNo(existing.stream().mapToInt(RuleVersionBinding::getVersionNo).max().orElse(0) + 1);
            binding.setGeneration(0L); binding.setStatus(1); insertBinding(binding);
        }
        snapshot.setDefinitionId(revision.getDefinitionId());
        snapshot.setVersion(history(revision.getDefinitionId()).stream().mapToInt(RuleDefinitionVersion::getVersion).max().orElse(0) + 1);
        snapshot.setBusinessVersion(binding.getVersionNo());
        snapshot.setVersionBindingId(binding.getId()); snapshot.setBindingGeneration(previousGeneration + 1);
        snapshot.setRevisionId(revision.getId()); snapshot.setArtifactId(revision.getArtifactId());
        insertSnapshot(snapshot);
        binding.setSnapshotId(snapshot.getId()); binding.setGeneration(previousGeneration + 1);
        binding.setStatus(1); binding.setUpdateTime(LocalDateTime.now()); updateBinding(binding, previousGeneration);
        return binding;
    }

    public int latestBusinessVersion(Long definitionId) {
        return bindings(definitionId).stream().mapToInt(RuleVersionBinding::getVersionNo).max().orElse(0);
    }

    public RuleVersionBinding requireBinding(Long definitionId, Long bindingId) {
        RuleVersionBinding binding = bindings(definitionId).stream().filter(item -> Objects.equals(bindingId, item.getId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("指定规则版本不存在"));
        if (!Integer.valueOf(1).equals(binding.getStatus())) throw new IllegalStateException("指定规则版本已下线");
        return binding;
    }

    public RuleDefinitionVersion resolveSnapshot(Long definitionId, Long bindingId) {
        RuleVersionBinding binding = requireBinding(definitionId, bindingId);
        return resolveSnapshot(binding);
    }

    private RuleDefinitionVersion resolveSnapshot(RuleVersionBinding binding) {
        RuleDefinitionVersion snapshot = versionMapper.selectById(binding.getSnapshotId());
        if (snapshot == null || !binding.getDefinitionId().equals(snapshot.getDefinitionId()))
            throw new IllegalStateException("指定版本快照缺失");
        return snapshot;
    }

    /** The caller has already applied project scope and active-rule authorization. */
    public RulePublished resolvePublished(RulePublished latest, Long bindingId) {
        if (latest == null) throw new IllegalArgumentException("规则未发布或不可访问");
        RulePublished result = com.alibaba.fastjson.JSON.parseObject(com.alibaba.fastjson.JSON.toJSONString(latest), RulePublished.class);
        if (bindingId != null) {
            RuleVersionBinding binding = requireBinding(latest.getDefinitionId(), bindingId);
            RuleDefinitionVersion snapshot = resolveSnapshot(binding);
            if (snapshot.getArtifactId() == null || snapshot.getRevisionId() == null)
                throw new IllegalStateException("该历史版本缺少可验证制品，请通过覆盖发布补全后再指定调用");
            result.setVersion(binding.getVersionNo()); result.setVersionBindingId(binding.getId()); result.setBindingGeneration(binding.getGeneration());
            result.setRevisionId(snapshot.getRevisionId()); result.setArtifactId(snapshot.getArtifactId()); result.setArtifactDigest(snapshot.getArtifactDigest());
            result.setModelJson(snapshot.getModelJson()); result.setCompiledScript(snapshot.getCompiledScript()); result.setCompiledType(snapshot.getCompiledType());
            if (snapshot.getCompiledScript() == null || snapshot.getCompiledScript().isBlank()) throw new IllegalStateException("指定版本没有可执行脚本");
        } else {
            bindings(latest.getDefinitionId()).stream().filter(binding -> Objects.equals(binding.getVersionNo(), latest.getVersion()))
                    .findFirst().ifPresent(binding -> {
                        result.setVersionBindingId(binding.getId());
                        // The published row may have been read just before an overwrite committed.
                        // Attribute its immutable script to its own generation, never to the new pointer.
                        result.setBindingGeneration(history(latest.getDefinitionId()).stream()
                                .filter(snapshot -> businessVersion(snapshot) == latest.getVersion()
                                        && Objects.equals(snapshot.getRevisionId(), latest.getRevisionId())
                                        && Objects.equals(snapshot.getCompiledScript(), latest.getCompiledScript()))
                                .map(snapshot -> snapshot.getBindingGeneration() == null ? 1L : snapshot.getBindingGeneration())
                                .max(Long::compareTo).orElse(0L));
                    });
        }
        return result;
    }

    public List<Map<String, Object>> publishedVersions(Long definitionId) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<Long, RuleDefinitionVersion> snapshots = new HashMap<>();
        history(definitionId).forEach(value -> snapshots.put(value.getId(), value));
        for (RuleVersionBinding binding : bindings(definitionId)) {
            RuleDefinitionVersion snapshot = snapshots.get(binding.getSnapshotId());
            if (snapshot == null || !Integer.valueOf(1).equals(binding.getStatus())) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", snapshot.getId()); row.put("version", binding.getVersionNo());
            row.put("bindingId", binding.getId()); row.put("generation", binding.getGeneration());
            row.put("revisionId", snapshot.getRevisionId()); row.put("publishTime", snapshot.getPublishTime());
            boolean available = snapshot.getArtifactId() != null && snapshot.getRevisionId() != null;
            row.put("available", available);
            if (available && fieldSnapshotResolver != null) {
                RulePublished published = new RulePublished();
                published.setDefinitionId(definitionId); published.setRevisionId(snapshot.getRevisionId());
                published.setArtifactId(snapshot.getArtifactId()); published.setArtifactDigest(snapshot.getArtifactDigest());
                var fields = fieldSnapshotResolver.resolve(published);
                row.put("outputFields", fields.getOutputFields());
                if (fields.getDiagnostics().stream().anyMatch(issue -> "ERROR".equals(issue.getSeverity()))) row.put("available", false);
            }
            result.add(row);
        }
        result.sort((a, b) -> Integer.compare((Integer) b.get("version"), (Integer) a.get("version")));
        return result;
    }

    protected List<RuleVersionBinding> bindings(Long definitionId) {
        return bindingMapper.selectList(new LambdaQueryWrapper<RuleVersionBinding>().eq(RuleVersionBinding::getDefinitionId, definitionId));
    }
    protected List<RuleDefinitionVersion> history(Long definitionId) {
        return versionMapper.selectList(new LambdaQueryWrapper<RuleDefinitionVersion>().eq(RuleDefinitionVersion::getDefinitionId, definitionId));
    }
    protected void insertBinding(RuleVersionBinding binding) {
        if (bindingMapper.insert(binding) != 1) throw new IllegalStateException("写入规则版本绑定失败");
    }
    protected void updateBinding(RuleVersionBinding binding, long previousGeneration) {
        if (bindingMapper.update(null, new LambdaUpdateWrapper<RuleVersionBinding>()
                .eq(RuleVersionBinding::getId, binding.getId()).eq(RuleVersionBinding::getGeneration, previousGeneration)
                .set(RuleVersionBinding::getGeneration, binding.getGeneration()).set(RuleVersionBinding::getSnapshotId, binding.getSnapshotId())
                .set(RuleVersionBinding::getStatus, binding.getStatus()).set(RuleVersionBinding::getUpdateTime, binding.getUpdateTime())) != 1)
            throw new IllegalStateException("规则版本已被其他发布修改");
    }
    protected void insertSnapshot(RuleDefinitionVersion snapshot) {
        if (versionMapper.insert(snapshot) != 1) throw new IllegalStateException("写入发布快照失败");
    }
}
