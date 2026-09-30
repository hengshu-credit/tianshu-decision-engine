package com.hengshucredit.rule.server.transfer;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.governance.ApprovalApplyContext;
import com.hengshucredit.rule.server.governance.DataObjectGovernedResourceAdapter;
import com.hengshucredit.rule.server.governance.ResourceSnapshot;
import com.hengshucredit.rule.server.governance.GovernanceSecretCodec;
import com.hengshucredit.rule.server.governance.SimpleEntityGovernedResourceAdapter;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldOptionMapper;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class TransferObjectFieldReuseTest {
    private static final TransferImportOptions REUSE = new TransferImportOptions(null, "GLOBAL", "REUSE", "REUSE", "_copy", false, null, null, Map.of());

    @BeforeClass public static void metadata() { OfflineProjectBindingTest.metadata(); }

    @Test public void realMapperOrderingDoesNotChangeConfigurationEqualityAfterImport() {
        var pair = imported();
        assertNotEquals(codes(pair.source()), codes(pair.target()));
        assertEquals("IDENTICAL", TransferResourceComparison.conflict(TransferResourceType.DATA_OBJECT,
                pair.source(), pair.target(), "ACTIVE"));
    }

    @Test public void previewAndApplyReuseCorrectChildrenWhenMapperReordersEqualSortValues() {
        var pair = imported();
        var f = new OfflineProjectBindingTest.Fixture();
        f.seed("DATA_OBJECT", 8L, 0L, pair.target());
        f.seed("FUNCTION", 9L, 0L, Map.of("funcCode", "ages", "paramsJson", JSON.toJSONString(List.of(
                Map.of("varId", pair.mapping().get(11L)), Map.of("varId", pair.mapping().get(13L))))));
        byte[] bytes = new TransferBundleCodec().encode(new TransferBundle(List.of("FUNCTION:2"), List.of(
                new TransferBundle.Resource("FUNCTION:2", Map.of("funcCode", "ages", "paramsJson", "[{\"varId\":11},{\"varId\":13}]"),
                        List.of(new TransferBundle.Reference("/paramsJson/@json/0/varId", "DATA_OBJECT:1", "/fields/1/id"),
                                new TransferBundle.Reference("/paramsJson/@json/1/varId", "DATA_OBJECT:1", "/fields/2/id")), List.of()),
                new TransferBundle.Resource("DATA_OBJECT:1", pair.source(), List.of(), List.of())), List.of()));
        List<?> conflicts = (List<?>) f.previewService.preview(bytes, REUSE).get("conflicts");
        assertEquals("IDENTICAL", ((TransferConflict) conflicts.get(0)).conflictType());
        assertEquals("IDENTICAL", ((TransferConflict) conflicts.get(1)).conflictType());
        var result = f.service.apply(bytes, REUSE, "tester");
        assertEquals(9L, ((Map<?, ?>) result.get("resourceIdMapping")).get("FUNCTION:2"));
        assertTrue(f.drafts.isEmpty());
    }

    @Test public void userConfiguredSortOrderStillParticipatesInComparison() {
        var pair = imported();
        fields(pair.target()).get(0).put("sortOrder", 99);
        assertEquals("CONFIG_CONFLICT", TransferResourceComparison.conflict(TransferResourceType.DATA_OBJECT,
                pair.source(), pair.target(), "ACTIVE"));
    }

    private static Imported imported() {
        var source = new Fixture();
        source.add(10L, null, "father", 0);
        source.add(11L, 10L, "age", 0);
        source.add(12L, null, "mother", 1);
        source.add(13L, 12L, "age", 0);
        Map<String, Object> config = source.snapshot();
        var target = new Fixture();
        var applied = target.adapter.apply(new ApprovalApplyContext(1L, null, 1, "CREATE",
                ResourceSnapshot.ofJson(CanonicalJson.write(config)), "tester", null));
        return new Imported(config, target.snapshot(), applied.fieldIdMapping());
    }

    private static List<String> codes(Map<String, Object> config) {
        return fields(config).stream().map(field -> String.valueOf(field.get("varCode"))).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fields(Map<String, Object> config) {
        return (List<Map<String, Object>>) config.get("fields");
    }

    private record Imported(Map<String, Object> source, Map<String, Object> target, Map<Long, Long> mapping) { }

    /** The projection query must follow the real mapper's sortOrder,id ordering, not insertion order. */
    private static class Fixture {
        final Map<Long, RuleDataObjectField> fields = new LinkedHashMap<>();
        final DataObjectGovernedResourceAdapter adapter;

        Fixture() {
            AtomicLong ids = new AtomicLong(100);
            RuleDataObjectFieldMapper fieldMapper = (RuleDataObjectFieldMapper) Proxy.newProxyInstance(
                    RuleDataObjectFieldMapper.class.getClassLoader(), new Class<?>[]{RuleDataObjectFieldMapper.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "selectList": return fields.values().stream().sorted(Comparator
                                    .comparing(RuleDataObjectField::getSortOrder, Comparator.nullsFirst(Comparator.naturalOrder()))
                                    .thenComparing(RuleDataObjectField::getId)).toList();
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
                private RuleDataObject value = JSON.parseObject("{\"id\":9,\"projectId\":0,\"scope\":\"GLOBAL\",\"objectCode\":\"family\",\"objectLabel\":\"家庭\",\"objectType\":\"OBJECT\",\"status\":1}", RuleDataObject.class);
                public RuleDataObject load(Long id) { return value; }
                public void insert(RuleDataObject object) { object.setId(9L); value = object; }
                public void update(RuleDataObject object) { value = object; }
            };
            ProjectAuthProperties auth = new ProjectAuthProperties();
            auth.getMasterKeys().put("v1", "0123456789abcdef0123456789abcdef");
            adapter = new DataObjectGovernedResourceAdapter(store, fieldMapper, options, new GovernanceSecretCodec(new CredentialCipher(auth)));
        }

        void add(Long id, Long parentId, String code, int order) {
            RuleDataObjectField field = new RuleDataObjectField();
            field.setId(id); field.setParentFieldId(parentId); field.setObjectId(9L); field.setProjectId(0L);
            field.setScope("GLOBAL"); field.setVarCode(code); field.setVarType(parentId == null ? "OBJECT" : "NUMBER");
            field.setSortOrder(order); field.setStatus(1); fields.put(id, field);
        }

        Map<String, Object> snapshot() { return CanonicalJson.readMap(adapter.loadEffective(9L).snapshotJson()); }
    }
}
