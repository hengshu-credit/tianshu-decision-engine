package com.hengshucredit.rule.server.transfer;

import org.junit.BeforeClass;
import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class OfflineResourceReuseTest {
    private static final TransferImportOptions REUSE = new TransferImportOptions(null, "GLOBAL", "REUSE", "REUSE", "_copy", false, null, null, Map.of());

    @BeforeClass public static void metadata() { OfflineProjectBindingTest.metadata(); }

    @Test public void sameCodeAndTypeVariableCanBeReusedWithDifferentLabel() {
        var f = new OfflineProjectBindingTest.Fixture();
        f.seed("VARIABLE", 8L, 0L, Map.of("varCode", "score", "varType", "NUMBER", "varLabel", "目标"));
        var result = f.service.apply(bundle("VARIABLE", Map.of("varCode", "score", "varType", "NUMBER", "varLabel", "源")), REUSE, "tester");
        assertEquals(8L, ((Map<?, ?>) result.get("resourceIdMapping")).get("VARIABLE:1"));
        assertTrue(f.drafts.isEmpty());
    }

    @Test public void variableTypeConflictCannotSilentlyReuse() {
        var f = new OfflineProjectBindingTest.Fixture();
        f.seed("VARIABLE", 8L, 0L, Map.of("varCode", "score", "varType", "STRING"));
        var error = assertThrows(IllegalArgumentException.class, () -> f.service.apply(
                bundle("VARIABLE", Map.of("varCode", "score", "varType", "NUMBER")), REUSE, "tester"));
        assertTrue(error.getMessage().contains("类型"));
        assertTrue(f.drafts.isEmpty());
    }

    @Test public void differentFunctionImplementationCannotBeReused() {
        var f = new OfflineProjectBindingTest.Fixture();
        f.seed("FUNCTION", 8L, 0L, Map.of("funcCode", "fn", "implScript", "return 2;"));
        byte[] bytes = bundle("FUNCTION", Map.of("funcCode", "fn", "implScript", "return 1;"));
        assertThrows(IllegalArgumentException.class, () -> f.service.apply(bytes, REUSE, "tester"));
        TransferConflict conflict = (TransferConflict) ((List<?>) f.previewService.preview(bytes, REUSE).get("conflicts")).get(0);
        assertEquals("CONFIG_CONFLICT", conflict.conflictType());
        assertEquals("SUFFIX", conflict.recommendedAction());
        assertTrue(f.drafts.isEmpty());
    }

    @Test public void datasourceReferenceAndBusinessIdDifferencesAreRealConflicts() {
        for (Map<String, Object> changed : List.of(Map.<String, Object>of("datasourceId", 9), Map.<String, Object>of("bodyTemplate", "{\"id\":9}"))) {
            var f = new OfflineProjectBindingTest.Fixture();
            var existing = new java.util.LinkedHashMap<String, Object>(Map.of("apiCode", "api", "datasourceId", 8, "bodyTemplate", "{\"id\":8}"));
            f.seed("EXTERNAL_API", 8L, 0L, existing);
            var incoming = new java.util.LinkedHashMap<>(existing); incoming.putAll(changed);
            byte[] bytes = bundle("EXTERNAL_API", incoming);
            assertThrows(IllegalArgumentException.class, () -> f.service.apply(bytes, REUSE, "tester"));
            TransferConflict conflict = (TransferConflict) ((List<?>) f.previewService.preview(bytes, REUSE).get("conflicts")).get(0);
            assertEquals("CONFIG_CONFLICT", conflict.conflictType());
        }
    }

    @Test public void identityAndAuditMetadataDoNotPreventSameConfigurationReuse() {
        var f = new OfflineProjectBindingTest.Fixture();
        f.seed("FUNCTION", 8L, 0L, Map.of("id", 8, "projectId", 0, "scope", "GLOBAL", "funcCode", "fn", "implScript", "return 1;", "createBy", "target"));
        var result = f.service.apply(bundle("FUNCTION", Map.of("id", 1, "projectId", 7, "scope", "PROJECT", "funcCode", "fn", "implScript", "return 1;", "createBy", "source")), REUSE, "tester");
        assertEquals(8L, ((Map<?, ?>) result.get("resourceIdMapping")).get("FUNCTION:1"));
        assertTrue(f.drafts.isEmpty());
    }

    @Test public void disabledResourceIsNotAReusableDependency() {
        var f = new OfflineProjectBindingTest.Fixture();
        f.seed("FUNCTION", 8L, 0L, Map.of("funcCode", "fn"));
        f.resources.get(0).setEffectiveStatus("DISABLED");
        assertThrows(IllegalArgumentException.class, () -> f.service.apply(bundle("FUNCTION", Map.of("funcCode", "fn")), REUSE, "tester"));
        assertTrue(f.drafts.isEmpty());
    }

    @Test public void referenceIdsAreRemappedBeforeComparingConfigurationInPreviewAndApply() {
        var f = new OfflineProjectBindingTest.Fixture();
        f.seed("VARIABLE", 8L, 0L, Map.of("varCode", "score", "varType", "NUMBER"));
        f.seed("FUNCTION", 9L, 0L, Map.of("funcCode", "fn", "paramsJson", "[ { \"varId\":8 } ]"));
        byte[] bytes = new TransferBundleCodec().encode(new TransferBundle(List.of("FUNCTION:2"), List.of(
                new TransferBundle.Resource("FUNCTION:2", Map.of("funcCode", "fn", "paramsJson", "[{\"varId\":1}]"),
                        List.of(new TransferBundle.Reference("/paramsJson/@json/0/varId", "VARIABLE:1", null)), List.of()),
                new TransferBundle.Resource("VARIABLE:1", Map.of("varCode", "score", "varType", "NUMBER"), List.of(), List.of())), List.of()));
        List<?> conflicts = (List<?>) f.previewService.preview(bytes, REUSE).get("conflicts");
        assertEquals("IDENTICAL", ((TransferConflict) conflicts.get(1)).conflictType());
        var result = f.service.apply(bytes, REUSE, "tester");
        assertEquals(9L, ((Map<?, ?>) result.get("resourceIdMapping")).get("FUNCTION:2"));
        assertTrue(f.drafts.isEmpty());

        var suffixVariable = new TransferImportOptions(null, "GLOBAL", "SUFFIX", "REUSE", "_copy", false, null, null, Map.of());
        conflicts = (List<?>) f.previewService.preview(bytes, suffixVariable).get("conflicts");
        assertEquals("CONFIG_CONFLICT", ((TransferConflict) conflicts.get(1)).conflictType());
        assertEquals("SUFFIX", ((TransferConflict) conflicts.get(1)).recommendedAction());
    }

    @Test public void equalObjectsWithDifferentIdsMapDuplicateChildNamesByStructure() {
        var f = new OfflineProjectBindingTest.Fixture();
        Map<String, Object> source = objectFields(10);
        Map<String, Object> target = objectFields(20);
        f.seed("DATA_OBJECT", 8L, 0L, target);
        f.seed("FUNCTION", 9L, 0L, Map.of("funcCode", "fn", "paramsJson", "[{\"varId\":21}]"));
        byte[] bytes = new TransferBundleCodec().encode(new TransferBundle(List.of("FUNCTION:2"), List.of(
                new TransferBundle.Resource("FUNCTION:2", Map.of("funcCode", "fn", "paramsJson", "[{\"varId\":11}]"),
                        List.of(new TransferBundle.Reference("/paramsJson/@json/0/varId", "DATA_OBJECT:1", "/fields/1/id")), List.of()),
                new TransferBundle.Resource("DATA_OBJECT:1", source, List.of(), List.of())), List.of()));
        List<?> conflicts = (List<?>) f.previewService.preview(bytes, REUSE).get("conflicts");
        assertEquals("IDENTICAL", ((TransferConflict) conflicts.get(1)).conflictType());
        var result = f.service.apply(bytes, REUSE, "tester");
        assertEquals(9L, ((Map<?, ?>) result.get("resourceIdMapping")).get("FUNCTION:2"));
        assertTrue(f.drafts.isEmpty());
    }

    @Test public void comparisonPreservesBusinessIdsInsideObjects() {
        assertEquals("CONFIG_CONFLICT", TransferResourceComparison.conflict(TransferResourceType.EXTERNAL_API,
                Map.of("body", Map.of("id", 8, "projectId", 1)), Map.of("body", Map.of("id", 9, "projectId", 1)), "ACTIVE"));
    }

    @Test public void constantCannotBeReusedAsOrdinaryInputWithSameType() {
        var f = new OfflineProjectBindingTest.Fixture();
        f.seed("VARIABLE", 8L, 0L, Map.of("varCode", "score", "varType", "NUMBER", "varSource", "CONSTANT"));
        assertThrows(IllegalArgumentException.class, () -> f.service.apply(bundle("VARIABLE",
                Map.of("varCode", "score", "varType", "NUMBER", "varSource", "INPUT")), REUSE, "tester"));
    }

    @Test public void configuredSecretsAreNotProofOfMatchingSecretContent() {
        var secretConfig = Map.<String, Object>of("datasourceCode", "db", "_secretConfigured", Map.of("/password", true));
        assertEquals("ENVIRONMENT_REVIEW_REQUIRED", TransferResourceComparison.conflict(TransferResourceType.DATABASE,
                secretConfig, secretConfig, "ACTIVE"));
    }

    @Test public void modelBinaryIsPartOfTheComparedConfiguration() {
        var left = Map.<String, Object>of("modelCode", "model", "modelContent", "SOURCE_BYTES");
        assertEquals("IDENTICAL", TransferResourceComparison.conflict(TransferResourceType.MODEL, left, left, "ACTIVE"));
        assertEquals("CONFIG_CONFLICT", TransferResourceComparison.conflict(TransferResourceType.MODEL, left,
                Map.of("modelCode", "model", "modelContent", "OTHER_BYTES"), "ACTIVE"));
    }

    private static Map<String, Object> objectFields(long firstId) {
        return Map.of("objectCode", "request", "fields", List.of(
                Map.of("id", firstId, "varCode", "father", "varType", "OBJECT"),
                Map.of("id", firstId + 1, "parentFieldId", firstId, "varCode", "age", "varType", "NUMBER"),
                Map.of("id", firstId + 2, "varCode", "mother", "varType", "OBJECT"),
                Map.of("id", firstId + 3, "parentFieldId", firstId + 2, "varCode", "age", "varType", "NUMBER")));
    }

    private static byte[] bundle(String type, Map<String, Object> config) {
        return new TransferBundleCodec().encode(new TransferBundle(List.of(type + ":1"),
                List.of(new TransferBundle.Resource(type + ":1", config, List.of(), List.of())), List.of()));
    }
}
