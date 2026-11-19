package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.core.compiler.OperandVersion;
import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.governance.GovernanceSecretCodec;
import com.hengshucredit.rule.server.governance.ResourceSnapshot;
import com.hengshucredit.rule.server.mapper.GovernanceApprovalRequestMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 本机模型版本的外数绑定。制品仅携带经过认证的版本指针，凭据只从本库的不可变审批快照恢复。 */
@Service
public class ModelExternalBindings {
    private static final String FORMAT = "tianshu.model-external.v1";
    private static final String API = "EXTERNAL_API";
    private static final String SOURCE = "EXTERNAL_DATASOURCE";
    private static final Set<String> SAMPLE_KEYS = Set.of("sample", "samples", "requestSample", "responseSample");

    private final RuleExternalApiConfigMapper apis;
    private final RuleExternalDatasourceMapper sources;
    private final GovernedResourceMapper resources;
    private final GovernedResourceVersionMapper versions;
    private final GovernanceApprovalRequestMapper approvals;
    private final GovernanceSecretCodec secrets;
    private final CredentialCipher cipher;
    private final LocalModelBindingSignature signatures;
    private final RuleVariableService variables;

    public ModelExternalBindings(RuleExternalApiConfigMapper apis, RuleExternalDatasourceMapper sources,
            GovernedResourceMapper resources, GovernedResourceVersionMapper versions,
            GovernanceApprovalRequestMapper approvals, GovernanceSecretCodec secrets, CredentialCipher cipher,
            LocalModelBindingSignature signatures, @Lazy RuleVariableService variables) {
        this.apis = apis;
        this.sources = sources;
        this.resources = resources;
        this.versions = versions;
        this.approvals = approvals;
        this.secrets = secrets;
        this.cipher = cipher;
        this.signatures = signatures;
        this.variables = variables;
    }

    @Transactional(readOnly = true)
    public JSONObject capture(long apiId, Long ownerProjectId) {
        return load(apiId, ownerProjectId, null, new TreeMap<>()).descriptor();
    }

    @Transactional(readOnly = true)
    public FrozenExternalApiScope open(Map<Long, JSONObject> descriptors, Long executionProjectId) {
        Map<Long, RuleExternalApiConfig> frozenApis = new TreeMap<>();
        Map<Long, RuleExternalDatasource> frozenSources = new TreeMap<>();
        Map<Long, Long> sourceVersions = new TreeMap<>();
        Map<String, String> paths = new TreeMap<>();
        Map<String, String> identities = new TreeMap<>();
        Map<Long, Map<String, String>> availableByProject = new TreeMap<>();
        for (var entry : descriptors.entrySet()) {
            require(entry.getKey() != null && entry.getValue() != null, "缺少外数版本绑定");
            var binding = load(entry.getKey(), executionProjectId, entry.getValue(), availableByProject);
            var ds = binding.source();
            Long version = binding.descriptor().getLong("datasourceVersionId");
            var prior = sourceVersions.putIfAbsent(ds.getId(), version);
            require(prior == null || prior.equals(version), "同一模型闭包内的数据源冻结版本冲突");
            frozenApis.put(entry.getKey(), binding.api());
            frozenSources.put(ds.getId(), ds);
            binding.paths().forEach((k, v) -> {
                var previous = paths.putIfAbsent(k, v);
                require(previous == null || previous.equals(v), "同一模型闭包内的字段路径冲突");
            });
            identities.put(String.valueOf(entry.getKey()), binding.descriptor().getString("seal"));
        }
        return FrozenExternalApiScope.open(frozenApis, frozenSources, paths, Sha256Digests.text(CanonicalJson.write(identities)));
    }

    private record Binding(JSONObject descriptor, RuleExternalApiConfig api, RuleExternalDatasource source, Map<String, String> paths) {}
    private record Approved(JSONObject payload, long versionId, String evidence) {}

