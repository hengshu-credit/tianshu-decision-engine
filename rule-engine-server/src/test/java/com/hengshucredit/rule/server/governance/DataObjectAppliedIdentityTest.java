package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldOptionMapper;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class DataObjectAppliedIdentityTest {
    @Test public void insertedNestedChildrenReturnActualIdsWithoutNameMatching() {
        var f = new Fixture();
        var result = f.apply(List.of(field(10L, null, "father"), field(11L, 10L, "age"),
                field(12L, null, "mother"), field(13L, 12L, "age")), "CREATE");
        Map<Long, Long> ids = result.fieldIdMapping();
        assertEquals(4, ids.size());
        assertNotEquals(ids.get(11L), ids.get(13L));
        assertEquals(ids.get(10L), f.fields.get(ids.get(11L)).getParentFieldId());
        assertEquals(ids.get(12L), f.fields.get(ids.get(13L)).getParentFieldId());
        assertEquals("father", f.fields.get(ids.get(10L)).getVarCode());
        assertEquals("mother", f.fields.get(ids.get(12L)).getVarCode());
        assertFalse(ids.values().stream().anyMatch(id -> id < 100));
        assertEquals(Long.valueOf(9), result.resourceId());
    }

    @Test public void retainedFieldAndItsNewChildBothHaveExplicitMappings() {
        var f = new Fixture();
        RuleDataObjectField retained = new RuleDataObjectField();
        retained.setId(10L); retained.setObjectId(9L); retained.setVarCode("father");
        f.fields.put(10L, retained);
        var result = f.apply(List.of(field(10L, null, "father"), field(11L, 10L, "age")), "UPDATE");
        assertEquals(Long.valueOf(10), result.fieldIdMapping().get(10L));
        assertEquals(Long.valueOf(10), f.fields.get(result.fieldIdMapping().get(11L)).getParentFieldId());
        assertEquals(2, f.fields.size());
    }

    @Test public void duplicateSourceIdsAreRejectedBeforeDeletingFields() {
        var f = new Fixture();
        RuleDataObjectField existing = new RuleDataObjectField(); existing.setId(80L); f.fields.put(80L, existing);
        assertThrows(IllegalArgumentException.class, () -> f.apply(
                List.of(field(10L, null, "father"), field(10L, null, "mother")), "UPDATE"));
        assertTrue(f.fields.containsKey(80L));
    }

    @Test public void missingParentIsRejectedInsteadOfInventingAnIdentity() {
        var f = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> f.apply(List.of(field(11L, 99L, "age")), "CREATE"));
        assertTrue(f.fields.isEmpty());
    }

    @Test public void referencedFieldDeletionIsRecheckedBeforeAnyProjectionWrite() throws Exception {
        var f = new Fixture();
        RuleDataObjectField field = new RuleDataObjectField();
        field.setId(80L); field.setObjectId(9L); field.setVarType("NUMBER"); f.fields.put(80L, field);
        var guard = new DataObjectUpdateReferenceGuard() {
            @Override protected List<RuleDataObjectField> fields(Long objectId) { return List.copyOf(f.fields.values()); }
            @Override protected List<Consumer> consumers(Long objectId, Map<String, Object> incoming) {
                return List.of(new Consumer("函数", 3L, Map.of("paramsJson", "[{\"varId\":80,\"refType\":\"DATA_OBJECT\"}]")));
            }
        };
        var member = DataObjectGovernedResourceAdapter.class.getDeclaredField("updateReferenceGuard");
        member.setAccessible(true); member.set(f.adapter, guard);
        var error = assertThrows(IllegalArgumentException.class, () -> f.apply(List.of(field(10L, null, "father")), "UPDATE"));
        assertTrue(error.getMessage().contains("函数:3"));
        assertEquals(0, f.rootWrites);
        assertTrue(f.fields.containsKey(80L));
        assertEquals(1, f.fields.size());
    }

    private static Map<String, Object> field(Long id, Long parent, String code) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id); value.put("parentFieldId", parent); value.put("varCode", code);
        value.put("varType", parent == null ? "OBJECT" : "NUMBER");
        return value;
    }

    private static class Fixture {
        final Map<Long, RuleDataObjectField> fields = new LinkedHashMap<>();
        final DataObjectGovernedResourceAdapter adapter;
        int rootWrites;
        Fixture() {
            AtomicLong ids = new AtomicLong(100);
            RuleDataObjectFieldMapper fieldMapper = (RuleDataObjectFieldMapper) Proxy.newProxyInstance(
                    RuleDataObjectFieldMapper.class.getClassLoader(), new Class<?>[]{RuleDataObjectFieldMapper.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "selectList": return List.copyOf(fields.values());
                            case "deleteById": fields.remove(args[0]); return 1;
                            case "insert": {
                                RuleDataObjectField field = (RuleDataObjectField) args[0];
                                field.setId(ids.incrementAndGet()); fields.put(field.getId(), field); return 1;
                            }
                            case "updateById": {
                                RuleDataObjectField field = (RuleDataObjectField) args[0]; fields.put(field.getId(), field); return 1;
                            }
                            default: throw new AssertionError(method.getName());
                        }
                    });
            RuleDataObjectFieldOptionMapper options = (RuleDataObjectFieldOptionMapper) Proxy.newProxyInstance(
                    RuleDataObjectFieldOptionMapper.class.getClassLoader(), new Class<?>[]{RuleDataObjectFieldOptionMapper.class},
                    (proxy, method, args) -> "selectList".equals(method.getName()) ? List.of() : 1);
            var store = new SimpleEntityGovernedResourceAdapter.EntityStore<RuleDataObject>() {
                private RuleDataObject value;
                public RuleDataObject load(Long id) { return value; }
                public void insert(RuleDataObject object) { rootWrites++; object.setId(9L); value = object; }
                public void update(RuleDataObject object) { rootWrites++; value = object; }
            };
            ProjectAuthProperties auth = new ProjectAuthProperties();
            auth.getMasterKeys().put("v1", "0123456789abcdef0123456789abcdef");
            adapter = new DataObjectGovernedResourceAdapter(store, fieldMapper, options, new GovernanceSecretCodec(new CredentialCipher(auth)));
        }
        AppliedResource apply(List<Map<String, Object>> values, String action) {
            var snapshot = ResourceSnapshot.ofJson(CanonicalJson.write(Map.of("objectCode", "family", "objectLabel", "家庭",
                    "objectType", "OBJECT", "scope", "GLOBAL", "projectId", 0L, "fields", values)));
            return adapter.apply(new ApprovalApplyContext(1L, "CREATE".equals(action) ? null : 9L, 1, action, snapshot, "tester", null));
        }
    }
}
