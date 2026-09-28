package com.hengshucredit.rule.server.transfer;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.governance.*;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class OfflineResourceTransferServiceTest {
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
