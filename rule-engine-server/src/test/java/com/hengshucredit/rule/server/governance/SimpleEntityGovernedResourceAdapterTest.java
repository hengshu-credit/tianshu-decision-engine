package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import lombok.Data;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

public class SimpleEntityGovernedResourceAdapterTest {

    @Test
    public void scriptFunctionIdIsNotMisclassifiedAsVariableId() {
        var adapter = adapter(new TestStore());
        var snapshot = ResourceSnapshot.ofJson("{\"scriptVarRefs\":["
                + "{\"varId\":161,\"refType\":\"FUNCTION\",\"refCode\":\"versioned\"}]}");
        var dependencies = adapter.collectDependencies(snapshot);
        Assert.assertEquals(1, dependencies.size());
        Assert.assertEquals("FUNCTION", dependencies.get(0).targetResourceType());
        Assert.assertEquals(Long.valueOf(161), dependencies.get(0).targetResourceId());
    }

    @Test
    public void deleteActionIsAppliedAsDisabledProjectionNotPhysicalDelete() {
        TestStore store = new TestStore();
        SampleResource current = new SampleResource();
        current.setId(7L);
        current.setName("old");
        current.setStatus(1);
        store.value = current;
        SimpleEntityGovernedResourceAdapter<SampleResource> adapter =
                adapter(store);
        ResourceSnapshot snapshot = adapter.normalizeDraft(
                ResourceSnapshot.ofJson(
                        "{\"id\":7,\"name\":\"old\",\"status\":1}"));

        AppliedResource applied = adapter.apply(
                new ApprovalApplyContext(12L, 7L, 2,
                        "DELETE", snapshot, "user", null));

        Assert.assertEquals(Long.valueOf(7L), applied.resourceId());
        Assert.assertEquals("DELETED", applied.effectiveStatus());
        Assert.assertEquals(Integer.valueOf(-1), store.value.getStatus());
        Assert.assertEquals(0, store.deleteCalls);
        Assert.assertEquals(1, store.updateCalls);
    }

    @Test
    public void restoreActionReactivatesEvenADeletedHistoricalSnapshot() {
        TestStore store = new TestStore();
        SampleResource current = new SampleResource();
        current.setId(7L);
        current.setName("deleted");
        current.setStatus(-1);
        store.value = current;
        SimpleEntityGovernedResourceAdapter<SampleResource> adapter =
                adapter(store);
        ResourceSnapshot deletedVersion = new ResourceSnapshot(
                "{\"id\":7,\"name\":\"restored\",\"status\":-1}",
                "DELETED", null, null);

        AppliedResource applied = adapter.apply(
                new ApprovalApplyContext(13L, 7L, 3,
                        "RESTORE", deletedVersion, "user", null));

        Assert.assertEquals("ACTIVE", applied.effectiveStatus());
        Assert.assertEquals(Integer.valueOf(1), store.value.getStatus());
        Assert.assertEquals("restored", store.value.getName());
    }

    @Test
    public void idBasedDependenciesUseExplicitRefType() {
        TestStore store = new TestStore();
        SimpleEntityGovernedResourceAdapter<SampleResource> adapter =
                adapter(store);
        ResourceSnapshot snapshot = adapter.normalizeDraft(
                ResourceSnapshot.ofJson(
                        "{\"name\":\"rule\",\"projectId\":3,"
                                + "\"varId\":9,\"refType\":\"MODEL\"}"));

        Assert.assertTrue(adapter.collectDependencies(snapshot).stream()
                .anyMatch(ref -> "PROJECT".equals(
                        ref.targetResourceType())
                        && Long.valueOf(3L).equals(
                        ref.targetResourceId())));
        Assert.assertTrue(adapter.collectDependencies(snapshot).stream()
                .anyMatch(ref -> "MODEL".equals(
                        ref.targetResourceType())
                        && Long.valueOf(9L).equals(
                        ref.targetResourceId())));
    }

    @Test
    public void normalizationKeepsBusinessNamesAndExcludesProjectToken() {
        TestStore store = new TestStore();
        SimpleEntityGovernedResourceAdapter<SampleResource> adapter =
                adapter(store);

        ResourceSnapshot snapshot = adapter.normalizeDraft(
                ResourceSnapshot.ofJson(
                        "{\"name\":\"sample\",\"projectName\":\"project-a\","
                                + "\"datasourceName\":\"source-a\","
                                + "\"accessToken\":\"must-not-enter-lifecycle\","
                                + "\"createTime\":\"2026-07-30T00:00:00\"}"));
        Map<String, Object> normalized = com.hengshucredit.rule.server
                .artifact.CanonicalJson.readMap(snapshot.snapshotJson());

        Assert.assertEquals("project-a", normalized.get("projectName"));
        Assert.assertEquals("source-a", normalized.get("datasourceName"));
        Assert.assertFalse(normalized.containsKey("accessToken"));
        Assert.assertFalse(normalized.containsKey("createTime"));
    }

