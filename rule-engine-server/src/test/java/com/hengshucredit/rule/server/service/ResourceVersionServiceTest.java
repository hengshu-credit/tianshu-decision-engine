package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.core.engine.ResourceVersionCall;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.governance.GovernanceSecretCodec;
import com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ResourceVersionServiceTest {
    @Test public void deletedBusinessFunctionCannotBeInvokedThroughItsNativeHistoryAlias() {
        var shared = version("FUNCTION", "{\"id\":11,\"scope\":\"GLOBAL\",\"status\":1}"); shared.setBusinessVersion(3); shared.setDeleted(true);
        var nativeVersion = new com.hengshucredit.rule.model.entity.RuleFunctionVersion(); nativeVersion.setId(99L); nativeVersion.setFunctionId(11L); nativeVersion.setVersion(8); nativeVersion.setBusinessVersion(3); nativeVersion.setFunctionJson(shared.getSnapshotJson());
        ReflectionTestUtils.setField(service, "functionVersionMapper", java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{com.hengshucredit.rule.server.mapper.RuleFunctionVersionMapper.class}, (p, m, a) -> nativeVersion));
        assertThrows(IllegalArgumentException.class, () -> service.snapshot(new ResourceVersionCall("FUNCTION", 11L, 99L, "", List.of(), null, null, "FUNCTION_HISTORY"), 0L));
        shared.setDeleted(false);
        assertEquals(11L, service.snapshot(new ResourceVersionCall("FUNCTION", 11L, 99L, "", List.of(), null, null, "FUNCTION_HISTORY"), 0L).getLongValue("ownerId"));
    }

    @Test public void deletedBusinessModelCannotBeSelectedThroughNativeSnapshotFields() {
        var shared = version("MODEL", "{\"id\":11,\"_modelContentVersionId\":101,\"publishedVersion\":8}"); shared.setBusinessVersion(3); shared.setDeleted(true);
        var nativeVersion = new com.hengshucredit.rule.model.entity.RuleModelVersion(); nativeVersion.setId(101L); nativeVersion.setModelId(11L); nativeVersion.setVersion(8); nativeVersion.setBusinessVersion(3);
        ReflectionTestUtils.setField(service, "modelVersionMapper", java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{com.hengshucredit.rule.server.mapper.RuleModelVersionMapper.class}, (p, m, a) -> nativeVersion));
        ReflectionTestUtils.setField(service, "modelVersions", new ModelVersionTestService() {
            @Override public com.hengshucredit.rule.model.entity.RuleModel executableModel(com.hengshucredit.rule.model.entity.RuleModelVersion ignored) { throw new AssertionError("已删除版本不得加载执行文件"); }
        });
        assertThrows(IllegalArgumentException.class, () -> service.fields("MODEL", 11L, 101L));
    }

    @Test public void nativeDeletionDoesNotGuessAnUnmappedLegacyBusinessNumber() {
        var shared = version("FUNCTION", "{\"id\":11}"); shared.setBusinessVersion(3); shared.setDeleted(true);
        service.assertNativeVersionAvailable("FUNCTION", 11L, null, 99L);
        service.assertNativeVersionAvailable("FUNCTION", 12L, 3, 99L);
        shared.setLegacySourceType("FUNCTION_VERSION"); shared.setLegacySourceId(99L);
        assertThrows(IllegalArgumentException.class, () -> service.assertNativeVersionAvailable("FUNCTION", 11L, null, 99L));
    }
    @Test public void businessModelFieldCatalogRejectsUnpublishedOrCorruptGovernanceHeadBeforeNativeLookup() {
        var head = version("MODEL", "{\"id\":11,\"publishedVersion\":2}");
        ReflectionTestUtils.setField(service, "modelVersionMapper", java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{com.hengshucredit.rule.server.mapper.RuleModelVersionMapper.class}, (p, m, a) -> { throw new AssertionError("无效业务版本不得继续读取原生模型"); }));
        head.setEffectiveStatus("DISABLED");
        assertThrows(IllegalArgumentException.class, () -> service.fields("MODEL", 11L, 91L, "BUSINESS_VERSION"));
        head.setEffectiveStatus("ACTIVE"); head.setSnapshotJson("{\"id\":11,\"publishedVersion\":3}");
        assertThrows(IllegalArgumentException.class, () -> service.fields("MODEL", 11L, 91L, "BUSINESS_VERSION"));
        assertThrows(IllegalArgumentException.class, () -> service.snapshot(new ResourceVersionCall("MODEL", 11L, 91L, "", List.of(), null, null, "BUSINESS_VERSION"), 3L));
    }
    @Test public void configuredButUnpublishedHistoricalVersionIsRejected() {
        var row = version("VARIABLE", "{\"id\":11,\"scope\":\"GLOBAL\",\"varSource\":\"CONSTANT\",\"varType\":\"INTEGER\",\"defaultValue\":\"42\"}");
        for (String status : List.of("DRAFT", "APPROVED", "DISABLED", "DELETED")) {
            row.setEffectiveStatus(status);
            assertThrows(IllegalArgumentException.class, () -> service.snapshot(new ResourceVersionCall("CONSTANT", 11L, 91L, "", List.of()), 5L));
        }
    }

    @Test public void executableModelConfigurationIsNotProofOfPublication() {
        try (var fixture = new ModelVersionExecutionTest.Fixture()) {
            var draftModel = ModelVersionExecutionTest.model(1, "OldAge", 1); draftModel.setPublishedVersion(null);
            var unpublished = fixture.capture(draftModel);
            unpublished.setId(101L); unpublished.setStatus(1);
            ReflectionTestUtils.setField(service, "modelVersions", fixture.tests);
            ReflectionTestUtils.setField(service, "modelVersionMapper", java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{com.hengshucredit.rule.server.mapper.RuleModelVersionMapper.class}, (p, m, args) -> unpublished));
            assertThrows(IllegalArgumentException.class, () -> service.snapshot(new ResourceVersionCall("MODEL", 7L, 101L, "", List.of()), 3L));
        }
    }
    private final ResourceVersionService service = new ResourceVersionService();
    private final Map<Long, GovernedResourceVersion> versions = new java.util.HashMap<>();
    private final List<RuleFunction> calls = new java.util.ArrayList<>();

    @Before public void setUp() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "resource-versions"), GovernedResourceVersion.class);
        ReflectionTestUtils.setField(service, "versionMapper", java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{GovernedResourceVersionMapper.class}, (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) return versions.get(args[0]);
                    if ("selectList".equals(method.getName())) return new java.util.ArrayList<>(versions.values());
                    throw new UnsupportedOperationException(method.getName());
                }));
        ReflectionTestUtils.setField(service, "functionService", new RuleFunctionService() {
            @Override public Object invokeSnapshot(RuleFunction function, List<Object> args) {
                calls.add(function);
                assertEquals(List.of(4), args);
                assertEquals("return a + 1;", function.getImplScript());
                return 5;
            }
        });
        GovernanceSecretCodec secrets = new GovernanceSecretCodec(null) {
            @Override public Map<String, Object> restore(com.hengshucredit.rule.server.governance.ResourceSnapshot snapshot) {
                return JSON.parseObject(snapshot.snapshotJson());
            }
        };
        ReflectionTestUtils.setField(service, "secretCodec", secrets);
    }

    private GovernedResourceVersion version(String type, String json) {
        GovernedResourceVersion version = new GovernedResourceVersion();
        version.setId(91L); version.setResourceId(11L); version.setResourceType(type);
        version.setEffectiveStatus("ACTIVE");
        version.setVersionNo(1); version.setSnapshotJson(json); version.setSnapshotDigest(Sha256Digests.text(json));
        versions.put(91L, version);
        return version;
    }

    @Test public void fixedConstantUsesHistoricalValueAndValidatesOwnership() {
        version("VARIABLE", "{\"id\":11,\"projectId\":5,\"scope\":\"PROJECT\",\"varSource\":\"CONSTANT\",\"varType\":\"INTEGER\",\"defaultValue\":\"42\"}");
        var call = new ResourceVersionCall("CONSTANT", 11L, 91L, "", List.of());
        assertEquals("42", String.valueOf(service.resolve(call, 5L, Map.of("changed", 99), VariableResolveOptions.defaults())));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(call, 6L, Map.of(), VariableResolveOptions.defaults()));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(new ResourceVersionCall("CONSTANT", 12L, 91L, "", List.of()), 5L, Map.of(), VariableResolveOptions.defaults()));
    }

    @Test public void businessVersionResolvesNewRevisionWhileSnapshotReferenceRemainsImmutable() {
        var original = version("VARIABLE", "{\"id\":11,\"scope\":\"GLOBAL\",\"varSource\":\"CONSTANT\",\"varType\":\"INTEGER\",\"defaultValue\":\"42\"}");
        original.setBusinessVersion(1);
        var updated = JSON.parseObject(JSON.toJSONString(original), GovernedResourceVersion.class);
        updated.setId(92L); updated.setVersionNo(3); updated.setSnapshotJson(original.getSnapshotJson().replace("42", "84"));
        updated.setSnapshotDigest(Sha256Digests.text(updated.getSnapshotJson())); versions.put(92L, updated);
        var options = VariableResolveOptions.defaults();
        assertEquals("42", String.valueOf(service.resolve(new ResourceVersionCall("CONSTANT", 11L, 91L, "", List.of()), 5L, Map.of(), options)));
        var call = new ResourceVersionCall("CONSTANT", 11L, 91L, "", List.of(), null, null, "BUSINESS_VERSION");
        assertEquals("84", String.valueOf(service.resolve(call, 5L, Map.of(), options)));
        assertEquals(Long.valueOf(92), service.snapshot(call, 5L).getLong("resolvedVersionId"));
        updated.setDeleted(true);
        assertEquals("84", String.valueOf(service.resolve(call, 5L, Map.of(), options)));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(call, 5L, Map.of(), VariableResolveOptions.defaults()));
    }

    @Test public void fixedFunctionInvokesSnapshotAndNeverCurrentFunction() {
        version("FUNCTION", "{\"id\":11,\"scope\":\"GLOBAL\",\"funcCode\":\"oldName\",\"implScript\":\"return a + 1;\",\"paramsJson\":\"[{\\\"name\\\":\\\"a\\\"}]\"}");
        assertEquals(5, service.resolve(new ResourceVersionCall("FUNCTION", 11L, 91L, "", List.of(4)), 5L, Map.of(), VariableResolveOptions.defaults()));
        assertEquals(1, calls.size());
    }

    @Test public void corruptedOrMissingVersionCannotFallBack() {
        var version = version("VARIABLE", "{\"id\":11,\"scope\":\"GLOBAL\"}");
        version.setSnapshotJson("{}");
        assertThrows(IllegalArgumentException.class, () -> service.resolve(new ResourceVersionCall("VARIABLE", 11L, 91L, "", List.of()), 5L, Map.of(), VariableResolveOptions.defaults()));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(new ResourceVersionCall("VARIABLE", 11L, 92L, "", List.of()), 5L, Map.of(), VariableResolveOptions.defaults()));
    }

    @Test public void modelOutputUsesSelectedExecutableSnapshotAndHistoricalFieldId() {
        try (var fixture = new ModelVersionExecutionTest.Fixture()) {
            var firstModel = ModelVersionExecutionTest.model(1, "OldAge", 1); firstModel.setPublishedVersion(1);
            var first = fixture.capture(firstModel); first.setId(101L); first.setStatus(1);
            var changed = ModelVersionExecutionTest.model(2, "NewAge", 100);
            changed.setPublishedVersion(2);
            changed.getOutputFields().get(0).setId(62L);
            var second = fixture.capture(changed); second.setId(102L); second.setStatus(1);
            var history = Map.of(101L, first, 102L, second);
            ReflectionTestUtils.setField(service, "modelVersions", fixture.tests);
            ReflectionTestUtils.setField(service, "modelVersionMapper", java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{com.hengshucredit.rule.server.mapper.RuleModelVersionMapper.class},
                    (proxy, method, args) -> history.get(args[0])));
            var values = Map.<String, Object>of("OldAge", 2, "NewAge", 3);
            var options = VariableResolveOptions.defaults();
            Object old = service.resolve(new ResourceVersionCall("MODEL_OUTPUT", 61L, 101L, "", List.of()), 3L, values, options);
            Object current = service.resolve(new ResourceVersionCall("MODEL_OUTPUT", 62L, 102L, "", List.of()), 3L, values, options);
            assertEquals(5d, ((Number) old).doubleValue(), 0d);
            assertEquals(106d, ((Number) current).doubleValue(), 0d);
            assertThrows(IllegalArgumentException.class, () -> service.resolve(new ResourceVersionCall("MODEL_OUTPUT", 61L, 102L, "", List.of()), 3L, values, options));
            Object fields = service.fields("MODEL", 7L, 101L).get("fields");
            assertTrue("历史输出字段应返回列表", fields instanceof List<?>);
            List<?> outputFields = (List<?>) fields;
            assertEquals(1, outputFields.size());
            assertTrue("历史输出字段应为对象", outputFields.get(0) instanceof Map<?, ?>);
            assertEquals("61", ((Map<?, ?>) outputFields.get(0)).get("refId"));
        }
    }

    @Test public void apiExpressionCannotInvokeArbitraryHistoricalScriptFunction() {
        version("FUNCTION", "{\"id\":11,\"scope\":\"GLOBAL\",\"funcCode\":\"sum\",\"implScript\":\"return 1;\"}");
        var options = VariableResolveOptions.defaults(); options.setApiExpression(true);
        assertThrows(IllegalArgumentException.class, () -> service.resolve(new ResourceVersionCall("FUNCTION", 11L, 91L, "", List.of()), 5L, Map.of(), options));
        assertTrue(calls.isEmpty());
    }

    @Test public void historicalScriptsExecuteTheirOwnBodiesAfterNameAndCodeChange() {
        RuleFunctionService executor = new RuleFunctionService();
        ReflectionTestUtils.setField(executor, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(executor, "qlExpressEngine", new com.hengshucredit.rule.core.engine.QLExpressEngine());
        ReflectionTestUtils.setField(service, "functionService", executor);
        version("FUNCTION", "{\"id\":11,\"scope\":\"GLOBAL\",\"funcCode\":\"oldName\",\"implType\":\"SCRIPT\",\"implScript\":\"return n + 1;\",\"paramsJson\":\"[{\\\"name\\\":\\\"n\\\",\\\"type\\\":\\\"NUMBER\\\"}]\"}");
        var first = versions.get(91L);
        var second = JSON.parseObject(JSON.toJSONString(first), GovernedResourceVersion.class);
        second.setId(92L); second.setVersionNo(2);
        second.setSnapshotJson(first.getSnapshotJson().replace("oldName", "newName").replace("n + 1", "n + 100"));
        second.setSnapshotDigest(Sha256Digests.text(second.getSnapshotJson())); versions.put(92L, second);
        assertEquals(5d, ((Number) service.resolve(new ResourceVersionCall("FUNCTION", 11L, 91L, "", List.of(4)), 5L, Map.of(), VariableResolveOptions.defaults())).doubleValue(), 0d);
        assertEquals(104d, ((Number) service.resolve(new ResourceVersionCall("FUNCTION", 11L, 92L, "", List.of(4)), 5L, Map.of(), VariableResolveOptions.defaults())).doubleValue(), 0d);
    }

    @Test public void sourceStatusReadsSelectedVersionAndDoesNotLeakIntoCurrentState() {
        version("VARIABLE", "{\"id\":11,\"scope\":\"GLOBAL\",\"varCode\":\"apiResult\",\"varSource\":\"API\"}");
        ReflectionTestUtils.setField(service, "variableResolver", new VariableSourceResolver() {
            @Override public Object resolveVersion(com.hengshucredit.rule.model.entity.RuleVariable variable, Map<String, Object> values, VariableResolveOptions options) {
                assertTrue(options.requiresSourceStatus("VARIABLE", 11L));
                options.recordSourceState("VARIABLE", 11L, "HTTP_STATUS", 401);
                return null;
            }
        });
        var options = VariableResolveOptions.defaults(); options.recordSourceState("VARIABLE", 11L, "HTTP_STATUS", 200);
        assertEquals(true, service.resolve(new ResourceVersionCall("VARIABLE", 11L, 91L, "", List.of(), "HTTP_STATUS", "401"), 5L, Map.of(), options));
        assertEquals(200, options.getSourceStates().get("VARIABLE:11").get("HTTP_STATUS"));
    }

    @Test public void originalFunctionHistoryHasAnIndependentVersionNamespace() {
        version("VARIABLE", "{\"id\":11,\"scope\":\"GLOBAL\"}");
        var legacy = new com.hengshucredit.rule.model.entity.RuleFunctionVersion();
        legacy.setId(91L); legacy.setFunctionId(11L); legacy.setVersion(3);
        legacy.setFunctionJson("{\"id\":11,\"scope\":\"GLOBAL\",\"status\":1,\"funcCode\":\"oldFn\",\"implScript\":\"return a + 1;\"}");
        ReflectionTestUtils.setField(service, "functionVersionMapper", java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{com.hengshucredit.rule.server.mapper.RuleFunctionVersionMapper.class}, (proxy, method, args) -> legacy));
        var call = new ResourceVersionCall("FUNCTION", 11L, 91L, "", List.of(4), null, null, "FUNCTION_HISTORY");
        assertEquals(5, service.resolve(call, 5L, Map.of(), VariableResolveOptions.defaults()));
        assertEquals("FUNCTION:11@FUNCTION_HISTORY:91", call.bindingKey());
        assertEquals("FUNCTION_HISTORY", service.field("FUNCTION", 11L, 91L, "FUNCTION_HISTORY").get("versionSource"));
        assertThrows(IllegalArgumentException.class, () -> service.snapshot(new ResourceVersionCall("FUNCTION", 12L, 91L, "", List.of(), null, null, "FUNCTION_HISTORY"), 5L));
    }

    @Test public void publishedConstantUsesEmbeddedVersionWithoutLookingUpCurrentHistory() {
        version("VARIABLE", "{\"id\":11,\"scope\":\"GLOBAL\",\"varSource\":\"CONSTANT\",\"varType\":\"INTEGER\",\"defaultValue\":\"42\"}");
        var call = new ResourceVersionCall("CONSTANT", 11L, 91L, "", List.of());
        var runtime = new com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService.RuntimeSnapshot();
        runtime.getResourceVersions().put(call.bindingKey(), service.snapshot(call, 5L));
        versions.clear();
        var options = VariableResolveOptions.defaults(); options.setRuntimeSnapshot(runtime);
        assertEquals("42", String.valueOf(service.resolve(call, 5L, Map.of(), options)));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(new ResourceVersionCall("CONSTANT", 11L, 92L, "", List.of()), 5L, Map.of(), options));
    }
}
