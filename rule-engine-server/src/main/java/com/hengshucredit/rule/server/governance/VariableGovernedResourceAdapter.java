package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.entity.RuleVariableOption;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableOptionMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;

import jakarta.annotation.Resource;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class VariableGovernedResourceAdapter
        extends AggregateEntityGovernedResourceAdapter<RuleVariable> {

    private final RuleVariableOptionMapper optionMapper;
    private final VariableSourceReferenceValidator sourceValidator;

    @Resource
    private RuleDataObjectFieldMapper dataObjectFieldMapper;

    public VariableGovernedResourceAdapter(
            SimpleEntityGovernedResourceAdapter.EntityStore<RuleVariable>
                    store,
            RuleVariableOptionMapper optionMapper,
            GovernanceSecretCodec secretCodec) {
        this(store, optionMapper, secretCodec, null);
    }

    public VariableGovernedResourceAdapter(
            SimpleEntityGovernedResourceAdapter.EntityStore<RuleVariable>
                    store,
            RuleVariableOptionMapper optionMapper,
            GovernanceSecretCodec secretCodec,
            VariableSourceReferenceValidator sourceValidator) {
        super(new SimpleEntityGovernedResourceAdapter<>(
                GovernanceResourceTypes.VARIABLE,
                RuleVariable.class,
                store,
                RuleVariable::getId,
                RuleVariable::setId,
                RuleVariable::getStatus,
                RuleVariable::setStatus,
                Set.of("varCode", "varLabel", "varType"),
                Set.of(),
                secretCodec));
        this.optionMapper = optionMapper;
        this.sourceValidator = sourceValidator;
    }

    @Override
    public List<ResourceDependencyRef> collectDependencies(ResourceSnapshot draft) {
        return DataObjectGovernanceReferences.owners(super.collectDependencies(draft), dataObjectFieldMapper);
    }

    @Override
    protected void enrichSnapshot(Long resourceId,
                                  Map<String, Object> snapshot) {
        snapshot.put("options", optionMapper.selectList(
                new LambdaQueryWrapper<RuleVariableOption>()
                        .eq(RuleVariableOption::getVariableId,
                                resourceId)
                        .orderByAsc(RuleVariableOption::getSortOrder)
                        .orderByAsc(RuleVariableOption::getId)));
    }

    @Override
    protected boolean isOwnershipReference(
            ResourceDependencyRef dependency) {
        return isCollectionOwnershipReference(
                dependency, "options", "variableId");
    }

    @Override
    public List<GovernanceIssue> validate(ResourceSnapshot draft,
                                          String action) {
        List<GovernanceIssue> issues = super.validate(draft);
        if (!"DISABLE".equalsIgnoreCase(action)
                && !"DELETE".equalsIgnoreCase(action)) {
            return issues;
        }
        return issues.stream()
                .filter(issue -> !isSourceValidationIssue(issue))
                .toList();
    }

    @Override
    protected void validateAggregate(Map<String, Object> snapshot,
                                     List<GovernanceIssue> issues) {
        if (sourceValidator == null) {
            return;
        }
        RuleVariable variable = JSON.parseObject(
                JSON.toJSONString(snapshot), RuleVariable.class);
        issues.addAll(sourceValidator.validate(variable));
    }

    private boolean isSourceValidationIssue(GovernanceIssue issue) {
        return issue != null && issue.code() != null
                && (issue.code().startsWith("VARIABLE_SOURCE_")
                || "VARIABLE_SCOPE_INVALID".equals(issue.code()));
    }

    @Override
    protected void applyAggregate(Long resourceId,
                                  Map<String, Object> snapshot) {
        optionMapper.delete(new LambdaQueryWrapper<RuleVariableOption>()
                .eq(RuleVariableOption::getVariableId, resourceId));
        List<RuleVariableOption> options = JSON.parseArray(
                JSON.toJSONString(snapshot.get("options")),
                RuleVariableOption.class);
        if (options == null) {
            return;
        }
        for (int index = 0; index < options.size(); index++) {
            RuleVariableOption option = options.get(index);
            option.setId(null);
            option.setVariableId(resourceId);
            option.setSortOrder(index);
            optionMapper.insert(option);
        }
    }
}
