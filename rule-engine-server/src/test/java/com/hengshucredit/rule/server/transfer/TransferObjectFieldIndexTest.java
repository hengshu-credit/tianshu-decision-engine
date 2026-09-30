package com.hengshucredit.rule.server.transfer;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TransferObjectFieldIndexTest {
    @Test public void sameNamedChildrenMatchOnlyTheirFullParentChain() {
        var source = index(field(1, null, "father", "OBJECT", null), field(2, 1L, "age", "NUMBER", null),
                field(3, null, "mother", "OBJECT", null), field(4, 3L, "age", "NUMBER", null));
        var target = index(field(40, 30L, "age", "NUMBER", null), field(30, null, "mother", "OBJECT", null),
                field(20, 10L, "age", "NUMBER", null), field(10, null, "father", "OBJECT", null));
        assertEquals(Map.of(1L, 10L, 2L, 20L, 3L, 30L, 4L, 40L), source.matchingIds(target));
    }

    @Test public void punctuationAndCaseInOriginalCodesDoNotCreatePathCollisions() {
        var source = index(field(1, null, "a.b", "NUMBER", null), field(2, null, "a", "OBJECT", null),
                field(3, 2L, "b", "NUMBER", null), field(4, null, "A.B", "NUMBER", null),
                field(5, null, "[\"a\",\"b\"]/c", "NUMBER", null));
        var target = index(field(50, null, "[\"a\",\"b\"]/c", "NUMBER", null), field(40, null, "A.B", "NUMBER", null),
                field(30, 20L, "b", "NUMBER", null), field(20, null, "a", "OBJECT", null), field(10, null, "a.b", "NUMBER", null));
        assertNotEquals(source.path(1L), source.path(3L));
        assertNotEquals(source.path(1L), source.path(4L));
        assertEquals(Map.of(1L, 10L, 2L, 20L, 3L, 30L, 4L, 40L, 5L, 50L), source.matchingIds(target));
    }

    @Test public void ancestorTypeAndGenericTypeChangesAreNotIdentityMatches() {
        var source = index(field(1, null, "people", "LIST", "OBJECT"), field(2, 1L, "age", "NUMBER", null));
        for (var changed : List.of(field(10, null, "people", "ARRAY", "OBJECT"),
                field(10, null, "people", "LIST", "MAP"), field(10, null, "People", "LIST", "OBJECT"))) {
            assertTrue(source.matchingIds(index(changed, field(20, 10L, "age", "NUMBER", null))).isEmpty());
        }
    }

    @Test public void missingAndEmptyGenericTypesAreKeptDistinct() {
        assertTrue(index(field(1, null, "value", "LIST", null))
                .matchingIds(index(field(2, null, "value", "LIST", ""))).isEmpty());
    }

    @Test public void duplicateIdsAndDuplicateStructuralPathsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> index(field(1, null, "a", "NUMBER", null), field(1, null, "b", "NUMBER", null)));
        assertThrows(IllegalArgumentException.class, () -> index(field(1, null, "a", "NUMBER", null), field(2, null, "a", "NUMBER", null)));
    }

    @Test public void missingParentAndCyclesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> index(field(1, 2L, "a", "NUMBER", null)));
        assertThrows(IllegalArgumentException.class, () -> index(field(1, 1L, "a", "OBJECT", null)));
        assertThrows(IllegalArgumentException.class, () -> index(field(1, 2L, "a", "OBJECT", null), field(2, 1L, "b", "OBJECT", null)));
    }

    @Test public void missingIdentityAndMalformedIdsAreRejected() {
        Map<String, Object> missing = new LinkedHashMap<>(field(1, null, "a", "NUMBER", null)); missing.remove("id");
        assertThrows(IllegalArgumentException.class, () -> index(missing));
        Map<String, Object> malformed = new LinkedHashMap<>(field(1, null, "a", "NUMBER", null)); malformed.put("id", "1.5");
        assertThrows(IllegalArgumentException.class, () -> index(malformed));
    }

    @Test public void indexingDoesNotChangeUserFieldValuesOrPhysicalArrayOrder() {
        Map<String, Object> second = field(2, null, "z", "STRING", null); second.put("sortOrder", 8);
        Map<String, Object> first = field(1, null, "a", "STRING", null); first.put("sortOrder", 3);
        List<Map<String, Object>> fields = List.of(second, first);
        var indexed = TransferObjectFieldIndex.of(Map.of("fields", fields));
        assertEquals(TransferObjectFieldIndex.of(Map.of("fields", List.of(first, second))).sortedFields(), indexed.sortedFields());
        assertEquals(List.of(second, first), fields);
        assertEquals(8, second.get("sortOrder"));
        assertEquals(2L, second.get("id"));
    }

    @SafeVarargs private static TransferObjectFieldIndex index(Map<String, Object>... fields) {
        return TransferObjectFieldIndex.of(Map.of("fields", List.of(fields)));
    }

    private static Map<String, Object> field(long id, Long parent, String code, String type, String generic) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id); value.put("parentFieldId", parent); value.put("varCode", code);
        value.put("varType", type); value.put("genericType", generic);
        return value;
    }
}
