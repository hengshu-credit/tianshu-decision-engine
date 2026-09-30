package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionVersion;
import com.hengshucredit.rule.model.entity.RuleExperimentGroup;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.entity.RuleVersionBinding;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionVersionMapper;
import com.hengshucredit.rule.server.mapper.RuleExperimentGroupMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleFunctionMapper;
import com.hengshucredit.rule.server.mapper.RuleModelInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.hengshucredit.rule.server.mapper.RuleVersionBindingMapper;
import com.hengshucredit.rule.server.service.OperandDependencyCollector;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 数据对象覆盖前，保护仍由目标环境配置引用的字段身份。 */
@Service
public class DataObjectUpdateReferenceGuard {
    @Resource private RuleDataObjectFieldMapper fieldMapper;
    @Resource private RuleDataObjectMapper objectMapper;
    @Resource private RuleDefinitionContentMapper contentMapper;
    @Resource private RuleDefinitionInputFieldMapper ruleInputMapper;
    @Resource private RuleDefinitionOutputFieldMapper ruleOutputMapper;
    @Resource private RuleRevisionMapper revisionMapper;
    @Resource private RuleVersionBindingMapper bindingMapper;
    @Resource private RuleDefinitionVersionMapper versionMapper;
    @Resource private RuleModelInputFieldMapper modelInputMapper;
    @Resource private RuleModelOutputFieldMapper modelOutputMapper;
    @Resource private RuleFunctionMapper functionMapper;
    @Resource private RuleVariableMapper variableMapper;
    @Resource private RuleExternalApiConfigMapper apiMapper;
    @Resource private RuleExternalDatasourceMapper datasourceMapper;
    @Resource private RuleExperimentGroupMapper groupMapper;

    private static final Set<String> JSON_CONFIG_FIELDS = Set.of("modelJson", "openApiConfigJson", "paramsJson", "sourceConfig",
            "sourceContent", "sourceOperand", "defaultOperand", "targetOperand", "transformOperand",
            "conditionConfig", "executionConfig", "headerConfig", "queryConfig", "requestMapping",
            "responseMapping", "authApiConfig", "authConfig", "cacheKeyConfig", "asyncPollConfig", "asyncCallbackConfig");
    private static final Set<String> OBJECT_REFERENCES = Set.of("refObjectId", "requestObjectId", "responseObjectId", "parentObjectId");

    public List<GovernanceIssue> validate(Long objectId, Map<String, Object> incoming) {
        if (objectId == null || objectId <= 0) return List.of();
        Map<Long, Map<?, ?>> next = new HashMap<>();
        if (incoming.get("fields") instanceof List<?> values) {
            for (Object value : values) if (value instanceof Map<?, ?> field && id(field.get("id")) != null) {
                next.put(id(field.get("id")), field);
            }
        }
        Map<Long, RuleDataObjectField> previous = new HashMap<>();
        Set<Long> affected = new LinkedHashSet<>();
        for (RuleDataObjectField field : fields(objectId)) {
            previous.put(field.getId(), field);
            Map<?, ?> replacement = next.get(field.getId());
            if (replacement == null || !Objects.equals(field.getVarType(), replacement.get("varType"))
                    || !Objects.equals(field.getGenericType(), replacement.get("genericType"))) affected.add(field.getId());
        }
        if (affected.isEmpty()) return List.of();
        // 引用整个父对象/列表的消费者同样依赖其子结构，不能仅检查被删叶子。
        Set<Long> affectedWithParents = new HashSet<>(affected);
        for (Long fieldId : affected) {
            RuleDataObjectField field = previous.get(fieldId);
            while (field != null && field.getParentFieldId() != null && affectedWithParents.add(field.getParentFieldId())) {
                field = previous.get(field.getParentFieldId());
            }
        }
        List<GovernanceIssue> issues = new ArrayList<>();
        for (Consumer consumer : consumers(objectId, incoming)) {
            Object config = expand(JSON.toJSON(consumer.configuration()));
            boolean referenced = referencesObject(config, objectId) || OperandDependencyCollector.collectReferences(config).stream()
                    .anyMatch(ref -> Set.of("DATA_OBJECT", "DATA_FIELD", "OBJECT").contains(String.valueOf(ref.getRefType()).toUpperCase(Locale.ROOT))
                            && affectedWithParents.contains(ref.getRefId()));
            if (referenced) issues.add(GovernanceIssue.error("DATA_OBJECT_FIELD_IN_USE",
                    "不能删除或改变仍被 " + consumer.type() + ":" + consumer.id()
                            + " 引用的数据对象字段类型；请先调整引用，或选择新建并追加后缀",
                    GovernanceResourceTypes.DATA_OBJECT, objectId, "$.fields"));
        }
        return issues;
    }