    private Binding load(long apiId, Long executionProject, JSONObject supplied, Map<Long, Map<String, String>> availableByProject) {
        var live = apis.selectById(apiId);
        require(live != null && Long.valueOf(apiId).equals(live.getId()) && enabled(live.getStatus()) && live.getDatasourceId() != null,
                "外数接口不存在或已停用");
        var owner = sources.selectById(live.getDatasourceId());
        require(owner != null && live.getDatasourceId().equals(owner.getId()) && enabled(owner.getStatus()), "外数数据源不存在或已停用");
        long project = scope(owner);
        require(project == 0 || Objects.equals(project, executionProject), "外数接口不属于执行项目");
        var apiRevision = approved(API, apiId, project, versionId(supplied, "apiVersionId"));
        var sourceRevision = approved(SOURCE, owner.getId(), project, versionId(supplied, "datasourceVersionId"));
        var api = apiRevision.payload().toJavaObject(RuleExternalApiConfig.class);
        var source = sourceRevision.payload().toJavaObject(RuleExternalDatasource.class);
        require(Objects.equals(api.getDatasourceId(), source.getId()) && scope(source) == project
                && enabled(api.getStatus()) && enabled(source.getStatus()), "历史外数资源的状态或归属不匹配");
        require("HTTP".equalsIgnoreCase(source.getProtocol())
                && (api.getRequestMode() == null || "SYNC".equalsIgnoreCase(api.getRequestMode()))
                && api.getRequestObjectId() == null && api.getResponseObjectId() == null, "模型仅支持可完整冻结的同步 HTTP 接口");
        // Per-resource field paths matter on rename. At execution validate project membership again,
        // but use the signed path from the publication, not the current display or script name.
        Map<String, String> available = availableByProject.computeIfAbsent(project, variables::buildRefScriptNameMap);
        var requested = new java.util.TreeSet<String>();
        for (String config : new String[]{api.getExecutionConfig(), api.getAuthApiConfig(), source.getAuthConfig(),
                api.getHeaderConfig(), api.getQueryConfig(), api.getRequestMapping(), api.getResponseMapping(),
                api.getBillingCondition(), api.getExceptionCondition(), api.getRetryCondition(), api.getTokenFailureCondition(),
                api.getCacheKeyConfig(), api.getSuccessCondition()}) {
            inspectConfig(config, requested);
        }
        Map<String, String> paths = new TreeMap<>();
        JSONObject recorded = supplied == null ? null : supplied.getJSONObject("paths");
        require(supplied == null || recorded != null && recorded.keySet().equals(requested), "模型冻结字段清单不匹配");
        for (String ref : requested) {
            require(available.containsKey(ref), "模型外数引用字段不在该数据源可用范围内：" + ref);
            String path = recorded == null ? available.get(ref) : recorded.getString(ref);
            require(path != null && !path.isBlank(), "外数引用缺少冻结字段路径");
            paths.put(ref, path);
        }
        JSONObject descriptor = new JSONObject(true);
        descriptor.put("format", FORMAT);
        descriptor.put("projectId", project);
        descriptor.put("apiId", apiId);
        descriptor.put("apiVersionId", apiRevision.versionId());
        descriptor.put("datasourceId", source.getId());
        descriptor.put("datasourceVersionId", sourceRevision.versionId());
        descriptor.put("paths", new JSONObject(new TreeMap<>(paths)));
        String keyId = supplied == null ? signatures.activeKeyId() : supplied.getString("keyId");
        descriptor.put("keyId", keyId);
        String seal = signatures.sign(keyId, FORMAT, CanonicalJson.write(List.of(descriptor, apiRevision.evidence(), sourceRevision.evidence())));
        descriptor.put("seal", seal);
        if (supplied != null) {
            require(MessageDigest.isEqual(CanonicalJson.write(descriptor).getBytes(StandardCharsets.UTF_8),
                    CanonicalJson.write(supplied).getBytes(StandardCharsets.UTF_8)), "模型外数绑定与本机审批证据不匹配");
        }
        return new Binding(descriptor, api, source, paths);
    }

    private Approved approved(String type, long id, long project, Long pinnedId) {
        var resource = resources.selectOne(new LambdaQueryWrapper<GovernedResource>()
                .eq(GovernedResource::getResourceType, type).eq(GovernedResource::getResourceId, id));
        require(resource != null && "ACTIVE".equals(resource.getEffectiveStatus())
                && Long.valueOf(id).equals(resource.getResourceId()) && type.equals(resource.getResourceType())
                && project == projectId(resource.getProjectId()) && resource.getEffectiveVersionId() != null,
                "外数资源缺少有效的本机治理版本：" + type + ":" + id);
        long versionId = pinnedId == null ? resource.getEffectiveVersionId() : pinnedId;
        var version = versions.selectById(versionId);
        require(version != null && Long.valueOf(versionId).equals(version.getId()) && Long.valueOf(id).equals(version.getResourceId())
                && type.equals(version.getResourceType()) && Objects.equals(resource.getId(), version.getGovernedResourceId())
                && "ACTIVE".equals(version.getEffectiveStatus()) && !Boolean.TRUE.equals(version.getDeleted())
                && version.getApprovalRequestId() != null && version.getSnapshotJson() != null
                && Objects.equals(version.getSnapshotDigest(), Sha256Digests.text(version.getSnapshotJson())),
                "外数历史版本归属或摘要校验失败：" + type + ":" + versionId);
        var approval = approvals.selectById(version.getApprovalRequestId());
        require(approval != null && version.getApprovalRequestId().equals(approval.getId())
                && "APPROVED".equals(approval.getStatus()) && approval.getReviewTime() != null
                && approval.getRequestNo() != null && !approval.getRequestNo().isBlank()
                && Long.valueOf(id).equals(approval.getResourceId()) && type.equals(approval.getResourceType())
                && projectId(approval.getProjectId()) == project && approval.getSubmittedSnapshotJson() != null
                && Objects.equals(approval.getSnapshotDigest(), Sha256Digests.text(approval.getSubmittedSnapshotJson()))
                && Objects.equals(approval.getSnapshotDigest(), version.getSnapshotDigest())
                && Objects.equals(approval.getSecretDigest(), version.getSecretDigest()),
                "外数历史配置缺少完整的本机审批证据：" + type + ":" + versionId);
        var publicPayload = JSON.parseObject(version.getSnapshotJson());
        require(Long.valueOf(id).equals(publicPayload.getLong("id")), "审批快照内的资源 ID 不匹配");
        verifySecrets(version, publicPayload);
        JSONObject payload = (JSONObject) JSON.toJSON(secrets.restore(new ResourceSnapshot(version.getSnapshotJson(),
                version.getEffectiveStatus(), version.getSecretPayloadCiphertext(), version.getSecretDigest())));
        // Domain-separated keyed seal protects the private evidence. Neither a plaintext secret
        // nor its (possibly dictionary-checkable) governance digest goes into the descriptor.
        String evidence = CanonicalJson.write(List.of(resource.getId(), versionId, approval.getRequestNo(),
                approval.getId(), approval.getSnapshotDigest(), version.getSnapshotDigest(), Objects.toString(version.getSecretDigest(), "")));
        return new Approved(payload, versionId, evidence);
    }

