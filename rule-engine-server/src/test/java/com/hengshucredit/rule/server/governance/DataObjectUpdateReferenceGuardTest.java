package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleVersionBinding;
import com.hengshucredit.rule.model.entity.RuleDefinitionVersion;
import com.hengshucredit.rule.server.mapper.RuleVersionBindingMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.LinkedHashSet;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class DataObjectUpdateReferenceGuardTest {
    @Test public void removingFieldUsedByRuleOutputModelOutputOrFunctionIsBlocked() {
        for (String type : List.of("RULE_INPUT", "RULE_OUTPUT", "MODEL_INPUT", "MODEL_OUTPUT", "FUNCTION")) {
            var f = fixture(type, Map.of("paramsJson", "[{\"varId\":81,\"refType\":\"DATA_OBJECT\"}]"));
            assertFalse(type, f.validate(9L, Map.of("fields", List.of())).isEmpty());
        }
    }

    @Test public void changingTypeOrListElementTypeOfReferencedFieldIsBlocked() {
        var f = fixture("VARIABLE", Map.of("sourceConfig",
                "{\"input\":{\"kind\":\"REFERENCE\",\"refId\":81,\"refType\":\"DATA_OBJECT\"}}"));
        assertFalse(f.validate(9L, Map.of("fields", List.of(Map.of("id", 81, "varType", "STRING")))).isEmpty());
        f.current.get(0).setVarType("LIST"); f.current.get(0).setGenericType("NUMBER");
        assertFalse(f.validate(9L, Map.of("fields", List.of(Map.of("id", 81, "varType", "LIST", "genericType", "STRING")))).isEmpty());
    }

    @Test public void intactFieldIdentityAllowsConfigurationUpdate() {
        var f = fixture("FUNCTION", Map.of("varId", 81, "refType", "DATA_OBJECT"));
        assertTrue(f.validate(9L, Map.of("fields", List.of(Map.of("id", 81, "varType", "NUMBER", "varLabel", "新标签")))).isEmpty());
        assertEquals(0, f.consumerReads);
    }

    @Test public void unusedRemovedFieldIsAllowedAndUnrelatedTypedIdsDoNotBlock() {
        for (String type : List.of("VARIABLE", "CONSTANT", "MODEL")) {
            var f = fixture("FUNCTION", Map.of("varId", 81, "refType", type));
            assertTrue(type, f.validate(9L, Map.of("fields", List.of())).isEmpty());
        }
        assertTrue(fixture("FUNCTION", Map.of("varId", 82, "refType", "DATA_OBJECT"))
                .validate(9L, Map.of("fields", List.of())).isEmpty());
    }

    @Test public void objectBindingsAndParentFieldConsumersProtectRemovedChildren() {
        for (String key : List.of("requestObjectId", "responseObjectId", "refObjectId", "parentObjectId")) {
            assertFalse(key, fixture("EXTERNAL_API", Map.of(key, 9))
                    .validate(9L, Map.of("fields", List.of())).isEmpty());
        }
        var f = fixture("RULE", Map.of("_varId", 80, "_refType", "DATA_OBJECT"));
        RuleDataObjectField parent = field(80L, "OBJECT");
        f.current.get(0).setParentFieldId(80L);
        f.current = List.of(parent, f.current.get(0));
        assertFalse(f.validate(9L, Map.of("fields", List.of(Map.of("id", 80, "varType", "OBJECT")))).isEmpty());
    }

    @Test public void literalBusinessIdsAndForeignObjectIdsDoNotBecomeReferences() {
        var f = fixture("VARIABLE", Map.of("sourceConfig", "{\"kind\":\"LITERAL\",\"value\":{\"varId\":81,\"refType\":\"DATA_OBJECT\",\"requestObjectId\":9}}"));
        assertTrue(f.validate(9L, Map.of("fields", List.of())).isEmpty());
        assertTrue(fixture("EXTERNAL_API", Map.of("requestObjectId", 99))
                .validate(9L, Map.of("fields", List.of())).isEmpty());
    }

    @Test public void jsonSourceExamplesAreNotEngineReferencesEvenWhenBusinessFieldNamesMatch() {
        var f = fixture("数据对象", Map.of("sourceType", "JSON", "sourceContent",
                "{\"refObjectId\":9,\"varId\":81,\"refType\":\"DATA_OBJECT\"}"));
        assertTrue(f.validate(9L, Map.of("fields", List.of())).isEmpty());
    }

    @Test public void actualApiSourceOperandsAreStillProtectedButNestedBusinessObjectIdsAreNotBindings() {
        assertFalse(fixture("数据对象", Map.of("sourceType", "API", "sourceContent",
                "{\"input\":{\"kind\":\"REFERENCE\",\"refId\":81,\"refType\":\"DATA_OBJECT\"}}"))
                .validate(9L, Map.of("fields", List.of())).isEmpty());
        assertTrue(fixture("数据对象", Map.of("sourceType", "API", "sourceContent",
                "{\"params\":{\"refObjectId\":9,\"requestObjectId\":9}}"))
                .validate(9L, Map.of("fields", List.of())).isEmpty());
    }

    @Test public void onlyCurrentSnapshotsOfActiveFixedVersionsProtectTheirFieldReferences() throws Exception {
        for (Class<?> entity : List.of(RuleVersionBinding.class, RuleDefinitionVersion.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "guard-test"), entity);
        }
        List<RuleVersionBinding> bindings = new ArrayList<>();
        bindings.add(binding(10L, 1)); bindings.add(binding(20L, 1)); bindings.add(binding(30L, 0));
        List<RuleDefinitionVersion> versions = List.of(version(10L, true), version(20L, false), version(30L, true));
        Set<Long> queried = new LinkedHashSet<>();
        var bindingMapper = (RuleVersionBindingMapper) Proxy.newProxyInstance(RuleVersionBindingMapper.class.getClassLoader(),
                new Class<?>[]{RuleVersionBindingMapper.class}, (proxy, method, args) -> {
                    assertEquals("selectList", method.getName()); return bindings;
                });
        var versionMapper = (RuleDefinitionVersionMapper) Proxy.newProxyInstance(RuleDefinitionVersionMapper.class.getClassLoader(),
                new Class<?>[]{RuleDefinitionVersionMapper.class}, (proxy, method, args) -> {
                    assertEquals("selectList", method.getName());
                    var query = (LambdaQueryWrapper<?>) args[0]; query.getSqlSegment();
                    assertFalse(query.getSqlSelect().contains("compiled_script"));
                    assertFalse(query.getSqlSelect().contains("artifact_digest"));
                    queried.clear(); query.getParamNameValuePairs().values().forEach(id -> queried.add(((Number) id).longValue()));
                    return versions.stream().filter(row -> queried.contains(row.getId())).toList();
                });
        var guard = new DataObjectUpdateReferenceGuard() {
            @Override protected List<RuleDataObjectField> fields(Long objectId) { return List.of(field(81L, "NUMBER")); }
            @Override protected List<Consumer> consumers(Long objectId, Map<String, Object> incoming) { return activeVersionConsumers(); }
        };
        for (var entry : Map.of("bindingMapper", bindingMapper, "versionMapper", versionMapper).entrySet()) {
            var member = DataObjectUpdateReferenceGuard.class.getDeclaredField(entry.getKey());
            member.setAccessible(true); member.set(guard, entry.getValue());
        }
        assertFalse(guard.validate(9L, Map.of("fields", List.of())).isEmpty());
        assertEquals(Set.of(10L, 20L), queried);
        bindings.get(0).setStatus(0);
        assertTrue(guard.validate(9L, Map.of("fields", List.of())).isEmpty());
        assertEquals(Set.of(20L), queried);
    }

    private static RuleVersionBinding binding(Long snapshotId, int status) {
        var row = new RuleVersionBinding(); row.setSnapshotId(snapshotId); row.setStatus(status); return row;
    }
    private static RuleDefinitionVersion version(Long id, boolean referencesField) {
        var row = new RuleDefinitionVersion(); row.setId(id); row.setDefinitionId(7L);
        row.setModelJson(referencesField ? "{\"resultVar\":{\"_varId\":81,\"_refType\":\"DATA_OBJECT\"}}" : "{}");
        return row;
    }

    private static Fixture fixture(String type, Object configuration) {
        return new Fixture(type, configuration);
    }
    private static RuleDataObjectField field(Long id, String type) {
        var field = new RuleDataObjectField(); field.setId(id); field.setObjectId(9L);
        field.setVarCode("age"); field.setVarType(type); return field;
    }
    private static class Fixture extends DataObjectUpdateReferenceGuard {
        List<RuleDataObjectField> current = List.of(field(81L, "NUMBER"));
        final List<Consumer> references;
        int consumerReads;
        Fixture(String type, Object configuration) { references = List.of(new Consumer(type, 7L, configuration)); }
        @Override protected List<RuleDataObjectField> fields(Long objectId) { return current; }
        @Override protected List<Consumer> consumers(Long objectId, Map<String, Object> incoming) {
            consumerReads++; return references;
        }
    }
}
