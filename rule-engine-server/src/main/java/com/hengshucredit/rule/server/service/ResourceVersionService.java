package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.core.compiler.ConstantValueCodec;
import com.hengshucredit.rule.core.engine.RequestContext;
import com.hengshucredit.rule.core.engine.ResourceVersionCall;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.governance.GovernanceResourceBootstrapService;
import com.hengshucredit.rule.server.governance.GovernanceSecretCodec;
import com.hengshucredit.rule.server.governance.ResourceSnapshot;
import com.hengshucredit.rule.server.governance.ResourceVersionLines;
import com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper;
import com.hengshucredit.rule.server.mapper.RuleModelVersionMapper;
import com.hengshucredit.rule.server.mapper.RuleFunctionVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 字段引用的版本身份与资源身份分别校验，历史配置不回退到当前主表。 */
@Service
public class ResourceVersionService {
    @Resource private com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper versionDatasourceMapper;

    public void validateScope(ResourceVersionCall call, Long projectId) {
        JSONObject version = snapshot(call, projectId);
        if ("EXTERNAL_API".equals(call.refType())) {
            var owner = versionDatasourceMapper.selectById(version.getJSONObject("payload").getLong("datasourceId"));
            if (owner == null || !Integer.valueOf(1).equals(owner.getStatus()) || !"GLOBAL".equals(owner.getScope())
                    && (projectId == null || !Objects.equals(projectId, owner.getProjectId())))
                throw new IllegalArgumentException("目标 API 版本不属于导入项目或全局范围");
        }
    }
    private final ThreadLocal<Set<String>> activeVersions = ThreadLocal.withInitial(LinkedHashSet::new);
    @Resource private GovernedResourceVersionMapper versionMapper;
    @Resource private RuleModelVersionMapper modelVersionMapper;
    @Resource private RuleFunctionVersionMapper functionVersionMapper;
    @Resource @Lazy private GovernanceResourceBootstrapService bootstrap;
    @Resource private GovernanceSecretCodec secretCodec;
    @Resource @Lazy private ModelVersionTestService modelVersions;
    @Resource @Lazy private RuleFunctionService functionService;
    @Resource @Lazy private VariableSourceResolver variableResolver;
    @Resource @Lazy private DataObjectSourceResolver objectResolver;
    @Resource @Lazy private ExternalApiConsumerService apiConsumer;

    public RequestContext.ResourceVersionScope bind(Long projectId, Map<String, Object> values, VariableResolveOptions options) {
        VariableResolveOptions executionOptions = options == null ? VariableResolveOptions.defaults() : options;
        return RuntimeContextBridge.currentContext().bindResourceVersions(call -> {
            Set<String> resolving = activeVersions.get();
            if (!resolving.add(call.bindingKey())) throw new IllegalArgumentException("固定版本引用存在循环：" + call.bindingKey());
            try { return resolve(call, projectId, values, executionOptions); }
            finally { resolving.remove(call.bindingKey()); if (resolving.isEmpty()) activeVersions.remove(); }
        });
    }

    public void validate(Object tree, Long projectId) {
        for (var reference : OperandDependencyCollector.collectReferences(tree)) {
            if (reference.getVersionId() != null) snapshot(new ResourceVersionCall(reference.getRefType(), reference.getRefId(),
                    reference.getVersionId(), "", List.of(), null, null, reference.getVersionSource()), projectId);
        }
    }

    public List<RuleDefinitionInputField> inputs(Object tree, Long projectId) {
        Map<String, RuleDefinitionInputField> inputs = new LinkedHashMap<>();
        for (var reference : OperandDependencyCollector.collectReferences(tree)) {
            if (reference.getVersionId() == null) continue;
            var call = new ResourceVersionCall(reference.getRefType(), reference.getRefId(), reference.getVersionId(), "", List.of(), null, null, reference.getVersionSource());
            JSONObject stored = snapshot(call, projectId);
            JSONObject payload = stored.getJSONObject("payload");
            if (call.refType().startsWith("MODEL")) {
                var context = modelVersions.context(payload.toJavaObject(RuleModelVersion.class));
                var schema = (com.hengshucredit.rule.model.dto.RuleTestSchema) context.get("schema");
                for (var field : schema.getInputs()) addInput(inputs, field.getScriptName(), field.getLabel(), field.getValueType());
            } else if ("VARIABLE".equals(call.refType())) {
                RuleVariable variable = payload.toJavaObject(RuleVariable.class);
                if ("INPUT".equals(variable.getVarSource())) addInput(inputs, first(variable.getScriptName(), variable.getVarCode()), variable.getVarLabel(), variable.getVarType());
                else if (!"CONSTANT".equals(variable.getVarSource())) {
                    for (String path : variableResolver.versionDependencies(variable, projectId)) addInput(inputs, path, path, "OBJECT");
                }
            } else if ("DATA_OBJECT_ROOT".equals(call.refType()) && !Set.of("API", "DB", "DATABASE").contains(first(payload.getString("sourceType"), "INPUT"))) {
                addInput(inputs, first(payload.getString("scriptName"), payload.getString("objectCode")), payload.getString("objectLabel"), "OBJECT");
            } else if ("DATA_OBJECT".equals(call.refType()) && !Set.of("API", "DB", "DATABASE").contains(first(payload.getString("sourceType"), "INPUT"))) {
                RuleDataObjectField field = payload.getJSONArray("fields").toJavaList(RuleDataObjectField.class).stream().filter(item -> call.refId().equals(item.getId())).findFirst().orElseThrow();
                addInput(inputs, objectPath(payload, field), field.getVarLabel(), field.getVarType());
            }
        }
        return new ArrayList<>(inputs.values());
    }

