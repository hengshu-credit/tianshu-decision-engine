package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ListMatchMatrixTest {

    @Test
    public void evaluatesAllFourFieldAndListCombinationModes() {
        List<Long> lists = Arrays.asList(10L, 20L);
        List<Object> fields = Arrays.<Object>asList("mobile", "idCard");
        Map<String, Boolean> cells = new LinkedHashMap<>();
        cells.put("mobile@10", true);
        cells.put("mobile@20", false);
        cells.put("idCard@10", false);
        cells.put("idCard@20", true);
        ListMatchMatrix matrix = matrix(cells);

        assertTrue(matrix.match(lists, fields, "ANY_FIELD_ANY_LIST", "IN_LIST", Collections.<String>emptyList(), null));
        assertTrue(matrix.match(lists, fields, "ALL_FIELDS_ANY_LIST", "IN_LIST", Collections.<String>emptyList(), null));
        assertFalse(matrix.match(lists, fields, "ANY_FIELD_ALL_LISTS", "IN_LIST", Collections.<String>emptyList(), null));
        assertFalse(matrix.match(lists, fields, "ALL_FIELDS_ALL_LISTS", "IN_LIST", Collections.<String>emptyList(), null));

        cells.put("mobile@20", true);
        assertTrue(matrix.match(lists, fields, "ANY_FIELD_ALL_LISTS", "IN_LIST", Collections.<String>emptyList(), null));
        assertFalse(matrix.match(lists, fields, "ALL_FIELDS_ALL_LISTS", "IN_LIST", Collections.<String>emptyList(), null));
        cells.put("idCard@10", true);
        assertTrue(matrix.match(lists, fields, "ALL_FIELDS_ALL_LISTS", "IN_LIST", Collections.<String>emptyList(), null));
    }

    @Test
    public void emptyDimensionsNeverMatchAndSnapshotTimeIsForwarded() {
        TrackingListService service = new TrackingListService(Collections.singletonMap("value@10", true));
        ListMatchMatrix matrix = new ListMatchMatrix(service);
        LocalDateTime snapshot = LocalDateTime.of(2026, 7, 1, 10, 30);

        assertFalse(matrix.match(Collections.<Long>emptyList(), Collections.<Object>singletonList("value"),
                "ANY_FIELD_ANY_LIST", "IN_LIST", Collections.<String>emptyList(), snapshot));
        assertFalse(matrix.match(Collections.singletonList(10L), Collections.emptyList(),
                "ANY_FIELD_ANY_LIST", "IN_LIST", Collections.<String>emptyList(), snapshot));
        assertTrue(matrix.match(Collections.singletonList(10L), Collections.<Object>singletonList("value"),
                "ANY_FIELD_ANY_LIST", "IN_LIST", Collections.singletonList("MOBILE"), snapshot));
        assertTrue(snapshot.equals(service.lastMatchTime));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownCombinationMode() {
        matrix(Collections.<String, Boolean>emptyMap()).match(Collections.singletonList(10L),
                Collections.<Object>singletonList("value"), "UNKNOWN", "IN_LIST", Collections.<String>emptyList(), null);
    }

    private static ListMatchMatrix matrix(Map<String, Boolean> cells) {
        return new ListMatchMatrix(new TrackingListService(cells));
    }

    private static class TrackingListService extends RuleListService {
        private final Map<String, Boolean> cells;
        private LocalDateTime lastMatchTime;

        private TrackingListService(Map<String, Boolean> cells) {
            this.cells = cells;
        }

        @Override
        public boolean match(Long listId, Object content, List<String> itemTypes, String matchMode) {
            return Boolean.TRUE.equals(cells.get(String.valueOf(content) + "@" + listId));
        }

        @Override
        public boolean matchAt(Long listId, Object content, List<String> itemTypes, String matchMode, LocalDateTime matchTime) {
            lastMatchTime = matchTime;
            return match(listId, content, itemTypes, matchMode);
        }
    }
}
