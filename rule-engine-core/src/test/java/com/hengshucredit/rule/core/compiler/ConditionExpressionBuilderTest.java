package com.hengshucredit.rule.core.compiler;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConditionExpressionBuilderTest {

    @Test
    public void compilesRegexArrayElementMapAndSizeOperators() {
        assertEquals("regexMatchValue(name, \"^VIP\")",
                ConditionExpressionBuilder.build("name", "STRING", "regex_match", "^VIP", false));
        assertEquals("!regexMatchValue(name, pattern)",
                ConditionExpressionBuilder.build("name", "STRING", "not_regex_match", "pattern", true));
        assertEquals("containsValue(allowedCodes, code)",
                ConditionExpressionBuilder.build("code", "STRING", "in_array", "allowedCodes", true));
        assertEquals("containsElementValue(tags, \"VIP\")",
                ConditionExpressionBuilder.build("tags", "LIST", "array_element_contains", "VIP", false));
        assertEquals("elementStartsWithValue(tags, \"VIP\")",
                ConditionExpressionBuilder.build("tags", "LIST", "array_element_starts_with", "VIP", false));
        assertEquals("hasMapValue(attributes, \"gold\")",
                ConditionExpressionBuilder.build("attributes", "MAP", "has_value", "gold", false));
        assertEquals("sizeOfValue(items) >= 3",
                ConditionExpressionBuilder.build("items", "LIST", "size_gte", "3", false));
    }

    @Test
    public void compilesServerListMembershipAgainstListQueryConfig() {
        assertEquals("isInLists(customer.mobile, [10, 20])",
                ConditionExpressionBuilder.build("customer.mobile", "STRING", "in_list", "[10, 20]", true));
        assertEquals("!isInLists(customer.mobile, [10, 20])",
                ConditionExpressionBuilder.build("customer.mobile", "STRING", "not_in_list", "[10, 20]", true));
    }

    @Test
    public void dateComparisonsNormalizeBothOperandsToEpochMillis() {
        assertEquals("dateToMillis(applyDate) >= dateToMillis(\"2026-07-20\")",
                ConditionExpressionBuilder.build("applyDate", "DATE", ">=", "2026-07-20", false));
        assertEquals("dateToMillis(applyDate) < dateToMillis(cutoffDate)",
                ConditionExpressionBuilder.build("applyDate", "DATE", "<", "cutoffDate", true));
        assertEquals("(dateToMillis(applyDate) >= dateToMillis(\"2026-01-01\") && dateToMillis(applyDate) <= dateToMillis(\"2026-12-31\"))",
                ConditionExpressionBuilder.build("applyDate", "DATE", "between", "2026-01-01,2026-12-31", false));
    }
}