    protected List<RuleDataObjectField> fields(Long objectId) {
        return fieldMapper.selectList(new LambdaQueryWrapper<RuleDataObjectField>()
                .select(RuleDataObjectField::getId, RuleDataObjectField::getParentFieldId,
                        RuleDataObjectField::getVarType, RuleDataObjectField::getGenericType)
                .eq(RuleDataObjectField::getObjectId, objectId));
    }

    /** 仅在破坏性字段更新时读取配置引用列；不读取模型二进制、历史制品或执行日志。 */
    protected List<Consumer> consumers(Long objectId, Map<String, Object> incoming) {
        List<Consumer> result = new ArrayList<>();
        ruleInputMapper.selectList(new LambdaQueryWrapper<RuleDefinitionInputField>().select(
                RuleDefinitionInputField::getDefinitionId, RuleDefinitionInputField::getVarId, RuleDefinitionInputField::getRefType))
                .forEach(row -> result.add(new Consumer("规则输入", row.getDefinitionId(), row)));
        ruleOutputMapper.selectList(new LambdaQueryWrapper<RuleDefinitionOutputField>().select(
                RuleDefinitionOutputField::getDefinitionId, RuleDefinitionOutputField::getVarId, RuleDefinitionOutputField::getRefType))
                .forEach(row -> result.add(new Consumer("规则输出", row.getDefinitionId(), row)));
        contentMapper.selectList(new LambdaQueryWrapper<RuleDefinitionContent>().select(
                RuleDefinitionContent::getDefinitionId, RuleDefinitionContent::getModelJson))
                .forEach(row -> result.add(new Consumer("规则配置", row.getDefinitionId(), row)));
        revisionMapper.selectList(new LambdaQueryWrapper<RuleRevision>().select(RuleRevision::getDefinitionId, RuleRevision::getModelJson)
                .in(RuleRevision::getState, "DRAFT", "REVIEW", "APPROVED"))
                .forEach(row -> result.add(new Consumer("规则修订", row.getDefinitionId(), row)));
        result.addAll(activeVersionConsumers());
        modelInputMapper.selectList(new LambdaQueryWrapper<RuleModelInputField>().select(RuleModelInputField::getModelId,
                RuleModelInputField::getVarId, RuleModelInputField::getRefType, RuleModelInputField::getSourceOperand,
                RuleModelInputField::getDefaultOperand))
                .forEach(row -> result.add(new Consumer("模型输入", row.getModelId(), row)));
        modelOutputMapper.selectList(new LambdaQueryWrapper<RuleModelOutputField>().select(RuleModelOutputField::getModelId,
                RuleModelOutputField::getVarId, RuleModelOutputField::getRefType, RuleModelOutputField::getTargetOperand,
                RuleModelOutputField::getTransformOperand))
                .forEach(row -> result.add(new Consumer("模型输出", row.getModelId(), row)));
        functionMapper.selectList(new LambdaQueryWrapper<RuleFunction>().select(RuleFunction::getId, RuleFunction::getParamsJson)
                .ne(RuleFunction::getStatus, -1)).forEach(row -> result.add(new Consumer("函数", row.getId(), row)));
        variableMapper.selectList(new LambdaQueryWrapper<RuleVariable>().select(RuleVariable::getId, RuleVariable::getSourceConfig)
                .ne(RuleVariable::getStatus, -1)).forEach(row -> result.add(new Consumer("变量", row.getId(), row)));
        apiMapper.selectList(new LambdaQueryWrapper<RuleExternalApiConfig>().select(RuleExternalApiConfig::getId,
                RuleExternalApiConfig::getRequestObjectId, RuleExternalApiConfig::getResponseObjectId,
                RuleExternalApiConfig::getExecutionConfig, RuleExternalApiConfig::getHeaderConfig, RuleExternalApiConfig::getQueryConfig,
                RuleExternalApiConfig::getRequestMapping, RuleExternalApiConfig::getResponseMapping, RuleExternalApiConfig::getAuthApiConfig,
                RuleExternalApiConfig::getCacheKeyConfig, RuleExternalApiConfig::getAsyncPollConfig, RuleExternalApiConfig::getAsyncCallbackConfig)
                .ne(RuleExternalApiConfig::getStatus, -1)).forEach(row -> result.add(new Consumer("外数 API", row.getId(), row)));
        datasourceMapper.selectList(new LambdaQueryWrapper<RuleExternalDatasource>()
                .select(RuleExternalDatasource::getId, RuleExternalDatasource::getAuthConfig).ne(RuleExternalDatasource::getStatus, -1))
                .forEach(row -> result.add(new Consumer("外数鉴权", row.getId(), row)));
        groupMapper.selectList(new LambdaQueryWrapper<RuleExperimentGroup>()
                .select(RuleExperimentGroup::getExperimentId, RuleExperimentGroup::getConditionConfig).ne(RuleExperimentGroup::getStatus, -1))
                .forEach(row -> result.add(new Consumer("分流实验", row.getExperimentId(), row)));
        objectMapper.selectList(new LambdaQueryWrapper<RuleDataObject>().select(RuleDataObject::getId,
                RuleDataObject::getParentObjectId, RuleDataObject::getSourceType, RuleDataObject::getSourceContent)
                .ne(RuleDataObject::getId, objectId).ne(RuleDataObject::getStatus, -1))
                .forEach(row -> result.add(new Consumer("数据对象", row.getId(), row)));
        fieldMapper.selectList(new LambdaQueryWrapper<RuleDataObjectField>().select(RuleDataObjectField::getObjectId,
                RuleDataObjectField::getRefObjectId, RuleDataObjectField::getSourceConfig).ne(RuleDataObjectField::getObjectId, objectId))
                .forEach(row -> result.add(new Consumer("数据对象字段", row.getObjectId(), row)));
        // 当前对象使用新快照校验，已删除的旧内部字段不是外部消费者。
        result.add(new Consumer("当前数据对象", objectId, incoming));
        return result;
    }

