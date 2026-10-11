package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.*;
import com.hengshucredit.rule.server.service.ResourceVersionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;

/** Deleting one business line affects fixed references to that line, not latest-only consumers. */
@Service
public class ResourceVersionImpactService {
    @Resource private ResourceVersionConsumerMapper consumers;
    @Resource private RuleVersionBindingMapper ruleBindings;
    @Resource private RuleModelVersionMapper modelVersions;
    @Resource private RuleFunctionVersionMapper functionVersions;
    @Resource private ResourceVersionService resourceVersions;

    public List<GovernanceIssue> analyze(GovernedResourceVersion head, List<GovernedResourceVersion> history) {
        List<GovernedResourceVersion> line = history.stream().filter(row -> head.getResourceType().equals(row.getResourceType())
                && Objects.equals(head.getResourceId(), row.getResourceId()) && Objects.equals(head.getDisplayVersion(), row.getDisplayVersion())).toList();
        Set<Long> revisions = new HashSet<>(), nativeIds = nativeIds(head, line), fields = new HashSet<>();
        for (var revision : line) {
            revisions.add(revision.getId());
            JSONObject value = JSON.parseObject(revision.getSnapshotJson());
            String collection = "MODEL".equals(head.getResourceType()) ? "outputFields" : "fields";
            if (value.getJSONArray(collection) != null) for (var field : value.getJSONArray(collection)) {
                Long id = ((JSONObject) JSON.toJSON(field)).getLong("id"); if (id != null) fields.add(id);
            }
        }
        List<GovernanceIssue> issues = new ArrayList<>();
        inspect(consumers.currentSnapshots(), head, revisions, nativeIds, fields, issues);
        inspect(consumers.governedSnapshots(), head, revisions, nativeIds, fields, issues);
        inspect(consumers.nativeSnapshots(), head, revisions, nativeIds, fields, issues);
        return issues;
    }

    private void inspect(List<Map<String, Object>> snapshots, GovernedResourceVersion head, Set<Long> revisions,
                         Set<Long> nativeIds, Set<Long> fields, List<GovernanceIssue> issues) {
        for (var source : snapshots) {
            if (issues.size() >= 5) return;
            String json = (String) source.get("snapshotJson");
            if (json == null || json.isBlank()) continue;
            String type = (String) source.get("resourceType"); Long ownerId = number(source.get("resourceId"));
            Integer business = source.get("businessVersion") == null ? null : number(source.get("businessVersion")).intValue();
            Long nativeId = number(source.get("nativeId"));
            if (head.getResourceType().equals(type) && Objects.equals(head.getResourceId(), ownerId)
                    && (Objects.equals(head.getDisplayVersion(), business) || !"RULE".equals(type) && nativeId != null && nativeIds.contains(nativeId))) continue;
            if (nativeId != null && Set.of("MODEL", "FUNCTION").contains(type)) {
                try { resourceVersions.assertNativeVersionAvailable(type, ownerId, business, nativeId); }
                catch (IllegalArgumentException deleted) { continue; }
                if ("FUNCTION".equals(type) && !Integer.valueOf(1).equals(JSON.parseObject(json).getInteger("status"))) continue;
            }
            for (var dependency : SimpleEntityGovernedResourceAdapter.dependenciesIn(json)) {
                DependencyVersionSelection selected = dependency.versionSelection();
                if (selected == null || !matches(selected, head, revisions, nativeIds, fields)) continue;
                issues.add(GovernanceIssue.error("VERSION_DEPENDENCY_ACTIVE", "业务 v" + head.getDisplayVersion()
                        + " 仍被 " + type + " #" + ownerId + "（" + source.get("sourceLabel") + "）引用，请先调整该版本依赖",
                        head.getResourceType(), head.getResourceId(), dependency.referencePath()));
                break;
            }
        }
    }

    private boolean matches(DependencyVersionSelection selected, GovernedResourceVersion head,
                            Set<Long> revisions, Set<Long> nativeIds, Set<Long> fields) {
        String type = switch (selected.refType()) { case "CONSTANT" -> "VARIABLE"; case "MODEL_OUTPUT" -> "MODEL"; default -> selected.refType(); };
        if (!head.getResourceType().equals(type)) return false;
        boolean field = "MODEL_OUTPUT".equals(selected.refType()) || "DATA_OBJECT".equals(selected.refType());
        if (field ? !fields.contains(selected.refId()) : !Objects.equals(head.getResourceId(), selected.refId())) return false;
        boolean nativeVersion = "RULE_BINDING".equals(selected.versionSource()) || "FUNCTION_HISTORY".equals(selected.versionSource())
                || "MODEL".equals(type) && !"BUSINESS_VERSION".equals(selected.versionSource());
        return (nativeVersion ? nativeIds : revisions).contains(selected.versionId());
    }

    private Set<Long> nativeIds(GovernedResourceVersion head, List<GovernedResourceVersion> line) {
        Set<Long> ids = new HashSet<>();
        if ("RULE".equals(head.getResourceType())) {
            ruleBindings.selectList(new LambdaQueryWrapper<RuleVersionBinding>().eq(RuleVersionBinding::getDefinitionId, head.getResourceId())
                    .eq(RuleVersionBinding::getVersionNo, head.getDisplayVersion())).stream()
                    .filter(binding -> Objects.equals(head.getResourceId(), binding.getDefinitionId()) && Objects.equals(head.getDisplayVersion(), binding.getVersionNo()))
                    .forEach(binding -> ids.add(binding.getId()));
        } else if ("MODEL".equals(head.getResourceType())) {
            Set<Integer> fileVersions = new HashSet<>();
            for (var revision : line) {
                JSONObject value = JSON.parseObject(revision.getSnapshotJson());
                if (value.getLong("_modelContentVersionId") != null) ids.add(value.getLong("_modelContentVersionId"));
                if (value.getInteger("publishedVersion") != null) fileVersions.add(value.getInteger("publishedVersion"));
            }
            modelVersions.selectList(new LambdaQueryWrapper<RuleModelVersion>()
                    .select(RuleModelVersion::getId, RuleModelVersion::getModelId, RuleModelVersion::getVersion, RuleModelVersion::getBusinessVersion)
                    .eq(RuleModelVersion::getModelId, head.getResourceId())).stream()
                    .filter(version -> Objects.equals(head.getResourceId(), version.getModelId()))
                    .filter(version -> Objects.equals(head.getDisplayVersion(), version.getBusinessVersion()) || fileVersions.contains(version.getVersion()))
                    .forEach(version -> ids.add(version.getId()));
        } else if ("FUNCTION".equals(head.getResourceType())) {
            functionVersions.selectList(new LambdaQueryWrapper<RuleFunctionVersion>()
                    .select(RuleFunctionVersion::getId, RuleFunctionVersion::getFunctionId, RuleFunctionVersion::getBusinessVersion)
                    .eq(RuleFunctionVersion::getFunctionId, head.getResourceId())).stream()
                    .filter(version -> Objects.equals(head.getResourceId(), version.getFunctionId()) && Objects.equals(head.getDisplayVersion(), version.getBusinessVersion()))
                    .forEach(version -> ids.add(version.getId()));
        }
        for (var revision : line) if (!"RULE".equals(head.getResourceType()) && (head.getResourceType() + "_VERSION").equals(revision.getLegacySourceType()) && revision.getLegacySourceId() != null)
            ids.add(revision.getLegacySourceId());
        return ids;
    }

    private static Long number(Object value) { return value == null ? null : Long.valueOf(String.valueOf(value)); }
}
