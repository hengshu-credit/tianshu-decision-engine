package com.hengshucredit.rule.server.transfer;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class OfflineDataObjectImportTest {
    @BeforeClass public static void metadata() { OfflineProjectBindingTest.metadata(); }

    @Test public void createAndOverwriteKeepSameNamedChildrenUnderTheirOwnParents() {
        for (String policy : List.of("SUFFIX", "OVERWRITE")) {
            var f = fixture();
            if ("OVERWRITE".equals(policy)) {
                f.seed("DATA_OBJECT", 200L, 0L, Map.of("objectCode", "request"));
                f.appliedFieldIds = Map.of(-1L, 100L, -2L, 101L, -3L, 200L, -4L, 201L);
            }
            f.service.apply(bundle(), options(policy), "tester");
            Map<String, Object> function = CanonicalJson.readMap(f.drafts.get(1).getSnapshotJson());
            List<?> params = (List<?>) com.alibaba.fastjson.JSON.parse((String) function.get("paramsJson"));
            assertEquals(101L, ((Number) ((Map<?, ?>) params.get(0)).get("varId")).longValue());
            assertEquals(201L, ((Number) ((Map<?, ?>) params.get(1)).get("varId")).longValue());
        }
    }

    private static OfflineProjectBindingTest.Fixture fixture() {
        var f = new OfflineProjectBindingTest.Fixture();
        f.appliedFieldIds = Map.of(10L, 100L, 11L, 101L, 12L, 200L, 13L, 201L);
        return f;
    }

    @Test public void missingAppliedFieldIdentityStopsDownstreamWrites() {
        var f = fixture();
        f.appliedFieldIds = Map.of(10L, 100L);
        var error = assertThrows(IllegalArgumentException.class, () -> f.service.apply(bundle(), options("SUFFIX"), "tester"));
        assertTrue(error.getMessage().contains("子字段无法映射"));
        assertEquals(1, f.drafts.size());
    }

    @Test public void conflictedApprovalIsNotReportedAsSuccessfulImport() {
        var f = fixture();
        f.approvalStatus = "CONFLICT";
        assertThrows(IllegalStateException.class, () -> f.service.apply(bundle(), options("OVERWRITE"), "tester"));
        assertEquals(1, f.drafts.size());
    }

    @Test public void overwriteKeepsTargetFieldIdentitiesAndRemapsImportedConsumers() {
        var f = fixture();
        f.seed("DATA_OBJECT", 200L, 0L, Map.of("objectCode", "request", "fields", List.of(
                field(80, null, "mother", "OBJECT"), field(81, 80L, "age", "NUMBER"),
                field(90, null, "father", "OBJECT"), field(91, 90L, "age", "NUMBER"))));
        f.appliedFieldIds = Map.of(80L, 80L, 81L, 81L, 90L, 90L, 91L, 91L);
        f.service.apply(bundle(), options("OVERWRITE"), "tester");
        Map<String, Object> draft = CanonicalJson.readMap(f.drafts.get(0).getSnapshotJson());
        List<?> fields = (List<?>) draft.get("fields");
        assertEquals(90L, ((Number) ((Map<?, ?>) fields.get(0)).get("id")).longValue());
        assertEquals(91L, ((Number) ((Map<?, ?>) fields.get(1)).get("id")).longValue());
        assertEquals(90L, ((Number) ((Map<?, ?>) fields.get(1)).get("parentFieldId")).longValue());
        Map<String, Object> function = CanonicalJson.readMap(f.drafts.get(1).getSnapshotJson());
        List<?> params = (List<?>) com.alibaba.fastjson.JSON.parse((String) function.get("paramsJson"));
        assertEquals(91L, ((Number) ((Map<?, ?>) params.get(0)).get("varId")).longValue());
        assertEquals(81L, ((Number) ((Map<?, ?>) params.get(1)).get("varId")).longValue());
    }

    @Test public void overwriteDoesNotMistakeCrossEnvironmentNumericCollisionForSameField() {
        var f = fixture();
        f.seed("DATA_OBJECT", 200L, 0L, Map.of("objectCode", "request", "fields", List.of(
                field(10, null, "mother", "OBJECT"), field(11, 10L, "age", "NUMBER"),
                field(12, null, "father", "OBJECT"), field(13, 12L, "age", "NUMBER"))));
        f.appliedFieldIds = Map.of(10L, 10L, 11L, 11L, 12L, 12L, 13L, 13L);
        f.service.apply(bundle(), options("OVERWRITE"), "tester");
        Map<String, Object> draft = CanonicalJson.readMap(f.drafts.get(0).getSnapshotJson());
        List<?> fields = (List<?>) draft.get("fields");
        assertEquals(12L, ((Number) ((Map<?, ?>) fields.get(0)).get("id")).longValue());
        assertEquals(13L, ((Number) ((Map<?, ?>) fields.get(1)).get("id")).longValue());
        assertEquals(10L, ((Number) ((Map<?, ?>) fields.get(2)).get("id")).longValue());
    }

    @Test public void newFieldsUseTemporaryIdentitiesEvenWhenTheirSourceIdsBelongToRetainedTargetFields() {
        var f = fixture();
        f.seed("DATA_OBJECT", 200L, 0L, Map.of("objectCode", "request", "fields", List.of(
                field(10, null, "mother", "OBJECT"), field(11, 10L, "age", "NUMBER"))));
        f.appliedFieldIds = Map.of(-1L, 100L, -2L, 101L, 10L, 10L, 11L, 11L);
        f.service.apply(bundle(), options("OVERWRITE"), "tester");
        Map<String, Object> draft = CanonicalJson.readMap(f.drafts.get(0).getSnapshotJson());
        List<?> fields = (List<?>) draft.get("fields");
        assertEquals(200L, ((Number) draft.get("id")).longValue());
        assertEquals(-1L, ((Number) ((Map<?, ?>) fields.get(0)).get("id")).longValue());
        assertEquals(-2L, ((Number) ((Map<?, ?>) fields.get(1)).get("id")).longValue());
        assertEquals(-1L, ((Number) ((Map<?, ?>) fields.get(1)).get("parentFieldId")).longValue());
        Map<String, Object> function = CanonicalJson.readMap(f.drafts.get(1).getSnapshotJson());
        List<?> params = (List<?>) com.alibaba.fastjson.JSON.parse((String) function.get("paramsJson"));
        assertEquals(101L, ((Number) ((Map<?, ?>) params.get(0)).get("varId")).longValue());
        assertEquals(11L, ((Number) ((Map<?, ?>) params.get(1)).get("varId")).longValue());
    }

    private static byte[] bundle() {
        return new TransferBundleCodec().encode(new TransferBundle(List.of("FUNCTION:2"), List.of(
                new TransferBundle.Resource("FUNCTION:2", Map.of("funcCode", "ages", "paramsJson", "[{\"varId\":11},{\"varId\":13}]"),
                        List.of(new TransferBundle.Reference("/paramsJson/@json/0/varId", "DATA_OBJECT:1", "/fields/1/id"),
                                new TransferBundle.Reference("/paramsJson/@json/1/varId", "DATA_OBJECT:1", "/fields/3/id")), List.of()),
                new TransferBundle.Resource("DATA_OBJECT:1", Map.of("objectCode", "request", "fields", List.of(
                        field(10, null, "father", "OBJECT"), field(11, 10L, "age", "NUMBER"),
                        field(12, null, "mother", "OBJECT"), field(13, 12L, "age", "NUMBER"))), List.of(), List.of())), List.of()));
    }

    private static Map<String, Object> field(long id, Long parent, String code, String type) {
        var field = new java.util.LinkedHashMap<String, Object>();
        field.put("id", id); field.put("parentFieldId", parent); field.put("varCode", code); field.put("varType", type);
        return field;
    }

    private static TransferImportOptions options(String policy) {
        return new TransferImportOptions(null, "GLOBAL", "REUSE", policy, "_copy", false, null, null, Map.of());
    }
}
