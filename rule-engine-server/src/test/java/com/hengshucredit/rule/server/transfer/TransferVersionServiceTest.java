package com.hengshucredit.rule.server.transfer;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.mapper.GovernedResourceMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper;
import com.hengshucredit.rule.server.security.ConsoleAccessGuard;
import com.hengshucredit.rule.server.service.ResourceVersionService;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleVersionBindingService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.Assert.*;

public class TransferVersionServiceTest {
    @Test public void previewReturnsTheExactValidatedBindingAndPublicTargetName() {
        version(80, 9, "VARIABLE", "{\"id\":50,\"scope\":\"GLOBAL\",\"varType\":\"INTEGER\",\"varLabel\":\"目标阈值\"}");
        var config = Map.<String, Object>of("script", "return resourceVersionValue('VARIABLE','3','BUSINESS_VERSION:1','');");
        var source = new TransferBundle.Resource("RULE:1", config, List.of(), List.of());
        var reference = TransferVersionReferences.scan(config).get(0);
        var binding = binding(80, 80);
        var options = new TransferImportOptions(null, "GLOBAL", "REUSE", "SUFFIX", "_copy", false, null, null, Map.of(), false,
                List.of(), Map.of(), Map.of(), Map.of(), Map.of(reference.key(source.key()), binding));
        var row = service.preview(List.of(source), options).get(0);
        assertEquals(true, row.get("valid")); assertEquals(binding, row.get("binding"));
        assertEquals("目标阈值", ((Map<?, ?>) row.get("target")).get("ownerLabel"));
    }
    private final TransferVersionService service = new TransferVersionService();
    private final Map<Long, GovernedResourceVersion> versions = new LinkedHashMap<>();
    private final GovernedResource owner = new GovernedResource();