    private void verifySecrets(GovernedResourceVersion version, JSONObject payload) {
        String ciphertext = version.getSecretPayloadCiphertext();
        String digest = version.getSecretDigest();
        JSONObject markers = payload.getJSONObject("_secretConfigured");
        if (ciphertext == null || ciphertext.isBlank()) {
            require((digest == null || digest.isBlank()) && (markers == null || markers.isEmpty()), "外数冻结凭据缺失");
            return;
        }
        String plain = cipher.decrypt(ciphertext);
        require(Objects.equals(Sha256Digests.text(plain), digest), "外数冻结凭据摘要不匹配");
        JSONObject restored = JSON.parseObject(plain);
        require(restored != null && markers != null && restored.keySet().equals(markers.keySet()), "外数冻结凭据清单不匹配");
        restored.forEach((key, value) -> require(Objects.equals(markers.get(key), value != null && !String.valueOf(value).isBlank()),
                "外数冻结凭据的配置状态不匹配"));
    }

    private static Long versionId(JSONObject descriptor, String field) {
        if (descriptor == null) return null;
        Long id = descriptor.getLong(field);
        require(id != null && id > 0, "外数绑定缺少有效版本标识");
        return id;
    }

    private static long scope(RuleExternalDatasource source) {
        long project = projectId(source.getProjectId());
        require(project == 0 ? "GLOBAL".equals(source.getScope()) : project > 0 && "PROJECT".equals(source.getScope()), "外数数据源归属不明确");
        return project;
    }

    private static long projectId(Long id) { return id == null ? 0L : id; }
    private static boolean enabled(Integer status) { return Integer.valueOf(1).equals(status); }

    private static void inspectConfig(String json, Set<String> requested) {
        if (json == null || json.isBlank()) return;
        String text = json.trim();
        if (text.startsWith("{") || text.startsWith("[")) inspect(JSON.parse(text), requested);
    }

    private static void inspect(Object node, Set<String> requested) {
        if (node instanceof Collection<?> list) {
            list.forEach(value -> inspect(value, requested));
        } else if (node instanceof JSONObject obj) {
            String kind = obj.getString("kind");
            if ("LITERAL".equals(kind)) return;
            require(OperandVersion.fixedVersionId(obj) == null, "模型外数配置不支持额外的固定资源版本依赖");
            if ("REFERENCE".equals(kind) || "PATH".equals(kind) && obj.get("refId") != null) {
                Long id = obj.getLong("refId");
                String type = obj.getString("refType");
                require(id != null && id > 0 && type != null && Set.of("VARIABLE", "CONSTANT", "DATA_OBJECT", "DATA_OBJECT_ROOT").contains(type),
                        "外数配置包含未冻结的字段引用");
                requested.add(type + ":" + id);
            }
            for (var entry : obj.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (SAMPLE_KEYS.contains(key)) continue;
                if ("steps".equals(key) || "validationRuleIds".equals(key)) {
                    require(value == null || value instanceof Collection<?> values && values.isEmpty(),
                            "模型仅支持同步接口自身的请求；多步调用和输入校验规则需独立冻结：" + key);
                }
                require(!("apiConfigId".equals(key) || "authApiConfigId".equals(key)) || value == null, "模型外数包含未冻结的关联 API");
                if ("authApiConfig".equals(key) && value instanceof String auth) inspectConfig(auth, requested);
                else inspect(value, requested);
            }
        }
    }

    private static void require(boolean allowed, String message) {
        if (!allowed) throw new IllegalArgumentException(message);
    }
}