    /** 固定业务版本仍可执行时，读取其当前绑定快照，避免最新规则已不再引用而漏掉旧版本。 */
    protected List<Consumer> activeVersionConsumers() {
        Set<Long> snapshots = new LinkedHashSet<>();
        for (RuleVersionBinding binding : bindingMapper.selectList(new LambdaQueryWrapper<RuleVersionBinding>()
                .select(RuleVersionBinding::getSnapshotId, RuleVersionBinding::getStatus).eq(RuleVersionBinding::getStatus, 1))) {
            if (Integer.valueOf(1).equals(binding.getStatus()) && binding.getSnapshotId() != null) snapshots.add(binding.getSnapshotId());
        }
        if (snapshots.isEmpty()) return List.of();
        return versionMapper.selectList(new LambdaQueryWrapper<RuleDefinitionVersion>()
                .select(RuleDefinitionVersion::getId, RuleDefinitionVersion::getDefinitionId,
                        RuleDefinitionVersion::getModelJson, RuleDefinitionVersion::getOpenApiConfigJson)
                .in(RuleDefinitionVersion::getId, snapshots)).stream()
                .map(row -> new Consumer("规则固定版本快照", row.getId(), row)).toList();
    }

    private Object expand(Object value) {
        if (value instanceof JSONObject object) {
            // 字面量可以合法包含业务 varId/refType/objectId，不是引擎关联。
            if ("LITERAL".equals(object.getString("kind"))) return new JSONObject();
            JSONObject expanded = new JSONObject();
            object.forEach((key, child) -> {
                if ("sourceContent".equals(key) && !Set.of("API", "DB", "DATABASE")
                        .contains(String.valueOf(object.get("sourceType")).toUpperCase(Locale.ROOT))) return;
                if (Set.of("script", "example", "examples", "testSampleParams", "defaultValue").contains(key)) return;
                Object nested = child;
                if (JSON_CONFIG_FIELDS.contains(key) && child instanceof String text && !text.isBlank()) {
                    try { nested = JSON.parse(text); }
                    catch (RuntimeException invalidJson) { nested = null; }
                }
                expanded.put(key, expand(nested));
            });
            return expanded;
        }
        if (value instanceof JSONArray values) {
            JSONArray expanded = new JSONArray();
            values.forEach(child -> expanded.add(expand(child)));
            return expanded;
        }
        return value;
    }

    private boolean referencesObject(Object value, Long objectId) {
        if (value instanceof JSONObject object) {
            for (String key : OBJECT_REFERENCES) if (Objects.equals(objectId, id(object.get(key)))) return true;
            // 对象关联字段只认资源根配置及对象字段定义，不扫描来源数据中的同名业务键。
            if (object.get("fields") instanceof JSONArray fields) {
                for (Object field : fields) if (field instanceof JSONObject definition
                        && Objects.equals(objectId, id(definition.get("refObjectId")))) return true;
            }
        }
        return false;
    }

    private Long id(Object value) {
        if (value == null) return null;
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException invalid) { return null; }
    }

    protected record Consumer(String type, Long id, Object configuration) { }
}
