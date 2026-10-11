package com.hengshucredit.rule.server.transfer;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class TransferVersionReferencesTest {
    @Test public void versionPathsAreDiscoveredInsideJsonButNeverInBusinessLiterals() {
        String operand = "{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":3,\"versionMode\":\"FIXED\",\"versionSource\":\"BUSINESS_VERSION\",\"versionId\":8}";
        var found = TransferVersionReferences.scan(Map.of("content", Map.of("modelJson", operand)));
        assertEquals(1, found.size()); assertEquals("/content/modelJson/@json", found.get(0).path());
        assertTrue(TransferVersionReferences.scan(Map.of("defaultValue", operand)).isEmpty());
        assertTrue(TransferVersionReferences.scan(Map.of("kind", "LITERAL", "value", operand)).isEmpty());
        assertTrue(TransferVersionReferences.scan(Map.of("content", Map.of("modelJson", operand.replace("FIXED", "LATEST")))).isEmpty());
    }

    @Test public void rulesKeepTheirCompleteOperandsWhenBothIdentitiesAreRebound() {
        var config = com.hengshucredit.rule.server.artifact.CanonicalJson.readMap("{\"scope\":\"GLOBAL\",\"content\":{\"modelJson\":\"{\\\"leftVarId\\\":3,\\\"leftOperand\\\":{\\\"kind\\\":\\\"REFERENCE\\\",\\\"refType\\\":\\\"VARIABLE\\\",\\\"refId\\\":3,\\\"versionMode\\\":\\\"FIXED\\\",\\\"versionSource\\\":\\\"BUSINESS_VERSION\\\",\\\"versionId\\\":8}}\"}}");
        var source = new TransferBundle.Resource("RULE:1", config, java.util.List.of(), java.util.List.of());
        var reference = TransferVersionReferences.scan(config).get(0);
        var options = new TransferImportOptions(null, "GLOBAL", "REUSE", "REUSE", "", false, null, null, Map.of(), false, java.util.List.of(), Map.of(), Map.of(), Map.of(),
                Map.of(reference.key(source.key()), new TransferVersionBinding(30L, 80L, null, 80L)));
        var service = new TransferVersionService() {
            @Override protected Map<String, Object> target(Map<String, Object> from, TransferVersionBinding binding, String scope, Long project) {
                assertNotNull(binding); return Map.of("refId", binding.resourceId(), "version", 2, "snapshotId", 80L);
            }
        };
        var mappings = service.rewrite(config, source, options, Map.of(), 0L);
        assertEquals(1, mappings.size());
        assertEquals(30L, ((Number) TransferJsonPath.read(config, "/content/modelJson/@json/leftVarId")).longValue());
        assertEquals("REFERENCE", TransferJsonPath.read(config, "/content/modelJson/@json/leftOperand/kind"));
        assertEquals(80L, ((Number) TransferJsonPath.read(config, "/content/modelJson/@json/leftOperand/versionId")).longValue());
        assertEquals(3L, ((Number) TransferJsonPath.read(source.configuration(), "/content/modelJson/@json/leftVarId")).longValue());
    }

    @Test public void scriptMappingChangesOnlyAstIdArgumentsAndPreservesLatestPolicy() {
        String script = "// 😀 executeRuleVersionById('999','999')\nreturn executeRuleVersionById('3','8') + executeRuleById('4');";
        var config = new java.util.LinkedHashMap<String, Object>(Map.of("script", script));
        var references = TransferVersionReferences.scan(config); assertEquals(2, references.size());
        var bindings = new java.util.LinkedHashMap<String, TransferVersionBinding>();
        references.forEach(ref -> bindings.put(ref.key("RULE:1"), new TransferVersionBinding(ref.refId() + 100, 80L, null, 80L)));
        var options = new TransferImportOptions(null, "GLOBAL", "REUSE", "REUSE", "", false, null, null, Map.of(), false, java.util.List.of(), Map.of(), Map.of(), Map.of(), bindings);
        new TransferVersionService() {
            @Override protected Map<String, Object> target(Map<String, Object> from, TransferVersionBinding binding, String scope, Long project) { return Map.of("refId", binding.resourceId()); }
        }.rewrite(config, new TransferBundle.Resource("RULE:1", config, java.util.List.of(), java.util.List.of()), options, Map.of(), 0L);
        assertEquals("// 😀 executeRuleVersionById('999','999')\nreturn executeRuleVersionById(\"103\",\"80\") + executeRuleById(\"104\");", config.get("script"));
    }

    @Test public void tamperedDescriptorsAndDynamicIdsCannotBypassExplicitMapping() {
        var config = Map.<String, Object>of("script", "return resourceVersionValue('VARIABLE', '3', 'BUSINESS_VERSION:8', '');");
        var reference = TransferVersionReferences.scan(config).get(0);
        var metadata = new java.util.LinkedHashMap<>(reference.descriptor()); metadata.put("versionId", 9L);
        var source = new TransferBundle.Resource("RULE:1", config, java.util.List.of(), java.util.List.of(), java.util.List.of(metadata));
        assertThrows(IllegalArgumentException.class, () -> TransferVersionReferences.descriptors(source));
        assertThrows(IllegalArgumentException.class, () -> TransferVersionReferences.scan(Map.of("script", "return executeRuleVersionById(ruleId, '8');")));
    }
}
