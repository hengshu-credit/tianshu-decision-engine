package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.governance.GovernanceSecretCodec;
import com.hengshucredit.rule.server.mapper.GovernanceApprovalRequestMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import java.time.LocalDateTime;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;


public class ModelExternalBindingsTest {
    private static final long PROJECT = 9L;
    private final Map<Long,GovernedResourceVersion> storedVersions = new HashMap<>();
    private final Map<Long,GovernanceApprovalRequest> storedApprovals = new HashMap<>();
    private Map<String,String> available = Map.of("VARIABLE:7", "person", "VARIABLE:8", "unrelated");
    private final RuleVariableService variables = new RuleVariableService() {
        @Override public Map<String,String> buildRefScriptNameMap(Long id) { return id == PROJECT || id == 0L ? available : Map.of(); }
    };
    private static <T> T mapper(Class<T> type, String name, Function<Object,Object> call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy, method, args) -> {
            if (!name.equals(method.getName())) throw new AssertionError("Unexpected mapper operation "+method.getName());
            return call.apply(args[0]);
        }));
    }
    private final ProjectAuthProperties keys = new ProjectAuthProperties();
    private final CredentialCipher cipher = new CredentialCipher(keys);
    private final RuleExternalApiConfig api = new RuleExternalApiConfig();
    private final RuleExternalDatasource source = new RuleExternalDatasource();
    private final GovernedResource apiOwner = owner(31, 1, 101, "EXTERNAL_API");
    private final GovernedResource dsOwner = owner(32, 2, 102, "EXTERNAL_DATASOURCE");
    private ModelExternalBindings bindings;
    private GovernedResourceVersion sourceSnapshot;
    private static final String AUTH = "{\"username\":\"tenant\",\"password\":\"short-demo-secret\"}";

    @Before public void setUp() {
        keys.setActiveKeyId("local-test");
        keys.setMasterKeys(Map.of("local-test", "unit-test-only-master-key-8e37d2"));
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), GovernedResource.class);
        var resources = mapper(GovernedResourceMapper.class, "selectOne", condition -> {
            var wrapper = (LambdaQueryWrapper<?>) condition;
            wrapper.getSqlSegment();
            var params = wrapper.getParamNameValuePairs().values();
            if (params.contains("EXTERNAL_API") && params.contains(1L)) return apiOwner;
            if (params.contains("EXTERNAL_DATASOURCE") && params.contains(2L)) return dsOwner;
            return null;
        });
        bindings = new ModelExternalBindings(mapper(RuleExternalApiConfigMapper.class,"selectById", id -> id.equals(1L) ? api : null),
                mapper(RuleExternalDatasourceMapper.class,"selectById", id -> id.equals(2L) ? source : null), resources,
                mapper(GovernedResourceVersionMapper.class,"selectById",storedVersions::get),
                mapper(GovernanceApprovalRequestMapper.class,"selectById",storedApprovals::get),
                new GovernanceSecretCodec(cipher), cipher, new LocalModelBindingSignature(keys), variables);
        api.setId(1L); api.setDatasourceId(2L); api.setStatus(1); api.setRequestMode("SYNC");
        api.setRequestMethod("POST"); api.setEndpointUrl("/query-at-publication");
        api.setExecutionConfig("{\"version\":2,\"requestFields\":[{\"validationRuleIds\":[],\"value\":"
                + "{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":7}}]}");
        source.setId(2L); source.setScope("PROJECT"); source.setProjectId(PROJECT); source.setStatus(1);
        source.setProtocol("HTTP"); source.setAuthType("BASIC");
        source.setBaseUrl("https://approved.example");
        JSONObject apiPayload = (JSONObject) JSON.toJSON(api);
        // Governance stores executionConfig as a structured object in approved API snapshots.
        apiPayload.put("executionConfig", JSON.parseObject(api.getExecutionConfig()));
        store(apiOwner, apiPayload, null);
        JSONObject ds = (JSONObject) JSON.toJSON(source);
        ds.put("_secretConfigured", Map.of("/authConfig", true));
        sourceSnapshot = store(dsOwner, ds, CanonicalJson.write(Map.of("/authConfig", AUTH)));

    }

    private static GovernedResource owner(long id, long resourceId, long snapshotId, String type) {
        var owner = new GovernedResource();
        owner.setId(id); owner.setResourceId(resourceId); owner.setResourceType(type); owner.setProjectId(PROJECT);
        owner.setEffectiveStatus("ACTIVE"); owner.setEffectiveVersionId(snapshotId);
        return owner;
    }

    private GovernedResourceVersion store(GovernedResource owner, JSONObject payload, String secretJson) {
        long versionId = owner.getEffectiveVersionId();
        var v = new GovernedResourceVersion();
        v.setId(versionId); v.setGovernedResourceId(owner.getId()); v.setResourceId(owner.getResourceId());
        v.setResourceType(owner.getResourceType()); v.setVersionNo(1); v.setEffectiveStatus("ACTIVE");
        v.setSnapshotJson(CanonicalJson.write(payload)); v.setSnapshotDigest(Sha256Digests.text(v.getSnapshotJson()));
        if (secretJson != null) {
            v.setSecretPayloadCiphertext(cipher.encrypt(secretJson));
            v.setSecretDigest(Sha256Digests.text(secretJson));
        }
        var a = new GovernanceApprovalRequest();
        a.setId(versionId+1000); a.setProjectId(owner.getProjectId()); a.setResourceId(v.getResourceId()); a.setResourceType(v.getResourceType());
        a.setRequestNo("approval-"+versionId); a.setStatus("APPROVED"); a.setReviewTime(LocalDateTime.now());
        a.setSubmittedSnapshotJson(v.getSnapshotJson()); a.setSnapshotDigest(v.getSnapshotDigest()); a.setSecretDigest(v.getSecretDigest());
        v.setApprovalRequestId(a.getId());
        storedVersions.put(versionId,v);
        storedApprovals.put(a.getId(),a);
        return v;
    }

    @Test public void usesImmutableApprovedConfigurationsAndOnlyReferencedPaths() {
        JSONObject d=bindings.capture(1L, PROJECT);
        assertEquals(Map.of("VARIABLE:7","person"),d.getJSONObject("paths"));
        assertEquals(d, bindings.capture(1L, PROJECT));
        for (String excluded : List.of(AUTH, "short-demo-secret", "approved.example", sourceSnapshot.getSecretDigest(), sourceSnapshot.getSecretPayloadCiphertext()))
            assertFalse(d.toJSONString().contains(excluded));
        api.setEndpointUrl("/reconfigured"); source.setBaseUrl("https://new.example"); source.setAuthConfig("changed");
        dsOwner.setEffectiveVersionId(222L);
        available = Map.of("VARIABLE:7","renamed");
        try (var scope=bindings.open(Map.of(1L,d),PROJECT)) {
            assertEquals("/query-at-publication", scope.api(1L).getEndpointUrl());
            assertEquals("https://approved.example", scope.datasource(2L).getBaseUrl());
            assertEquals(AUTH, scope.datasource(2L).getAuthConfig());
            assertEquals("person",scope.paths().get("VARIABLE:7"));
        }
        assertNull(FrozenExternalApiScope.forApi(1L));
    }

    @Test public void descriptorCannotBeMovedRewrittenOrOpenedOnAnotherDeployment() {
        JSONObject d=bindings.capture(1L,PROJECT);
        assertThrows(IllegalArgumentException.class,()->bindings.open(Map.of(1L,d),10L));
        var altered=JSON.parseObject(d.toJSONString());
        altered.getJSONObject("paths").put("VARIABLE:7","someOtherValue");
        assertThrows(IllegalArgumentException.class,()->bindings.open(Map.of(1L,altered),PROJECT));
        keys.setMasterKeys(Map.of("local-test","other-local-deployment"));
        assertThrows(IllegalArgumentException.class,()->bindings.open(Map.of(1L,d),PROJECT));
    }

    @Test public void requiresCurrentEnabledOwnershipAndAuthenticatedHistoricalSecrets() {
        JSONObject d=bindings.capture(1L,PROJECT);
        source.setStatus(0);
        assertThrows(IllegalArgumentException.class,()->bindings.open(Map.of(1L,d),PROJECT));
        source.setStatus(1); source.setProjectId(15L);
        assertThrows(IllegalArgumentException.class,()->bindings.open(Map.of(1L,d),PROJECT));
        source.setProjectId(PROJECT);
        sourceSnapshot.setSecretPayloadCiphertext(cipher.encrypt(CanonicalJson.write(Map.of("/authConfig","another-config"))));
        assertThrows(IllegalArgumentException.class,()->bindings.open(Map.of(1L,d),PROJECT));
        sourceSnapshot.setSecretPayloadCiphertext(null);
        assertThrows(IllegalArgumentException.class,()->bindings.open(Map.of(1L,d),PROJECT));
    }

    @Test public void publicAndSecretChangesRequireTheSameApprovedSnapshot() {
        JSONObject descriptor = bindings.capture(1, PROJECT);
        GovernanceApprovalRequest sourceApproval = storedApprovals.get(sourceSnapshot.getApprovalRequestId());
        sourceApproval.setStatus("PENDING");
        assertThrows(IllegalArgumentException.class, () -> bindings.open(Map.of(1L, descriptor), PROJECT));
        sourceApproval.setStatus("APPROVED");
        JSONObject changed = JSON.parseObject(sourceSnapshot.getSnapshotJson());
        changed.put("baseUrl", "https://unapproved.example");
        sourceSnapshot.setSnapshotJson(CanonicalJson.write(changed));
        sourceSnapshot.setSnapshotDigest(Sha256Digests.text(sourceSnapshot.getSnapshotJson()));
        assertThrows(IllegalArgumentException.class, () -> bindings.capture(1, PROJECT));
    }

    @Test public void keyRotationKeepsOldLocalApprovalsVerifiable() {
        JSONObject descriptor = bindings.capture(1, PROJECT);
        keys.setMasterKeys(Map.of("local-test", "unit-test-only-master-key-8e37d2", "new", "new-unit-test-key"));
        keys.setActiveKeyId("new");
        try (var scope = bindings.open(Map.of(1L, descriptor), PROJECT)) {
            assertEquals(AUTH, scope.datasource(2L).getAuthConfig());
        }
        JSONObject next = bindings.capture(1, PROJECT);
        assertEquals("new", next.getString("keyId"));
        assertNotEquals(next.getString("seal"), descriptor.getString("seal"));
    }
    @Test public void globalResourcesAreReusableOnlyWhenHistoricalAndCurrentOwnershipAgree() {
        apiOwner.setProjectId(0L);
        dsOwner.setProjectId(0L);
        source.setScope("GLOBAL");
        source.setProjectId(0L);
        store(apiOwner, (JSONObject) JSON.toJSON(api), null);
        store(dsOwner, (JSONObject) JSON.toJSON(source), null);
        JSONObject descriptor = bindings.capture(1, 0L);
        assertEquals(Long.valueOf(0), descriptor.getLong("projectId"));
        try (var scope = bindings.open(Map.of(1L, descriptor), 77L)) {
            assertEquals("GLOBAL", scope.datasource(2L).getScope());
        }
        dsOwner.setProjectId(PROJECT);
        assertThrows(IllegalArgumentException.class, () -> bindings.open(Map.of(1L, descriptor), 77L));
    }

    @Test public void fieldsUsedInAuthAndResponseConditionsUsePublishedPaths() {
        api.setAuthApiConfig("{\"body\":{\"client\":{\"kind\":\"PATH\",\"refType\":\"VARIABLE\",\"refId\":8}}}");
        api.setBillingCondition("{\"left\":{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":11}}");
        available = Map.of("VARIABLE:7", "person", "VARIABLE:8", "client", "VARIABLE:11", "charge");
        store(apiOwner, (JSONObject) JSON.toJSON(api), null);
        JSONObject descriptor = bindings.capture(1, PROJECT);
        assertEquals(available, descriptor.getJSONObject("paths"));
        available = Map.of("VARIABLE:7", "person", "VARIABLE:8", "client");
        assertThrows(IllegalArgumentException.class, () -> bindings.open(Map.of(1L, descriptor), PROJECT));
    }
    @Test public void unsupportedRuntimeDependenciesBlockPublication() {
        for (String config : List.of("{\"requestFields\":[{\"validationRuleIds\":[4]}]}",
                "{\"steps\":[{\"type\":\"HTTP\",\"apiConfigId\":4}]}",
                "{\"requestFields\":[{\"value\":{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":999}}]}")) {
            api.setExecutionConfig(config); store(apiOwner,(JSONObject)JSON.toJSON(api),null);
            assertThrows(IllegalArgumentException.class,()->bindings.capture(1L,PROJECT));
        }
    }
}