    private static void addInput(Map<String, RuleDefinitionInputField> inputs, String path, String label, String type) {
        if (path == null || path.isBlank()) return;
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        // 原引用身份保存在 Operand；外部输入是历史版本声明的请求路径，不能回绑当前同名字段。
        field.setRefType("PATH"); field.setFieldName(path); field.setScriptName(path);
        field.setFieldLabel(label); field.setFieldType(type); field.setStatus(1);
        RuleDefinitionInputField previous = inputs.putIfAbsent(path, field);
        if (previous != null && !Objects.equals(previous.getFieldType(), type)) throw new IllegalArgumentException("指定版本对同一路径声明了不兼容类型：" + path);
    }

    public Object resolve(ResourceVersionCall call, Long projectId, Map<String, Object> values, VariableResolveOptions options) {
        JSONObject stored = stored(call, projectId, options);
        JSONObject payload = stored.getJSONObject("payload");
        Map<String, Object> attribution = new LinkedHashMap<>();
        attribution.put("type", "RESOURCE_VERSION"); attribution.put("refType", call.refType()); attribution.put("refId", call.refId());
        attribution.put("versionSource", call.versionSource() == null ? "SNAPSHOT" : call.versionSource());
        attribution.put("versionId", call.versionId()); attribution.put("resolvedVersionId", stored.get("resolvedVersionId") == null ? call.versionId() : stored.get("resolvedVersionId"));
        attribution.put("version", stored.get("displayVersion")); attribution.put("revision", stored.get("version"));
        RuntimeContextBridge.addTraceEvent(attribution);
        VariableResolveOptions isolated = fork(options, projectId);
        if (call.statusDimension() != null) isolated.setStatusReferenceKeys(Set.of(call.refType() + ":" + call.refId()));
        Object value;
        switch (call.refType()) {
            case "MODEL", "MODEL_OUTPUT" -> {
                RuleModelVersion version = payload.toJavaObject(RuleModelVersion.class);
                RuleModel model = modelVersions.executableModel(version);
                if (options != null && options.getInvocationCache() == null) options.setInvocationCache(new VariableResolutionInvocationCache());
                Map<String, Object> result = options == null ? modelVersions.execute(version, values, isolated)
                        : options.getInvocationCache().resolve("MODEL_VERSION:" + version.getModelId() + "@" + version.getId(),
                        () -> modelVersions.execute(version, values, isolated));
                if (Boolean.FALSE.equals(result.get("success"))) throw new IllegalStateException("指定模型版本执行失败：" + result.get("errorMessage"));
                value = result.get("outputs");
                if ("MODEL_OUTPUT".equals(call.refType())) {
                    RuleModelOutputField field = model.getOutputFields().stream().filter(item -> call.refId().equals(item.getId())).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("字段不属于指定模型版本"));
                    value = read(value, first(field.getFieldName(), field.getFeatureName(), field.getScriptName()));
                }
            }
            case "FUNCTION" -> {
                RuleFunction function = payload.toJavaObject(RuleFunction.class);
                if (options != null && options.isApiExpression()) ExternalApiExecutionConfig.validateVersionFunction(function);
                value = functionService.invokeSnapshot(function, call.arguments());
            }
            case "VARIABLE", "CONSTANT" -> {
                RuleVariable variable = payload.toJavaObject(RuleVariable.class);
                if ("CONSTANT".equals(call.refType()) && !"CONSTANT".equals(variable.getVarSource()))
                    throw new IllegalArgumentException("指定历史字段不是常量");
                value = "CONSTANT".equals(variable.getVarSource())
                        ? ConstantValueCodec.toRuntimeValue(variable.getVarType(), variable.getDefaultValue())
                        : variableResolver.resolveVersion(variable, values, isolated);
            }
            case "DATA_OBJECT", "DATA_OBJECT_ROOT" -> {
                List<RuleDataObjectField> fields = payload.getJSONArray("fields").toJavaList(RuleDataObjectField.class);
                RuleDataObjectField field = "DATA_OBJECT_ROOT".equals(call.refType()) ? null : fields.stream().filter(item -> call.refId().equals(item.getId())).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("字段不属于指定数据对象版本"));
                for (RuleDataObjectField item : fields) {
                    item.setObjectSourceType(first(payload.getString("sourceType"), "INPUT"));
                    item.setObjectSourceContent(payload.getString("sourceContent"));
                    item.setObjectCode(payload.getString("objectCode"));
                    item.setObjectScriptName(first(payload.getString("scriptName"), payload.getString("objectCode")));
                }
                String path = field == null ? first(payload.getString("scriptName"), payload.getString("objectCode")) : objectPath(payload, field);
                Map<String, Object> resolved = new LinkedHashMap<>(values);
                if (field == null) objectResolver.resolveWholeObject(projectId, payload.toJavaObject(com.hengshucredit.rule.model.entity.RuleDataObject.class), resolved, isolated, fields);
                RuleDefinitionInputField direct = new RuleDefinitionInputField();
                direct.setVarId(call.refId()); direct.setRefType(call.refType()); direct.setScriptName(path);
                if (field != null) objectResolver.resolve(projectId, List.of(direct), resolved, isolated, fields);
                value = read(resolved, path);
                if (value == null && field != null && field.referencesValue()) {
                    value = variableResolver.resolveReferencedVariable(field.getRefVariableId(), values, isolated);
                }
            }
            case "EXTERNAL_API" -> {
                RuleExternalApiConfig api = payload.toJavaObject(RuleExternalApiConfig.class);
                Map<String, Object> response = apiConsumer.resolveVersion(projectId, api, values, options == null ? isolated : options, call.bindingKey());
                ExternalApiConsumerService.recordStatus(response, isolated, call.refType(), call.refId());
                value = ExternalApiConsumerService.select(response, call.relativePath());
            }
            default -> throw new IllegalArgumentException("不支持的固定版本类型");
        }
        if (call.statusDimension() != null) {
            Object state = isolated.getSourceStates().getOrDefault(call.refType() + ":" + call.refId(), Map.of()).get(call.statusDimension());
            return state != null && String.valueOf(state).equalsIgnoreCase(call.expectedStatus());
        }
        return "EXTERNAL_API".equals(call.refType()) ? value : read(value, call.relativePath());
    }

    /** 发布制品携带无密钥的历史配置；本环境敏感配置仍从不可变版本记录恢复。 */
    public JSONObject snapshot(ResourceVersionCall call, Long projectId) {
        return snapshot(call, projectId, null);
    }

    private JSONObject snapshot(ResourceVersionCall call, Long projectId, Map<String, GovernedResourceVersion> capturedHeads) {
        return snapshot(call, projectId, capturedHeads, null);
    }

    private JSONObject snapshot(ResourceVersionCall call, Long projectId, Map<String, GovernedResourceVersion> capturedHeads,
                                GovernedResourceVersion selectedGovernedVersion) {
        if ("BUSINESS_VERSION".equals(call.versionSource())) {
            GovernedResourceVersion anchor = versionMapper.selectById(call.versionId());
            String type = call.refType().startsWith("MODEL") ? "MODEL" : governanceType(call.refType());
            if (anchor == null || !type.equals(anchor.getResourceType())) throw new IllegalArgumentException("业务版本来源不匹配");
            String key = type + ":" + anchor.getResourceId() + ":" + anchor.getDisplayVersion();
            GovernedResourceVersion head = capturedHeads == null ? ResourceVersionLines.head(versionHistory(type, anchor.getResourceId()), anchor)
                    : capturedHeads.computeIfAbsent(key, ignored -> JSON.parseObject(JSON.toJSONString(
                            ResourceVersionLines.head(versionHistory(type, anchor.getResourceId()), anchor)), GovernedResourceVersion.class));
            validateBusinessHead(head);
            Long snapshotId = head.getId();
            if ("MODEL".equals(type)) {
                Integer number = JSON.parseObject(head.getSnapshotJson()).getInteger("publishedVersion");
                RuleModelVersion modelVersion = modelVersionMapper.selectOne(new LambdaQueryWrapper<RuleModelVersion>()
                        .eq(RuleModelVersion::getModelId, head.getResourceId()).eq(RuleModelVersion::getVersion, number));
                if (modelVersion == null) throw new IllegalArgumentException("业务版本缺少模型执行快照");
                snapshotId = modelVersion.getId();
            }
            JSONObject result = snapshot(new ResourceVersionCall(call.refType(), call.refId(), snapshotId,
                    call.relativePath(), call.arguments(), call.statusDimension(), call.expectedStatus()), projectId, capturedHeads, head);
            result.put("key", call.bindingKey()); result.put("versionSource", call.versionSource());
            result.put("versionId", call.versionId()); result.put("resolvedVersionId", head.getId());
            result.put("displayVersion", head.getDisplayVersion()); result.put("version", head.getVersionNo());
            return result;
        }
        JSONObject result = new JSONObject(true);
        result.put("key", call.bindingKey()); result.put("refType", call.refType());
        result.put("refId", call.refId()); result.put("versionId", call.versionId());
        if ("FUNCTION_HISTORY".equals(call.versionSource())) {
            RuleFunctionVersion version = functionVersionMapper.selectById(call.versionId());
            if (version == null || !call.refId().equals(version.getFunctionId()) || version.getFunctionJson() == null)
                throw new IllegalArgumentException("函数历史版本不存在或归属不匹配");
            assertNativeVersionAvailable("FUNCTION", version.getFunctionId(), version.getBusinessVersion(), version.getId());
            JSONObject payload = JSON.parseObject(version.getFunctionJson());
            if (!Integer.valueOf(1).equals(payload.getInteger("status"))) throw new IllegalArgumentException("函数历史版本尚未发布或已停用");
            if (!call.refId().equals(payload.getLong("id"))) throw new IllegalArgumentException("函数历史快照归属不匹配");
            checkScope(payload.getString("scope"), payload.getLong("projectId"), projectId);
            result.put("version", version.getVersion()); result.put("ownerId", version.getFunctionId());
            result.put("displayVersion", version.getDisplayVersion());
            result.put("versionSource", call.versionSource()); result.put("payload", payload);
        } else if (call.refType().startsWith("MODEL")) {
            RuleModelVersion version = modelVersionMapper.selectById(call.versionId());
            RuleModel model = publishedModel(version);
            checkScope(model.getScope(), model.getProjectId(), projectId);
            if ("MODEL".equals(call.refType()) ? !call.refId().equals(version.getModelId())
                    : model.getOutputFields().stream().noneMatch(field -> call.refId().equals(field.getId())))
                throw new IllegalArgumentException("资源不属于指定模型版本");
            result.put("version", version.getVersion()); result.put("ownerId", version.getModelId());
            result.put("displayVersion", version.getDisplayVersion());
            result.put("payload", JSON.toJSON(version));
        } else {
            GovernedResourceVersion version = selectedGovernedVersion == null ? versionMapper.selectById(call.versionId()) : selectedGovernedVersion;
            String type = governanceType(call.refType());
            if (version == null || !type.equals(version.getResourceType())) throw new IllegalArgumentException("资源版本不存在或类型不匹配");
            if (!published(version)) throw new IllegalArgumentException("资源版本尚未发布或已停用，不能作为上游引用");
            if (!Objects.equals(Sha256Digests.text(version.getSnapshotJson()), version.getSnapshotDigest()))
                throw new IllegalArgumentException("资源版本快照摘要校验失败");
            JSONObject payload = JSON.parseObject(version.getSnapshotJson());
            if (!Objects.equals(payload.getLong("id"), version.getResourceId())) throw new IllegalArgumentException("版本快照资源归属不匹配");
            if ("DATA_OBJECT".equals(type) && !"DATA_OBJECT_ROOT".equals(call.refType())) {
                if (payload.getJSONArray("fields") == null || payload.getJSONArray("fields").stream().noneMatch(
                        field -> call.refId().equals(((JSONObject) JSON.toJSON(field)).getLong("id"))))
                    throw new IllegalArgumentException("字段不属于指定数据对象版本");
            } else if (!call.refId().equals(version.getResourceId())) throw new IllegalArgumentException("资源不属于指定版本");
            if (!"EXTERNAL_API".equals(type)) checkScope(payload.getString("scope"), payload.getLong("projectId"), projectId);
            result.put("version", version.getVersionNo()); result.put("ownerId", version.getResourceId());
            result.put("displayVersion", version.getDisplayVersion());
            result.put("payload", payload); result.put("digest", version.getSnapshotDigest());
            result.put("secretDigest", version.getSecretDigest());
            result.put("requiresSecrets", version.getSecretDigest() != null && !version.getSecretDigest().isBlank());
        }
        return result;
    }

    private JSONObject stored(ResourceVersionCall call, Long projectId, VariableResolveOptions options) {
        var runtime = options == null ? null : options.getRuntimeSnapshot();
        JSONObject stored = runtime == null || "BUSINESS_VERSION".equals(call.versionSource()) && !runtime.isImported()
                ? snapshot(call, projectId, options == null ? null : options.getBusinessVersionHeads()) : runtime.getResourceVersions().get(call.bindingKey());
        if (stored == null) throw new IllegalArgumentException("执行制品缺少指定资源版本：" + call.bindingKey());
        stored = JSON.parseObject(stored.toJSONString());
        JSONObject publicPayload = stored.getJSONObject("payload");
        if (runtime != null && runtime.isImported() && ("EXTERNAL_API".equals(call.refType())
                || "VARIABLE".equals(call.refType()) && Set.of("API", "DB", "LIST", "SFTP", "DERIVED").contains(publicPayload.getString("varSource") == null ? "" : publicPayload.getString("varSource"))
                || Set.of("DATA_OBJECT", "DATA_OBJECT_ROOT").contains(call.refType()) && Set.of("API", "DB", "DATABASE").contains(publicPayload.getString("sourceType") == null ? "" : publicPayload.getString("sourceType"))))
            throw new IllegalArgumentException("导入的固定版本含外部资源引用，请在目标环境重新绑定并发布；禁止按来源环境 ID 调用本地资源");
        if (stored.getBooleanValue("requiresSecrets")) {
            if (runtime != null && runtime.isImported()) throw new IllegalArgumentException("导入制品的历史敏感配置需要在目标环境重新选择并发布版本");
            GovernedResourceVersion version = versionMapper.selectById(stored.getLong("resolvedVersionId") == null ? call.versionId() : stored.getLong("resolvedVersionId"));
            if (version == null || !Objects.equals(version.getSnapshotDigest(), stored.getString("digest"))
                    || !Objects.equals(version.getSecretDigest(), stored.getString("secretDigest")))
                throw new IllegalArgumentException("历史敏感配置不可用或摘要不匹配");
            stored.put("payload", JSON.toJSON(secretCodec.restore(new ResourceSnapshot(version.getSnapshotJson(),
                    version.getEffectiveStatus(), version.getSecretPayloadCiphertext(), version.getSecretDigest()))));
        }
        return stored;
    }

    public List<Map<String, Object>> catalog(String refType, Long ownerId) {
        List<Map<String, Object>> result = new ArrayList<>();
        String governedType = refType.startsWith("MODEL") ? "MODEL" : governanceType(refType);
        bootstrap.ensure(governedType, ownerId);
        List<GovernedResourceVersion> history = versionHistory(governedType, ownerId);
        for (GovernedResourceVersion head : ResourceVersionLines.heads(history)) {
            if (!ResourceVersionLines.published(head)) continue;
            Map<String, Object> item = versionOption(head.getId(), head.getVersionNo(), ownerId);
            item.put("displayVersion", head.getDisplayVersion()); item.put("versionSource", "BUSINESS_VERSION");
            result.add(item);
        }
        if (refType.startsWith("MODEL")) {
            for (RuleModelVersion version : modelVersionMapper.selectList(new LambdaQueryWrapper<RuleModelVersion>()
                    .select(RuleModelVersion.class, field -> !Set.of("modelContent", "executionSnapshotJson").contains(field.getProperty()))
                    .eq(RuleModelVersion::getModelId, ownerId).orderByDesc(RuleModelVersion::getVersion))) {
                Map<String, Object> item = versionOption(version.getId(), version.getVersion(), ownerId);
                item.put("displayVersion", version.getDisplayVersion());
                if (nativeVersionDeleted(history, "MODEL", ownerId, version.getBusinessVersion(), version.getId())) {
                    item.put("disabled", true); item.put("reason", "所属业务版本已删除");
                } else if (!Integer.valueOf(1).equals(version.getStatus()) || version.getExecutionSnapshotDigest() == null) {
                    item.put("disabled", true); item.put("reason", "模型版本缺少已发布执行快照");
                }
                result.add(item);
            }
        } else {
            String type = governanceType(refType);
            for (GovernedResourceVersion version : history) {
                if (published(version)) {
                    Map<String, Object> option = versionOption(version.getId(), version.getVersionNo(), ownerId);
                    option.put("displayVersion", version.getDisplayVersion());
                    result.add(option);
                }
            }
            if ("FUNCTION".equals(type)) for (RuleFunctionVersion version : functionVersionMapper.selectList(new LambdaQueryWrapper<RuleFunctionVersion>()
                    .eq(RuleFunctionVersion::getFunctionId, ownerId).orderByDesc(RuleFunctionVersion::getVersion))) {
                Map<String, Object> option = versionOption(version.getId(), version.getVersion(), ownerId);
                option.put("displayVersion", version.getDisplayVersion());
                option.put("versionSource", "FUNCTION_HISTORY");
                if (nativeVersionDeleted(history, "FUNCTION", ownerId, version.getBusinessVersion(), version.getId())) { option.put("disabled", true); option.put("reason", "所属业务版本已删除"); }
                else if (version.getFunctionJson() == null || version.getFunctionJson().isBlank()) { option.put("disabled", true); option.put("reason", "历史函数缺少执行快照"); }
                else if (!Integer.valueOf(1).equals(JSON.parseObject(version.getFunctionJson()).getInteger("status"))) {
                    option.put("disabled", true); option.put("reason", "历史函数版本未发布或已停用");
                }
                result.add(option);
            }
        }
        return result;
    }

    /** 返回白名单字段元信息，不返回脚本、文件、请求配置或敏感配置。 */
    public Map<String, Object> fields(String type, Long ownerId, Long versionId) {
        List<Map<String, Object>> fields = new ArrayList<>();
        Map<String, Object> result;
        if ("MODEL".equals(type)) {
            RuleModelVersion version = modelVersionMapper.selectById(versionId);
            RuleModel model = publishedModel(version);
            if (!ownerId.equals(version.getModelId())) throw new IllegalArgumentException("模型版本归属不匹配");
            result = versionOption(versionId, version.getVersion(), ownerId);
            result.put("displayVersion", version.getDisplayVersion());
            for (RuleModelOutputField output : model.getOutputFields()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("refId", String.valueOf(output.getId())); item.put("refType", "MODEL_OUTPUT");
                item.put("code", model.getModelCode() + "." + first(output.getFieldName(), output.getFeatureName(), output.getScriptName()));
                item.put("label", first(output.getFieldLabel(), output.getFieldName())); item.put("valueType", output.getFieldType());
                fields.add(item);
            }
            result.put("ownerCode", model.getModelCode()); result.put("ownerLabel", model.getModelName());
        } else if ("DATA_OBJECT".equals(type)) {
            GovernedResourceVersion version = versionMapper.selectById(versionId);
            if (version == null || !type.equals(version.getResourceType()) || !ownerId.equals(version.getResourceId()))
                throw new IllegalArgumentException("数据对象版本归属不匹配");
            if (!published(version)) throw new IllegalArgumentException("数据对象版本尚未发布或已停用");
            if (!Objects.equals(Sha256Digests.text(version.getSnapshotJson()), version.getSnapshotDigest())) throw new IllegalArgumentException("版本快照摘要校验失败");
            JSONObject owner = JSON.parseObject(version.getSnapshotJson());
            result = versionOption(versionId, version.getVersionNo(), ownerId);
            result.put("displayVersion", version.getDisplayVersion());
            for (RuleDataObjectField field : owner.getJSONArray("fields").toJavaList(RuleDataObjectField.class)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("refId", String.valueOf(field.getId())); item.put("refType", type);
                item.put("code", objectPath(owner, field)); item.put("label", field.getVarLabel()); item.put("valueType", field.getVarType());
                item.put("parentFieldId", field.getParentFieldId() == null ? null : String.valueOf(field.getParentFieldId()));
                fields.add(item);
            }
            result.put("ownerCode", first(owner.getString("scriptName"), owner.getString("objectCode"))); result.put("ownerLabel", owner.getString("objectLabel"));
        } else throw new IllegalArgumentException("该分类没有历史子字段");
        result.put("fields", fields);
        return result;
    }

    public Map<String, Object> fields(String type, Long ownerId, Long versionId, String source) {
        if (source == null || source.isBlank()) return fields(type, ownerId, versionId);
        if (!"BUSINESS_VERSION".equals(source)) throw new IllegalArgumentException("资源版本来源不匹配");
        GovernedResourceVersion anchor = versionMapper.selectById(versionId);
        if (anchor == null || !type.equals(anchor.getResourceType()) || !ownerId.equals(anchor.getResourceId()))
            throw new IllegalArgumentException("资源版本归属不匹配");
        GovernedResourceVersion head = ResourceVersionLines.head(versionHistory(type, ownerId), anchor);
        validateBusinessHead(head);
        Long nativeId = head.getId();
        if ("MODEL".equals(type)) {
            Integer number = JSON.parseObject(head.getSnapshotJson()).getInteger("publishedVersion");
            var nativeVersion = modelVersionMapper.selectOne(new LambdaQueryWrapper<RuleModelVersion>()
                    .eq(RuleModelVersion::getModelId, ownerId).eq(RuleModelVersion::getVersion, number));
            if (nativeVersion == null) throw new IllegalArgumentException("模型版本没有执行快照");
            nativeId = nativeVersion.getId();
        }
        Map<String, Object> result = fields(type, ownerId, nativeId);
        result.put("versionId", String.valueOf(versionId)); result.put("versionSource", source);
        result.put("displayVersion", head.getDisplayVersion()); result.put("version", head.getVersionNo());
        return result;
    }

    public Map<String, Object> field(String type, Long refId, Long versionId) {
        return field(type, refId, versionId, null);
    }

    public Map<String, Object> field(String type, Long refId, Long versionId, String versionSource) {
        ResourceVersionCall call = new ResourceVersionCall(type, refId, versionId, "", List.of(), null, null, versionSource);
        JSONObject snapshot = snapshot(call, null);
        JSONObject payload = snapshot.getJSONObject("payload");
        Map<String, Object> field = versionOption(versionId, snapshot.getInteger("version"), snapshot.getLong("ownerId"));
        field.put("displayVersion", snapshot.getInteger("displayVersion"));
        if (versionSource != null) field.put("versionSource", versionSource);
        field.put("refId", String.valueOf(refId)); field.put("refType", type);
        if (type.startsWith("MODEL")) {
            RuleModel model = modelVersions.executableModel(payload.toJavaObject(RuleModelVersion.class));
            field.put("code", model.getModelCode()); field.put("label", model.getModelName()); field.put("valueType", "OBJECT");
            if ("MODEL_OUTPUT".equals(type)) {
                RuleModelOutputField output = model.getOutputFields().stream().filter(item -> refId.equals(item.getId())).findFirst().orElseThrow();
                field.put("code", model.getModelCode() + "." + first(output.getFieldName(), output.getFeatureName(), output.getScriptName()));
                field.put("label", first(output.getFieldLabel(), output.getFieldName())); field.put("valueType", output.getFieldType());
            }
        } else if ("FUNCTION".equals(type)) {
            for (String key : List.of("funcCode", "funcName", "paramsJson", "returnType")) field.put(key, payload.get(key));
        } else if ("DATA_OBJECT_ROOT".equals(type)) {
            field.put("code", first(payload.getString("scriptName"), payload.getString("objectCode")));
            field.put("label", payload.getString("objectLabel")); field.put("valueType", "OBJECT");
        } else if ("DATA_OBJECT".equals(type)) {
            RuleDataObjectField data = payload.getJSONArray("fields").toJavaList(RuleDataObjectField.class).stream()
                    .filter(item -> refId.equals(item.getId())).findFirst().orElseThrow();
            field.put("code", objectPath(payload, data)); field.put("label", data.getVarLabel()); field.put("valueType", data.getVarType());
        } else if (!"EXTERNAL_API".equals(type)) {
            field.put("code", first(payload.getString("scriptName"), payload.getString("varCode")));
            field.put("label", payload.getString("varLabel")); field.put("valueType", payload.getString("varType"));
            field.put("varSource", payload.getString("varSource"));
            if ("CONSTANT".equals(type)) field.put("constantValue", payload.get("defaultValue"));
        }
        return field;
    }

    private static Map<String, Object> versionOption(Long id, Integer version, Long ownerId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("versionId", String.valueOf(id)); result.put("version", version); result.put("versionOwnerId", String.valueOf(ownerId));
        return result;
    }

    private static boolean published(GovernedResourceVersion version) {
        return ResourceVersionLines.published(version);
    }

    private List<GovernedResourceVersion> versionHistory(String type, Long id) {
        return versionMapper.selectList(new LambdaQueryWrapper<GovernedResourceVersion>()
                .select(GovernedResourceVersion.class, field -> !"secretPayloadCiphertext".equals(field.getProperty()))
                .eq(GovernedResourceVersion::getResourceType, type).eq(GovernedResourceVersion::getResourceId, id)
                .orderByDesc(GovernedResourceVersion::getVersionNo));
    }

    public GovernedResourceVersion configurationVersion(String type, Long id, Long versionId) {
        GovernedResourceVersion anchor = versionMapper.selectById(versionId);
        if (anchor == null || !type.equals(anchor.getResourceType()) || !id.equals(anchor.getResourceId()))
            throw new IllegalArgumentException("资源版本归属不匹配");
        GovernedResourceVersion head = ResourceVersionLines.head(versionHistory(type, id), anchor);
        validateBusinessHead(head);
        return head;
    }

    private void validateBusinessHead(GovernedResourceVersion head) {
        if (!ResourceVersionLines.published(head)) throw new IllegalArgumentException("指定版本尚未发布或已停用");
        if (!Objects.equals(Sha256Digests.text(head.getSnapshotJson()), head.getSnapshotDigest()))
            throw new IllegalArgumentException("版本摘要校验失败");
        if (!Objects.equals(JSON.parseObject(head.getSnapshotJson()).getLong("id"), head.getResourceId()))
            throw new IllegalArgumentException("版本快照资源归属不匹配");
    }

    private RuleModel publishedModel(RuleModelVersion version) {
        if (version != null) assertNativeVersionAvailable("MODEL", version.getModelId(), version.getBusinessVersion(), version.getId());
        RuleModel model = modelVersions.executableModel(version);
        JSONObject metadata = JSON.parseObject(version.getExecutionSnapshotJson()).getJSONObject("model");
        if (!Integer.valueOf(1).equals(version.getStatus()) || metadata == null
                || !Objects.equals(version.getVersion(), metadata.getInteger("publishedVersion"))) {
            throw new IllegalArgumentException("模型版本只有配置快照、尚未发布，不能作为上游引用");
        }
        return model;
    }

    public void assertNativeVersionAvailable(String type, Long ownerId, Integer businessVersion, Long nativeId) {
        if (nativeVersionDeleted(versionHistory(type, ownerId), type, ownerId, businessVersion, nativeId))
            throw new IllegalArgumentException("所属业务版本已删除，不能再执行、引用或恢复");
    }

    private boolean nativeVersionDeleted(List<GovernedResourceVersion> history, String type, Long ownerId, Integer businessVersion, Long nativeId) {
        List<GovernedResourceVersion> owned = history.stream().filter(row -> type.equals(row.getResourceType()) && Objects.equals(ownerId, row.getResourceId())).toList();
        for (var head : ResourceVersionLines.heads(owned)) {
            if (!Boolean.TRUE.equals(head.getDeleted())) continue;
            if (businessVersion != null && Objects.equals(businessVersion, head.getDisplayVersion())) return true;
            if (nativeId != null && (type + "_VERSION").equals(head.getLegacySourceType()) && Objects.equals(nativeId, head.getLegacySourceId())) return true;
            if ("MODEL".equals(type) && nativeId != null && Objects.equals(nativeId, JSON.parseObject(head.getSnapshotJson()).getLong("_modelContentVersionId"))) return true;
        }
        return false;
    }
    private static String governanceType(String type) {
        return switch (type) {
            case "VARIABLE", "CONSTANT" -> "VARIABLE";
            case "DATA_OBJECT_ROOT" -> "DATA_OBJECT";
            case "DATA_OBJECT", "EXTERNAL_API", "FUNCTION" -> type;
            default -> throw new IllegalArgumentException("该分类不支持固定版本");
        };
    }
    private static void checkScope(String scope, Long ownerProject, Long projectId) {
        if (projectId != null && !"GLOBAL".equals(scope) && !Objects.equals(projectId, ownerProject))
            throw new IllegalArgumentException("指定资源版本不属于当前项目可用范围");
    }
    static VariableResolveOptions fork(VariableResolveOptions source, Long projectId) {
        VariableResolveOptions target = new VariableResolveOptions();
        if (source != null) org.springframework.beans.BeanUtils.copyProperties(source, target);
        target.setInvocationCache(new VariableResolutionInvocationCache());
        target.setRequiredNamesUpstreamOnly(true); target.setExecutionProjectId(projectId);
        target.setSourceStates(new LinkedHashMap<>()); target.setHistoryFieldDefinitions(null);
        return target;
    }
    private static String objectPath(JSONObject owner, RuleDataObjectField field) {
        String root = first(owner.getString("scriptName"), owner.getString("objectCode"));
        String name = first(field.getScriptName(), field.getVarCode());
        return name.equals(root) || name.startsWith(root + ".") ? name : root + "." + name;
    }
    private static Object read(Object value, String path) {
        if (path == null || path.isBlank()) return value;
        if (value instanceof Map<?, ?> map && map.containsKey(path)) return map.get(path);
        return value == null ? null : com.alibaba.fastjson.JSONPath.eval(value, path.startsWith("$") ? path : path.startsWith(".") || path.startsWith("[") ? "$" + path : "$." + path);
    }
    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        throw new IllegalArgumentException("历史资源缺少字段编码");
    }
}
