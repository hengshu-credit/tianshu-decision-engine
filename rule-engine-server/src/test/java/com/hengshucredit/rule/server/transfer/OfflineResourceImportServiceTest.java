package com.hengshucredit.rule.server.transfer;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 项目级离线导入必须按显式项目绑定解析目标 ID，不能按名称猜测。 */
public class OfflineResourceImportServiceTest {
    @Test
    public void ruleVariableWithoutTypeUsesRemappedDeclarationOnceForSchemaAndDesigner() {
        var variable = new OfflineResourceTransferServiceTest.StubAdapter("VARIABLE", Map.of("varType", "LIST"), List.of());
        var service = new OfflineResourceImportService();
        ReflectionTestUtils.setField(service, "adapterRegistry",
                new com.hengshucredit.rule.server.governance.GovernedResourceAdapterRegistry(List.of(variable)));
        var config = Map.<String,Object>of(
                "inputFieldsJson", List.of(Map.of("refType", "VARIABLE", "varId", 9, "fieldType", "?")),
                "outputFieldsJson", List.of(Map.of("refType", "VARIABLE", "varId", 9, "fieldType", "OBJECT")),
                "content", Map.of("modelJson", "{\"operand\":{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":9}}"));
        var paths = List.of("/inputFieldsJson/0/varId", "/outputFieldsJson/0/varId", "/content/modelJson/@json/operand/refId");
        var rule = new TransferBundle.Resource("RULE:1", config,
                paths.stream().map(path -> new TransferBundle.Reference(path, "VARIABLE:9", null)).toList(), List.of());
        var target = TransferBundle.copyConfiguration(config);
        ReflectionTestUtils.invokeMethod(service, "rewriteReferences", target, rule, Map.of(), Map.of("VARIABLE:9", 603L), Map.of(), Map.of());
        for (var path : paths) assertEquals(603L, ((Number)TransferJsonPath.read(target, path)).longValue());
        assertEquals("LIST", TransferJsonPath.read(target, "/inputFieldsJson/0/fieldType"));
        assertEquals("OBJECT", TransferJsonPath.read(target, "/outputFieldsJson/0/fieldType"));
        assertEquals("LIST", TransferJsonPath.read(target, "/content/modelJson/@json/operand/valueType"));
        assertEquals(List.of(603L), variable.loaded);
    }
    @Test
    public void unavailableAttachedFunctionStopsImportBeforeCreatingTheProject() {
        var service = new OfflineResourceImportService();
        ReflectionTestUtils.setField(service, "functionLibraries", new TransferFunctionLibraries("no-deployed-jars"));
        var fn = new TransferBundle.Resource("FUNCTION:1", Map.of("implType","JAVA","implClass","example.Uninstalled"),List.of(),List.of());
        var bundle = new TransferBundle(List.of(fn.key()),List.of(fn),List.of());
        byte[] bytes = { 1, 2, 3 };
        String sha = com.hengshucredit.rule.server.artifact.Sha256Digests.bytes(bytes);
        var archive = new TransferBundleCodec().encode(bundle, Map.of(fn.key(), new TransferFunctionLibraries.Library(sha, bytes)));
        var options = new TransferImportOptions(null, "PROJECT", "REUSE", "REUSE", null, true, "NEW_PROJ", "测试项目", Map.of());
        var error = org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> service.apply(archive, options, "operator"));
        assertTrue(error.getMessage(), error.getMessage().contains("function-jars/" + sha + ".jar"));
    }

    @Test
    public void sourceProjectBindingWinsOverSingleTargetFallback() {
        OfflineResourceImportService service = new OfflineResourceImportService();
        TransferImportOptions options = new TransferImportOptions(
                99L, "PROJECT", "REUSE", "SUFFIX", "_imported", false,
                null, null, Map.of("7", 42L));
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("projectId", 7L);

        Long target = ReflectionTestUtils.invokeMethod(service, "resolveTargetProjectId",
                configuration, options, 99L);

        assertEquals(Long.valueOf(42L), target);
    }

    @Test(expected = IllegalArgumentException.class)
    public void projectImportWithoutExplicitBindingIsRejected() {
        OfflineResourceImportService service = new OfflineResourceImportService();
        TransferImportOptions options = new TransferImportOptions(
                null, "PROJECT", "REUSE", "SUFFIX", "_imported", false,
                null, null, Map.of());
        ReflectionTestUtils.invokeMethod(service, "resolveTargetProjectId",
                Map.of("projectId", 7L), options, null);
    }

    @Test
    public void fixedVersionCycleIsOrderedByTargetRuleBeforeCallerRule() {
        OfflineResourceImportService service = new OfflineResourceImportService();
        TransferBundle.Resource caller = new TransferBundle.Resource(
                "RULE:1", Map.of("ruleCode", "caller"),
                List.of(new TransferBundle.Reference("/fixedVersion", "RULE_VERSION:9", null)), List.of());
        TransferBundle.Resource binding = new TransferBundle.Resource(
                "RULE_VERSION:9", Map.of("definitionId", 2L, "versionNo", 1),
                List.of(new TransferBundle.Reference("/definitionId", "RULE:2", null)), List.of());
        TransferBundle.Resource target = new TransferBundle.Resource(
                "RULE:2", Map.of("ruleCode", "target"), List.of(), List.of());

        @SuppressWarnings("unchecked")
        List<TransferBundle.Resource> ordered = ReflectionTestUtils.invokeMethod(
                service, "topologicalOrder", List.of(caller, binding, target));

        assertEquals(List.of("RULE:2", "RULE_VERSION:9", "RULE:1"),
                ordered.stream().map(TransferBundle.Resource::key).toList());
    }

    @Test
    public void selectedImportAllowsAnExternalReferenceToBeResolvedByThePanel() {
        OfflineResourceImportService service = new OfflineResourceImportService();
        TransferBundle.Resource selected = new TransferBundle.Resource(
                "RULE:1", Map.of("ruleCode", "caller"),
                List.of(new TransferBundle.Reference("/dependencyId", "VARIABLE:9", null,
                        true, "customer_id", "客户编号")), List.of());

        @SuppressWarnings("unchecked")
        List<TransferBundle.Resource> ordered = ReflectionTestUtils.invokeMethod(
                service, "topologicalOrder", List.of(selected));

        assertEquals(List.of("RULE:1"), ordered.stream().map(TransferBundle.Resource::key).toList());
        assertTrue(ordered.get(0).references().get(0).external());
    }
}
