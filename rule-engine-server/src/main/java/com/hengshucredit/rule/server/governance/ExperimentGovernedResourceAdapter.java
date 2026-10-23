package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleExperiment;
import com.hengshucredit.rule.model.entity.RuleExperimentGroup;
import com.hengshucredit.rule.server.mapper.RuleExperimentGroupMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExperimentGovernedResourceAdapter
        extends AggregateEntityGovernedResourceAdapter<RuleExperiment> {

    private final RuleExperimentGroupMapper groupMapper;
    @jakarta.annotation.Resource @org.springframework.context.annotation.Lazy
    private com.hengshucredit.rule.server.service.RuleExperimentService experimentService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.hengshucredit.rule.server.service.EngineFieldValidationService fieldValidationService;

    @Override
    protected AppliedResource afterAggregateApplied(ApprovalApplyContext context, AppliedResource applied) {
        if (experimentService == null) return applied;
        int number = experimentService.recordAppliedVersion(applied.resourceId(), context.reuseVersion() ? context.nextVersionNo() : null,
                "审批 " + context.requestId(), context.actor());
        return new AppliedResource(applied.resourceId(), number, applied.effectiveStatus(), applied.artifactId());
    }

    public ExperimentGovernedResourceAdapter(
            SimpleEntityGovernedResourceAdapter.EntityStore<RuleExperiment>
                    store,
            RuleExperimentGroupMapper groupMapper,
            GovernanceSecretCodec secretCodec) {
        super(new SimpleEntityGovernedResourceAdapter<>(
                GovernanceResourceTypes.EXPERIMENT,
                RuleExperiment.class,
                store,
                RuleExperiment::getId,
                RuleExperiment::setId,
                RuleExperiment::getStatus,
                RuleExperiment::setStatus,
                Set.of("experimentCode", "experimentName",
                        "routingMode"),
                Set.of(),
                secretCodec));
        this.groupMapper = groupMapper;
    }

    @Override
    protected void enrichSnapshot(Long resourceId,
                                  Map<String, Object> snapshot) {
        snapshot.put("groups", groupMapper.selectList(
                new LambdaQueryWrapper<RuleExperimentGroup>()
                        .eq(RuleExperimentGroup::getExperimentId,
                                resourceId)
                        .orderByAsc(RuleExperimentGroup::getSortOrder)
                        .orderByAsc(RuleExperimentGroup::getId)));
    }

    @Override
    protected boolean isOwnershipReference(
            ResourceDependencyRef dependency) {
        return isCollectionOwnershipReference(
                dependency, "groups", "experimentId");
    }

    @Override
    protected void applyAggregate(Long resourceId,
                                  Map<String, Object> snapshot) {
        applyGroups(resourceId, snapshot, false);
    }

    @Override
    protected AppliedResource applyAggregateWithResult(ApprovalApplyContext context, AppliedResource applied, Map<String, Object> snapshot) {
        applyGroups(applied.resourceId(), snapshot, context.reuseVersion());
        return applied;
    }

    private void applyGroups(Long resourceId, Map<String, Object> snapshot, boolean reuse) {
        groupMapper.delete(new LambdaQueryWrapper<RuleExperimentGroup>()
                .eq(RuleExperimentGroup::getExperimentId, resourceId));
        List<RuleExperimentGroup> groups = groups(snapshot);
        for (int index = 0; index < groups.size(); index++) {
            RuleExperimentGroup group = groups.get(index);
            if (reuse && group.getId() != null && groupMapper.selectById(group.getId()) != null)
                throw new IllegalArgumentException("历史分组 ID 已被其他实验占用，不能复用原版本");
            if (!reuse) group.setId(null);
            group.setExperimentId(resourceId);
            group.setSortOrder(index);
            groupMapper.insert(group);
        }
    }

    @Override
    protected void validateAggregate(Map<String, Object> snapshot,
                                     List<GovernanceIssue> issues) {
        if (fieldValidationService != null) {
            try {
                String normalized = fieldValidationService
                        .normalizeExperimentConfiguration(
                                longValue(snapshot.get("projectId")),
                                snapshot.get("inputValidationConfig") == null
                                        ? null
                                        : String.valueOf(snapshot.get("inputValidationConfig")));
                snapshot.put("inputValidationConfig", normalized);
            } catch (IllegalArgumentException error) {
                issues.add(GovernanceIssue.error(
                        "EXPERIMENT_INPUT_VALIDATION_INVALID",
                        error.getMessage(), GovernanceResourceTypes.EXPERIMENT,
                        longValue(snapshot.get("id")),
                        "$.inputValidationConfig"));
            }
        }
        List<RuleExperimentGroup> groups = groups(snapshot);
        for (int index = 0; index < groups.size(); index++) {
            RuleExperimentGroup group = groups.get(index);
            if (group.getRuleId() == null || group.getRuleId() <= 0) {
                issues.add(GovernanceIssue.error("EXPERIMENT_RULE_REQUIRED",
                        "实验组尚未选择执行规则，请选择已发布的规则后再发布实验",
                        GovernanceResourceTypes.EXPERIMENT, longValue(snapshot.get("id")),
                        "$.groups[" + index + "].ruleId"));
            }
            if (experimentService != null) {
                try { experimentService.validateGroupVersion(group); }
                catch (IllegalArgumentException | IllegalStateException error) { issues.add(GovernanceIssue.error("EXPERIMENT_RULE_VERSION_INVALID", error.getMessage(),
                        GovernanceResourceTypes.EXPERIMENT, longValue(snapshot.get("id")), "$.groups[" + index + "].versionBindingId")); }
            }
        }
        try {
            experimentService.validateExperiment(JSON.parseObject(JSON.toJSONString(snapshot), RuleExperiment.class));
        } catch (IllegalArgumentException invalid) {
            issues.add(GovernanceIssue.error("EXPERIMENT_ROUTING_INVALID", invalid.getMessage(),
                    GovernanceResourceTypes.EXPERIMENT, longValue(snapshot.get("id")), "$.groups"));
        }
    }

    private List<RuleExperimentGroup> groups(
            Map<String, Object> snapshot) {
        List<RuleExperimentGroup> groups = JSON.parseArray(
                JSON.toJSONString(snapshot.get("groups")),
                RuleExperimentGroup.class);
        return groups == null ? List.of() : groups;
    }

    private Long longValue(Object value) {
        return value instanceof Number number
                ? number.longValue() : null;
    }
}