    @Before public void setup() {
        for (Class<?> type : List.of(GovernedResource.class, GovernedResourceVersion.class))
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "transfer-version-test"), type);
        owner.setResourceId(50L); owner.setResourceType("VARIABLE"); owner.setEffectiveStatus("ACTIVE");
        ReflectionTestUtils.setField(service, "access", new ConsoleAccessGuard(null, null) {
            @Override public void requireResource(String type, String action) { assertEquals("view", action); }
        });
        ReflectionTestUtils.setField(service, "governedResources", Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{GovernedResourceMapper.class}, (p, m, args) -> owner));
        ResourceVersionService resources = new ResourceVersionService();
        ReflectionTestUtils.setField(resources, "versionMapper", Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{GovernedResourceVersionMapper.class}, (p, m, args) -> switch (m.getName()) {
                    case "selectById" -> versions.get(args[0]);
                    case "selectList" -> new ArrayList<>(versions.values());
                    default -> throw new UnsupportedOperationException(m.getName());
                }));
        ReflectionTestUtils.setField(service, "resources", resources);
    }

    private GovernedResourceVersion version(long id, int revision, String type, String json) {
        var row = new GovernedResourceVersion(); row.setId(id); row.setResourceId(50L); row.setResourceType(type);
        row.setVersionNo(revision); row.setBusinessVersion(7); row.setEffectiveStatus("ACTIVE");
        row.setSnapshotJson(json); row.setSnapshotDigest(Sha256Digests.text(json)); versions.put(id, row); return row;
    }
    private Map<String, Object> source(String type) { return Map.of("refType", type, "refId", 3L, "versionId", 1L, "versionSource", "BUSINESS_VERSION", "valueType", "INTEGER"); }
    private TransferVersionBinding binding(long anchor, long expected) { return new TransferVersionBinding(50L, anchor, null, expected); }

    @Test public void explicitDifferentIdsAndBusinessNumbersAreResolvedWithoutGuessing() {
        version(80, 9, "VARIABLE", "{\"id\":50,\"scope\":\"GLOBAL\",\"varType\":\"INTEGER\"}");
        var result = service.target(source("VARIABLE"), binding(80, 80), "PROJECT", 4L);
        assertEquals(50L, result.get("refId")); assertEquals(7, result.get("version")); assertEquals(80L, result.get("snapshotId"));
        assertThrows(IllegalArgumentException.class, () -> service.target(source("VARIABLE"), null, "PROJECT", 4L));
        assertThrows(IllegalArgumentException.class, () -> service.target(source("VARIABLE"), binding(1, 1), "PROJECT", 4L));
    }

    @Test public void previewBindingBecomesInvalidAfterTargetLineWasOverwritten() {
        String json = "{\"id\":50,\"scope\":\"GLOBAL\",\"varType\":\"INTEGER\"}";
        version(80, 9, "VARIABLE", json); version(81, 10, "VARIABLE", json);
        assertThrows(IllegalStateException.class, () -> service.target(source("VARIABLE"), binding(80, 80), "GLOBAL", 0L));
        assertEquals(81L, service.target(source("VARIABLE"), binding(80, 81), "GLOBAL", 0L).get("snapshotId"));
    }

    @Test public void targetScopeTypeDeletedStateAndVersionOwnerAreValidated() {
        var version = version(80, 9, "VARIABLE", "{\"id\":50,\"scope\":\"PROJECT\",\"projectId\":4,\"varType\":\"STRING\"}");
        assertThrows(IllegalArgumentException.class, () -> service.target(source("VARIABLE"), binding(80, 80), "PROJECT", 4L));
        var source = new HashMap<>(source("VARIABLE")); source.put("valueType", "STRING");
        assertThrows(IllegalArgumentException.class, () -> service.target(source, binding(80, 80), "PROJECT", 5L));
        assertThrows(IllegalArgumentException.class, () -> service.target(source, binding(80, 80), "GLOBAL", 0L));
        version.setResourceId(51L);
        assertThrows(IllegalArgumentException.class, () -> service.target(source, binding(80, 80), "PROJECT", 4L));
        version.setResourceId(50L); version.setDeleted(true);
        assertThrows(IllegalArgumentException.class, () -> service.target(source, binding(80, 80), "PROJECT", 4L));
    }

    @Test public void historicalFieldMustBelongToTheSelectedSnapshot() {
        version(80, 9, "DATA_OBJECT", "{\"id\":50,\"scope\":\"GLOBAL\",\"fields\":[{\"id\":55,\"varCode\":\"Age_Mixed\",\"varType\":\"INTEGER\"}]}");
        var invalid = new TransferVersionBinding(50L, 80L, 56L, 80L);
        assertThrows(IllegalArgumentException.class, () -> service.target(source("DATA_OBJECT"), invalid, "GLOBAL", 0L));
        var valid = new TransferVersionBinding(50L, 80L, 55L, 80L);
        var result = service.target(source("DATA_OBJECT"), valid, "GLOBAL", 0L);
        assertEquals(55L, result.get("refId")); assertEquals("Age_Mixed", result.get("code"));
    }

    @Test public void functionParametersCannotBeSilentlyChangedByMapping() {
        version(80, 9, "FUNCTION", "{\"id\":50,\"scope\":\"GLOBAL\",\"returnType\":\"INTEGER\",\"paramsJson\":\"[{\\\"type\\\":\\\"STRING\\\"}]\"}");
        var source = new HashMap<>(source("FUNCTION")); source.put("paramsJson", "[{\"type\":\"INTEGER\"}]");
        assertThrows(IllegalArgumentException.class, () -> service.target(source, binding(80, 80), "GLOBAL", 0L));
        source.put("paramsJson", "[{\"type\":\"STRING\"}]");
        assertEquals(50L, service.target(source, binding(80, 80), "GLOBAL", 0L).get("refId"));
    }

    @Test public void ruleBindingUsesExplicitTargetSnapshotWhileLatestKeepsItsPolicy() {
        RuleDefinition definition = new RuleDefinition(); definition.setId(50L); definition.setScope("GLOBAL"); definition.setStatus(1); definition.setPublishedVersion(9);
        ReflectionTestUtils.setField(service, "definitions", new RuleDefinitionService() {
            @Override public RuleDefinition getById(java.io.Serializable id) { return definition; }
        });
        ReflectionTestUtils.setField(service, "rules", new RuleVersionBindingService() {
            @Override public RuleDefinitionVersion resolveSnapshot(Long id, Long versionId) {
                assertEquals(Long.valueOf(50), id); assertEquals(Long.valueOf(80), versionId);
                var row = new RuleDefinitionVersion(); row.setId(99L); row.setRevisionId(100L); row.setArtifactId(101L); row.setBusinessVersion(6); return row;
            }
        });
        assertThrows(IllegalStateException.class, () -> service.target(source("RULE"), binding(80, 80), "GLOBAL", 0L));
        assertEquals(6, service.target(source("RULE"), binding(80, 99), "GLOBAL", 0L).get("version"));
        var latest = Map.<String, Object>of("refType", "RULE", "versionSource", "LATEST");
        assertEquals("LATEST", service.target(latest, new TransferVersionBinding(50L, null, null, null), "GLOBAL", 0L).get("mode"));
    }
}
