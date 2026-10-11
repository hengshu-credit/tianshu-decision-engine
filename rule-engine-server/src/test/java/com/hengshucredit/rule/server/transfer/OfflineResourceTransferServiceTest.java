package com.hengshucredit.rule.server.transfer;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.governance.*;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;
import com.hengshucredit.rule.server.mapper.TransferProjectResourceMapper;

public class OfflineResourceTransferServiceTest {
    @Test public void latestExportUsesProductionSnapshotEvenWhenCompatibilityProjectionContainsAnotherDraft() {
        var adapter = new StubAdapter("RULE", Map.of("id", 9, "ruleCode", "draft"), List.of());
        var service = service(adapter);
        var production = new com.hengshucredit.rule.model.entity.GovernedResource(); production.setResourceType("RULE"); production.setResourceId(9L); production.setEffectiveVersionId(90L); production.setEffectiveStatus("ACTIVE");
        var version = new com.hengshucredit.rule.model.entity.GovernedResourceVersion(); version.setId(90L); version.setResourceType("RULE"); version.setResourceId(9L); version.setEffectiveStatus("ACTIVE");
        version.setSnapshotJson("{\"id\":9,\"ruleCode\":\"production\"}"); version.setSnapshotDigest(com.hengshucredit.rule.server.artifact.Sha256Digests.text(version.getSnapshotJson()));
        ReflectionTestUtils.setField(service, "governedResourceMapper", java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hengshucredit.rule.server.mapper.GovernedResourceMapper.class}, (p, m, a) -> production));
        ReflectionTestUtils.setField(service, "governedResourceVersionMapper", java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper.class}, (p, m, a) -> version));
        var bundle = new TransferBundleCodec().decode(service.export(List.of(new TransferRootRequest("RULE", 9L, "LATEST", null)))).bundle();
        assertEquals("production", bundle.resources().get(0).configuration().get("ruleCode"));
    }
    @Test public void fixedHistoricalFieldExportDoesNotLookUpTheCurrentEnvironmentFieldOrReplaceItsOperand() {
        var config = Map.<String, Object>of("id", 9, "ruleCode", "fixed", "content", Map.of("modelJson",
                "{\"leftVarId\":42,\"leftOperand\":{\"kind\":\"REFERENCE\",\"refType\":\"DATA_OBJECT\",\"refId\":42,\"versionId\":8,\"versionMode\":\"FIXED\",\"versionSource\":\"BUSINESS_VERSION\"}}"));
        var adapter = new StubAdapter("RULE", config, List.of(
                new ResourceDependencyRef("DATA_OBJECT", 42L, "DATA_OBJECT", "$.content.modelJson[json].leftVarId", "REFERENCES", true),
                new ResourceDependencyRef("DATA_OBJECT", 42L, "DATA_OBJECT", "$.content.modelJson[json].leftOperand.refId", "REFERENCES", true)));
        var service = service(adapter);
        ReflectionTestUtils.setField(service, "versionTransfers", new TransferVersionService() {
            @Override public List<Map<String, Object>> describe(Object value) { return TransferVersionReferences.scan(value).stream().map(TransferVersionReferences.Reference::descriptor).toList(); }
        });
        var bundle = new TransferBundleCodec().decode(service.export(List.of(new TransferRootRequest("RULE", 9L)))).bundle();
        assertEquals(1, bundle.resources().size()); assertTrue(bundle.resources().get(0).references().isEmpty());
        assertEquals(1, bundle.resources().get(0).versionReferences().size());
        assertEquals("REFERENCE", TransferJsonPath.read(bundle.resources().get(0).configuration(), "/content/modelJson/@json/leftOperand/kind"));
    }
    @Test
    public void projectExportIncludesMembersAndTheirTransitiveUpstreamWithoutOwnershipCycle() {
        var project = new StubAdapter("PROJECT", Map.of("id", 3, "projectCode", "risk"), List.of());
        var variable = new StubAdapter("VARIABLE", Map.of("id", 1, "projectId", 3,
                "varCode", "risk", "sourceConfig", "{\"listId\":7}"), List.of(
                new ResourceDependencyRef("PROJECT", 3L, "PROJECT", "$.projectId", "BELONGS_TO", true),
                new ResourceDependencyRef("LIST_LIBRARY", 7L, "LIST_LIBRARY", "$.sourceConfig[json].listId", "REFERENCES", true)));
        var list = new StubAdapter("LIST_LIBRARY", Map.of("id", 7, "listCode", "shared_list"), List.of());
        var service = service(project, variable, list);
        TransferProjectResourceMapper members = projectId -> {
            assertEquals(Long.valueOf(3), projectId);
            return List.of(new TransferRootRequest("VARIABLE", 1L));
        };
        ReflectionTestUtils.setField(service, "projectResourceMapper", members);

        var bundle = new TransferBundleCodec().decode(service.export(
                List.of(new TransferRootRequest("PROJECT", 3L)))).bundle();

        assertEquals(List.of("PROJECT:3"), bundle.roots());
        assertEquals(java.util.Set.of("PROJECT:3", "VARIABLE:1", "LIST_LIBRARY:7"),
                bundle.resources().stream().map(TransferBundle.Resource::key).collect(java.util.stream.Collectors.toSet()));
        var ordered = OfflineResourceImportService.topologicalOrder(bundle.resources());
        assertEquals("VARIABLE:1", ordered.get(ordered.size() - 1).key());
        assertTrue(bundle.resources().stream().flatMap(item -> item.references().stream()).noneMatch(TransferBundle.Reference::external));
    }

    @Test
    public void upstreamProjectDoesNotExpandUnrelatedSiblingsAndSelectedOnlyProjectStaysSingle() {
        var project = new StubAdapter("PROJECT", Map.of("id", 3, "projectCode", "risk"), List.of());
        var variable = new StubAdapter("VARIABLE", Map.of("id", 1, "projectId", 3, "varCode", "risk"), List.of(
                new ResourceDependencyRef("PROJECT", 3L, "PROJECT", "$.projectId", "BELONGS_TO", true)));
        var service = service(project, variable);
        TransferProjectResourceMapper members = projectId -> { throw new AssertionError("不得扩展未选中的项目成员"); };
        ReflectionTestUtils.setField(service, "projectResourceMapper", members);
        assertEquals(2, new TransferBundleCodec().decode(service.export(
                List.of(new TransferRootRequest("VARIABLE", 1L)))).bundle().resources().size());
        assertEquals(1, new TransferBundleCodec().decode(service.export(
                List.of(new TransferRootRequest("PROJECT", 3L)), false)).bundle().resources().size());
    }

    @Test
    public void sourceConfigJsonAndNestedListReferencesCanBeExported() {
        var adapter = new StubAdapter("VARIABLE",
                Map.of("id", 1, "varCode", "amount", "sourceConfig", "{\"listIds\":[7,8]}"),
                List.of(new ResourceDependencyRef("LIST_LIBRARY", 7L, "LIST_LIBRARY", "$.sourceConfig[json].listIds[0]", "REFERENCES", true),
                        new ResourceDependencyRef("LIST_LIBRARY", 8L, "LIST_LIBRARY", "$.sourceConfig[json].listIds[1]", "REFERENCES", true)));
        var lists = new StubAdapter("LIST_LIBRARY", Map.of("listCode", "list", "listType", "BLACK"), List.of());
        var service = service(adapter, lists);
        var bundle = new TransferBundleCodec().decode(service.export(List.of(new TransferRootRequest("VARIABLE", 1L)))).bundle();
        assertEquals(3, bundle.resources().size());
        var variable = bundle.resources().stream().filter(r -> r.key().equals("VARIABLE:1")).findFirst().orElseThrow();
        assertEquals("/sourceConfig/@json/listIds/0", variable.references().get(0).path());
        assertEquals(List.of(7L, 8L), lists.loaded);
    }

    @Test
    public void listRecordBatchCannotBeRequestedAsARootResource() {
        assertThrows(IllegalArgumentException.class, () -> new TransferRootRequest("LIST_RECORD_BATCH", 1L).key());
    }

    @Test
    public void fieldReferenceMustKeepTheChildIdentityInsteadOfReplacingItWithObjectId() {
        var objects = new StubAdapter("DATA_OBJECT", Map.of("id", 6, "objectCode", "Customer",
                "fields", List.of(Map.of("id", 42, "varCode", "age", "varType", "INTEGER"))), List.of());
        var rules = new StubAdapter("RULE", Map.of("id", 9, "ruleCode", "r",
                "inputFieldsJson", List.of(Map.of("refType", "DATA_OBJECT", "varId", 42))),
                List.of(new ResourceDependencyRef("DATA_OBJECT", 6L, "DATA_OBJECT",
                        "$.inputFieldsJson[0].varId", "REFERENCES", true)));
        var bundle = new TransferBundleCodec().decode(service(rules, objects).export(
                List.of(new TransferRootRequest("RULE", 9L)))).bundle();
        var rule = bundle.resources().stream().filter(r -> r.key().equals("RULE:9")).findFirst().orElseThrow();
        assertEquals("/fields/0/id", rule.references().get(0).childPath());
    }

    @Test
    public void stringFieldIdsAreRewrittenToTargetFieldsAndObjectIdsRemainRootReferences() {
        var objects = new StubAdapter("DATA_OBJECT", Map.of("id", 6, "objectCode", "Customer",
                "fields", List.of(Map.of("id", 6, "varCode", "age", "varType", "INTEGER"))), List.of());
        var rules = new StubAdapter("RULE", Map.of("id", 9, "ruleCode", "r", "requestObjectId", 6,
                "content", Map.of("modelJson", "{\"condition\":{\"_varId\":\"6\",\"_refType\":\"DATA_OBJECT\"}}")), List.of(
                new ResourceDependencyRef("DATA_OBJECT", 6L, "DATA_OBJECT", "$.requestObjectId", "REFERENCES", true),
                new ResourceDependencyRef("DATA_OBJECT", 6L, "DATA_OBJECT", "$.content.modelJson[json].condition._varId", "REFERENCES", true)));
        var bundle = new TransferBundleCodec().decode(service(rules, objects).export(
                List.of(new TransferRootRequest("RULE", 9L)))).bundle();
        var rule = bundle.resources().stream().filter(r -> r.key().equals("RULE:9")).findFirst().orElseThrow();
        assertNull(rule.references().get(0).childPath());
        assertEquals("/fields/0/id", rule.references().get(1).childPath());
        var configuration = new java.util.LinkedHashMap<>(rule.configuration());
        ReflectionTestUtils.invokeMethod(new OfflineResourceImportService(), "rewriteReferences", configuration,
                rule, TransferSelection.index(bundle.resources()), Map.of("DATA_OBJECT:6", 60L),
                Map.of("DATA_OBJECT:6", Map.of(6L, 600L)), Map.of());
        assertEquals(60L, configuration.get("requestObjectId"));
        assertEquals(600L, ((Number) TransferJsonPath.read(configuration,
                "/content/modelJson/@json/condition/_varId")).longValue());
    }

    @Test
    public void modelContentIsKeptInTheOfflineConfigurationPackage() {
        var model = new StubAdapter("MODEL", Map.of("id", 4, "modelCode", "fraud_model",
                "modelFormat", "ONNX", "modelContent", "BASE64_MODEL_BYTES"), List.of());
        var bundle = new TransferBundleCodec().decode(service(model).export(
                List.of(new TransferRootRequest("MODEL", 4L)))).bundle();
        Map<String, Object> configuration = bundle.resources().get(0).configuration();
        assertEquals("BASE64_MODEL_BYTES", configuration.get("modelContent"));
    }

    @Test
    public void selectedOnlyExportLeavesUpstreamAsExternalAssociation() {
        var adapter = new StubAdapter("VARIABLE",
                Map.of("id", 1, "varCode", "amount", "sourceConfig", "{\"listIds\":[7]}"),
                List.of(new ResourceDependencyRef("LIST_LIBRARY", 7L, "LIST_LIBRARY",
                        "$.sourceConfig[json].listIds[0]", "REFERENCES", true)));
        var list = new StubAdapter("LIST_LIBRARY", Map.of("id", 7, "listCode", "credit_list"), List.of());

        var bundle = new TransferBundleCodec().decode(service(adapter, list).export(
                List.of(new TransferRootRequest("VARIABLE", 1L)), false)).bundle();

        assertEquals(1, bundle.resources().size());
        assertTrue(bundle.resources().get(0).references().get(0).external());
    }

    private OfflineResourceTransferService service(StubAdapter... adapters) {
        var service = new OfflineResourceTransferService();
        ReflectionTestUtils.setField(service, "adapterRegistry", new GovernedResourceAdapterRegistry(List.of(adapters)));
        return service;
    }

    private static class StubAdapter implements GovernedResourceAdapter {
        final String type;
        final Map<String, Object> config;
        final List<ResourceDependencyRef> dependencies;
        final List<Long> loaded = new ArrayList<>();
        StubAdapter(String type, Map<String, Object> config, List<ResourceDependencyRef> dependencies) {
            this.type = type; this.config = config; this.dependencies = dependencies;
        }
        public String resourceType() { return type; }
        public ResourceSnapshot loadEffective(Long id) { loaded.add(id); return ResourceSnapshot.ofJson(CanonicalJson.write(config)); }
        public ResourceSnapshot normalizeDraft(ResourceSnapshot draft) { return draft; }
        public List<GovernanceIssue> validate(ResourceSnapshot draft) { return List.of(); }
        public List<ResourceDependencyRef> collectDependencies(ResourceSnapshot draft) { return dependencies; }
        public ResourceDiff diff(ResourceSnapshot a, ResourceSnapshot b) { throw new AssertionError("export must be read only"); }
        public AppliedResource apply(ApprovalApplyContext context) { throw new AssertionError("export must not apply"); }
    }
}
