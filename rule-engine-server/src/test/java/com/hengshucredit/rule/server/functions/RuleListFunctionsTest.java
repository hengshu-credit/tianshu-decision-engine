package com.hengshucredit.rule.server.functions;

import com.hengshucredit.rule.server.service.ListMatchMatrix;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleListFunctionsTest {

    @Test
    public void singleValueCanQueryOneOrManyListsAndReturnBooleanOrNumber() {
        RuleListFunctions functions = new RuleListFunctions(new StubMatrix(true));

        assertTrue(functions.isInLists("13800138000", 10L));
        assertTrue(functions.isInLists("13800138000", Arrays.asList(10L, 20L)));
        assertEquals(1, functions.isInListsNumber("13800138000", "[10,20]"));
    }

    @Test
    public void advancedFunctionAcceptsMultipleValuesAndCombinationMode() {
        StubMatrix matrix = new StubMatrix(false);
        RuleListFunctions functions = new RuleListFunctions(matrix);

        assertFalse(functions.listMatch(Arrays.asList("mobile", "idCard"), Arrays.asList(10L, 20L),
                "ALL_FIELDS_ANY_LIST", "IN_LIST", Arrays.asList("MOBILE", "ID_CARD")));
        assertEquals(Arrays.<Object>asList("mobile", "idCard"), matrix.values);
        assertEquals(Arrays.asList(10L, 20L), matrix.listIds);
        assertEquals("ALL_FIELDS_ANY_LIST", matrix.combinationMode);
    }

    private static class StubMatrix extends ListMatchMatrix {
        private final boolean result;
        private List<Long> listIds;
        private List<Object> values;
        private String combinationMode;

        private StubMatrix(boolean result) {
            super(null);
            this.result = result;
        }

        @Override
        public boolean match(List<Long> listIds, List<Object> values, String combinationMode,
                             String matchMode, List<String> itemTypes, java.time.LocalDateTime matchTime) {
            this.listIds = listIds;
            this.values = values;
            this.combinationMode = combinationMode;
            return result;
        }
    }
}