    @Test
    public void dependenciesInsideJsonConfigurationAreResolvedById() {
        TestStore store = new TestStore();
        SimpleEntityGovernedResourceAdapter<SampleResource> adapter =
                adapter(store);
        ResourceSnapshot snapshot = adapter.normalizeDraft(
                ResourceSnapshot.ofJson(
                        "{\"name\":\"api-variable\","
                                + "\"sourceConfig\":\"{\\\"apiId\\\":11}\"}"));

        Assert.assertTrue(adapter.collectDependencies(snapshot).stream()
                .anyMatch(ref -> "EXTERNAL_API".equals(
                        ref.targetResourceType())
                        && Long.valueOf(11L).equals(
                        ref.targetResourceId())));
    }

    @Test
    public void variableSourceDependenciesDistinguishDatabaseListsAndReferences() {
        SimpleEntityGovernedResourceAdapter<SampleResource> adapter =
                adapter(new TestStore());
        ResourceSnapshot snapshot = adapter.normalizeDraft(
                ResourceSnapshot.ofJson(
                        "{\"name\":\"list-db-variable\","
                                + "\"sourceConfig\":\"{"
                                + "\\\"datasourceId\\\":21,"
                                + "\\\"sql\\\":\\\"select 1\\\","
                                + "\\\"listIds\\\":[31,32],"
                                + "\\\"queryOperands\\\":[{"
                                + "\\\"refId\\\":41,"
                                + "\\\"refType\\\":\\\"DATA_OBJECT\\\"}]}\"}"));

        java.util.List<ResourceDependencyRef> dependencies =
                adapter.collectDependencies(snapshot);

        Assert.assertTrue(dependencies.stream().anyMatch(ref ->
                "DATABASE".equals(ref.targetResourceType())
                        && Long.valueOf(21L).equals(ref.targetResourceId())));
        Assert.assertEquals(2, dependencies.stream().filter(ref ->
                "LIST_LIBRARY".equals(ref.targetResourceType())).count());
        Assert.assertTrue(dependencies.stream().anyMatch(ref ->
                "DATA_OBJECT".equals(ref.targetResourceType())
                        && Long.valueOf(41L).equals(ref.targetResourceId())));
        Assert.assertFalse(dependencies.stream().anyMatch(ref ->
                "EXTERNAL_DATASOURCE".equals(ref.targetResourceType())
                        && Long.valueOf(21L).equals(ref.targetResourceId())));
    }

    @Test
    public void aggregateRestoreReplacesRootAndChildrenWithoutLifecycleApply() {
        TestStore store = new TestStore();
        SampleResource draft = new SampleResource();
        draft.setId(7L);
        draft.setName("draft");
        draft.setStatus(1);
        store.value = draft;
        TestAggregateAdapter aggregate =
                new TestAggregateAdapter(adapter(store));

        aggregate.restore(7L, ResourceSnapshot.ofJson(
                "{\"id\":7,\"name\":\"effective\","
                        + "\"status\":1,\"childName\":\"effective-child\"}"));

        Assert.assertEquals("effective", store.value.getName());
        Assert.assertEquals("effective-child", aggregate.childName);
        Assert.assertEquals(1, store.updateCalls);
        Assert.assertEquals(0, aggregate.afterApplyCalls);
    }

    private SimpleEntityGovernedResourceAdapter<SampleResource> adapter(
            TestStore store) {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setActiveKeyId("test");
        properties.setMasterKeys(Map.of(
                "test",
                "test-governance-master-key-32-characters-long"));
        return new SimpleEntityGovernedResourceAdapter<>(
                "SAMPLE", SampleResource.class, store,
                SampleResource::getId, SampleResource::setId,
                SampleResource::getStatus,
                SampleResource::setStatus,
                Set.of("name"), Set.of(),
                new GovernanceSecretCodec(
                        new CredentialCipher(properties)));
    }

    private static class TestAggregateAdapter
            extends AggregateEntityGovernedResourceAdapter<SampleResource> {
        private String childName;
        private int afterApplyCalls;

        private TestAggregateAdapter(
                SimpleEntityGovernedResourceAdapter<SampleResource>
                        rootAdapter) {
            super(rootAdapter);
        }

        private void restore(Long resourceId,
                             ResourceSnapshot effectiveSnapshot) {
            restoreProjection(resourceId, effectiveSnapshot);
        }

        @Override
        protected void enrichSnapshot(
                Long resourceId, Map<String, Object> snapshot) {
            snapshot.put("childName", childName);
        }

        @Override
        protected void applyAggregate(
                Long resourceId, Map<String, Object> snapshot) {
            childName = String.valueOf(snapshot.get("childName"));
        }

        @Override
        protected AppliedResource afterAggregateApplied(
                ApprovalApplyContext context, AppliedResource applied) {
            afterApplyCalls++;
            return applied;
        }
    }

    @Data
    public static class SampleResource {
        private Long id;
        private Long projectId;
        private Long varId;
        private String refType;
        private String name;
        private String projectName;
        private String datasourceName;
        private String accessToken;
        private String sourceConfig;
        private Integer status;
    }

    private static class TestStore implements
            SimpleEntityGovernedResourceAdapter.EntityStore<SampleResource> {
        private SampleResource value;
        private int updateCalls;
        private int deleteCalls;

        @Override
        public SampleResource load(Long id) {
            return value;
        }

        @Override
        public void insert(SampleResource entity) {
            entity.setId(8L);
            value = entity;
        }

        @Override
        public void update(SampleResource entity) {
            updateCalls++;
            value = entity;
        }
    }
}
