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
    @Resource private com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper governanceVersions;
    @Resource private com.hengshucredit.rule.server.mapper.RuleModelMapper modelMapper;
    @Resource private com.hengshucredit.rule.server.mapper.RuleModelVersionMapper modelVersions;

    @Override public ResourceSnapshot loadEffective(Long resourceId) {
        if (modelMapper == null) return super.loadEffective(resourceId);
        RuleModel model = modelMapper.selectOne(new LambdaQueryWrapper<RuleModel>()
                .select(RuleModel.class, field -> !"modelContent".equals(field.getProperty())).eq(RuleModel::getId, resourceId));
        if (model == null) throw new IllegalArgumentException("模型不存在");
        var version = modelVersions.selectOne(new LambdaQueryWrapper<com.hengshucredit.rule.model.entity.RuleModelVersion>()
                .select(com.hengshucredit.rule.model.entity.RuleModelVersion.class,
                        field -> !Set.of("modelContent", "executionSnapshotJson").contains(field.getProperty()))
                .eq(com.hengshucredit.rule.model.entity.RuleModelVersion::getModelId, resourceId)
                .eq(com.hengshucredit.rule.model.entity.RuleModelVersion::getVersion, model.getCurrentVersion()));
        if (version == null && Integer.valueOf(1).equals(model.getStatus())) throw new IllegalArgumentException("模型缺少不可变文件版本，请先保存模型文件");
        Map<String, Object> snapshot = com.hengshucredit.rule.server.artifact.CanonicalJson.readMap(JSON.toJSONString(model));
        if (version != null) snapshot.put("_modelContentVersionId", version.getId());
        enrichSnapshot(resourceId, snapshot);
        return normalizeDraft(new ResourceSnapshot(com.hengshucredit.rule.server.artifact.CanonicalJson.write(snapshot),
                Integer.valueOf(1).equals(model.getStatus()) ? "ACTIVE" : Integer.valueOf(-1).equals(model.getStatus()) ? "DELETED" : "DISABLED", null, null));
    }

    @Override public AppliedResource apply(ApprovalApplyContext context) {
        var snapshot = JSON.parseObject(context.snapshot().snapshotJson());
        Long fileVersionId = snapshot.getLong("_modelContentVersionId");
        if (fileVersionId != null && !Set.of("DELETE", "DISABLE", "DRAFT_DELETE").contains(context.action())) {
            var version = modelVersions.selectOne(new LambdaQueryWrapper<com.hengshucredit.rule.model.entity.RuleModelVersion>()
                    .select(com.hengshucredit.rule.model.entity.RuleModelVersion::getId, com.hengshucredit.rule.model.entity.RuleModelVersion::getModelId)
                    .eq(com.hengshucredit.rule.model.entity.RuleModelVersion::getId, fileVersionId));
            if (version == null || !java.util.Objects.equals(context.resourceId(), version.getModelId()))
                throw new IllegalArgumentException("模型文件版本不属于当前模型");
            // Copy inside the database instead of duplicating large base64 model files in governance JSON.
            modelMapper.restoreVersionContent(context.resourceId(), fileVersionId);
        }
        return super.apply(context);
    }

    @Override
    public List<GovernanceIssue> validate(ResourceSnapshot draft) { return validate(draft, "UPDATE"); }

    @Override
    public List<GovernanceIssue> validate(ResourceSnapshot draft, String action) {
        var issues = new java.util.ArrayList<>(super.validate(draft));
        if ("DELETE".equals(action) || "DISABLE".equals(action) || modelService == null) return issues;
        try {
            modelService.validateDraftForPublication(JSON.parseObject(draft.snapshotJson(), RuleModel.class));
        } catch (IllegalArgumentException error) {
            issues.add(GovernanceIssue.error("MODEL_PUBLICATION_INVALID", error.getMessage(),
                    GovernanceResourceTypes.MODEL, null, "$.inputFields"));
        }
        return issues;
    }

    @Override
    protected AppliedResource afterAggregateApplied(ApprovalApplyContext context, AppliedResource applied) {
        if (context.reuseVersion()) {
            var source = governanceVersions.selectById(context.sourceVersionId());
            if (source == null || !"MODEL".equals(source.getResourceType()) || !applied.resourceId().equals(source.getResourceId()))
                throw new IllegalArgumentException("模型恢复来源不匹配");
            modelService.publishReusingVersion(applied.resourceId(), context.nextVersionNo(), "审批恢复 #" + context.requestId(), context.actor());
        } else if (Set.of("ENABLE", "CREATE", "UPDATE", "RESTORE").contains(context.action())) {
            modelService.publish(applied.resourceId(), "审批发布 #" + context.requestId(), context.actor());
        }
        Integer number = modelService.publishedBusinessVersion(applied.resourceId());
        return new AppliedResource(applied.resourceId(), number == null ? applied.versionNo() : number, applied.effectiveStatus(), applied.artifactId());
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
        applyFields(resourceId, snapshot, false);
    }

    @Override
    protected AppliedResource applyAggregateWithResult(ApprovalApplyContext context, AppliedResource applied, Map<String, Object> snapshot) {
        applyFields(applied.resourceId(), snapshot, context.reuseVersion());
        return applied;
    }

    private void applyFields(Long resourceId, Map<String, Object> snapshot, boolean reuse) {
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
                    if (!reuse) input.setId(null);
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
                    if (!reuse) output.setId(null);
                    outputMapper.insert(output);
                }
            }
            List<Long> removed = existing.stream().map(RuleModelOutputField::getId).filter(id -> !retained.contains(id)).toList();
            if (!removed.isEmpty()) outputMapper.delete(new LambdaQueryWrapper<RuleModelOutputField>().eq(RuleModelOutputField::getModelId, resourceId).in(RuleModelOutputField::getId, removed));
        }
    }
}
