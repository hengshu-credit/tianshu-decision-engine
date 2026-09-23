package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.server.mapper.RuleModelInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.service.RuleModelService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModelGovernedResourceAdapter
        extends AggregateEntityGovernedResourceAdapter<RuleModel> {

    private final RuleModelInputFieldMapper inputMapper;
    private final RuleModelOutputFieldMapper outputMapper;
    @Resource private RuleDataObjectFieldMapper dataObjectFieldMapper;
    @Resource @Lazy private RuleModelService modelService;

    @Override
    protected AppliedResource afterAggregateApplied(ApprovalApplyContext context, AppliedResource applied) {
        // Enabling a model is its publish operation, not just a status change.
        // Keep upload/binding edits separate: their I/O configuration may still be incomplete.
        if ("ENABLE".equals(context.action())) {
            modelService.publish(applied.resourceId(), "审批发布 #" + context.requestId(), context.actor());
        }
        return applied;
    }

    @Override public List<ResourceDependencyRef> collectDependencies(ResourceSnapshot draft) {
        return DataObjectGovernanceReferences.owners(super.collectDependencies(draft), dataObjectFieldMapper);
    }

    public ModelGovernedResourceAdapter(
            SimpleEntityGovernedResourceAdapter.EntityStore<RuleModel>
                    store,
            RuleModelInputFieldMapper inputMapper,
            RuleModelOutputFieldMapper outputMapper,
            GovernanceSecretCodec secretCodec) {
        super(new SimpleEntityGovernedResourceAdapter<>(
                GovernanceResourceTypes.MODEL,
                RuleModel.class,
                store,
                RuleModel::getId,
                RuleModel::setId,
                RuleModel::getStatus,
                RuleModel::setStatus,
                Set.of("modelCode", "modelName", "modelType",
                        "modelFormat"),
                Set.of("modelContent"),
                secretCodec));
        this.inputMapper = inputMapper;
        this.outputMapper = outputMapper;
    }

    @Override
    protected void enrichSnapshot(Long resourceId,
                                  Map<String, Object> snapshot) {
        snapshot.put("inputFields", inputMapper.selectList(
                new LambdaQueryWrapper<RuleModelInputField>()
                        .eq(RuleModelInputField::getModelId, resourceId)
                        .orderByAsc(RuleModelInputField::getSortOrder)
                        .orderByAsc(RuleModelInputField::getId)));
        snapshot.put("outputFields", outputMapper.selectList(
                new LambdaQueryWrapper<RuleModelOutputField>()
                        .eq(RuleModelOutputField::getModelId, resourceId)
                        .orderByAsc(RuleModelOutputField::getSortOrder)
                        .orderByAsc(RuleModelOutputField::getId)));
    }

    @Override
    protected boolean isOwnershipReference(
            ResourceDependencyRef dependency) {
        return isCollectionOwnershipReference(
                dependency, "inputFields", "modelId")
                || isCollectionOwnershipReference(
                dependency, "outputFields", "modelId");
    }

    @Override
    protected void applyAggregate(Long resourceId,
                                  Map<String, Object> snapshot) {
        List<RuleModelInputField> inputs = JSON.parseArray(
                JSON.toJSONString(snapshot.get("inputFields")),
                RuleModelInputField.class);
        if (inputs != null) {
            var existing = inputMapper.selectList(new LambdaQueryWrapper<RuleModelInputField>()
                    .eq(RuleModelInputField::getModelId, resourceId));
            Set<Long> retained = new java.util.HashSet<>();
            for (int index = 0; index < inputs.size(); index++) {
                RuleModelInputField input = inputs.get(index);
                input.setModelId(resourceId);
                input.setSortOrder(index);
                if (input.getId() != null && existing.stream().anyMatch(field -> field.getId().equals(input.getId()))) {
                    retained.add(input.getId());
                    inputMapper.updateById(input);
                } else {
                    if (input.getId() != null && inputMapper.selectById(input.getId()) != null) throw new IllegalArgumentException("模型输入字段不属于当前模型");
                    input.setId(null);
                    inputMapper.insert(input);
                }
            }
            List<Long> removed = existing.stream().map(RuleModelInputField::getId).filter(id -> !retained.contains(id)).toList();
            if (!removed.isEmpty()) inputMapper.delete(new LambdaQueryWrapper<RuleModelInputField>().eq(RuleModelInputField::getModelId, resourceId).in(RuleModelInputField::getId, removed));
        }
        List<RuleModelOutputField> outputs = JSON.parseArray(
                JSON.toJSONString(snapshot.get("outputFields")),
                RuleModelOutputField.class);
        if (outputs != null) {
            var existing = outputMapper.selectList(new LambdaQueryWrapper<RuleModelOutputField>()
                    .eq(RuleModelOutputField::getModelId, resourceId));
            Set<Long> retained = new java.util.HashSet<>();
            for (int index = 0; index < outputs.size(); index++) {
                RuleModelOutputField output = outputs.get(index);
                output.setModelId(resourceId);
                output.setSortOrder(index);
                if (output.getId() != null && existing.stream().anyMatch(field -> field.getId().equals(output.getId()))) {
                    retained.add(output.getId());
                    outputMapper.updateById(output);
                } else {
                    if (output.getId() != null && outputMapper.selectById(output.getId()) != null) throw new IllegalArgumentException("模型输出字段不属于当前模型");
                    output.setId(null);
                    outputMapper.insert(output);
                }
            }
            List<Long> removed = existing.stream().map(RuleModelOutputField::getId).filter(id -> !retained.contains(id)).toList();
            if (!removed.isEmpty()) outputMapper.delete(new LambdaQueryWrapper<RuleModelOutputField>().eq(RuleModelOutputField::getModelId, resourceId).in(RuleModelOutputField::getId, removed));
        }
    }
}
