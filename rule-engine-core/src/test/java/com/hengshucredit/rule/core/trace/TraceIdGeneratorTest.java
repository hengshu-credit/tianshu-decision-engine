package com.hengshucredit.rule.core.trace;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TraceIdGeneratorTest {

    @Test
    public void generatesFixedWidthReadableTraceId() {
        String traceId = TraceIdGenerator.generate("DF", "P", "00A7");

        assertEquals(36, traceId.length());
        assertTrue(traceId.matches("DFP00A7\\d{17}[0-9A-Z]{12}"));
    }

    @Test
    public void mapsAllRuleModelTypesToTwoCharacterCodes() {
        assertEquals("TB", TraceIdGenerator.ruleTypeCode("TABLE"));
        assertEquals("TR", TraceIdGenerator.ruleTypeCode("TREE"));
        assertEquals("DF", TraceIdGenerator.ruleTypeCode("FLOW"));
        assertEquals("RS", TraceIdGenerator.ruleTypeCode("RULE_SET"));
        assertEquals("CT", TraceIdGenerator.ruleTypeCode("CROSS"));
        assertEquals("SC", TraceIdGenerator.ruleTypeCode("SCORE"));
        assertEquals("AC", TraceIdGenerator.ruleTypeCode("CROSS_ADV"));
        assertEquals("AS", TraceIdGenerator.ruleTypeCode("SCORE_ADV"));
        assertEquals("QL", TraceIdGenerator.ruleTypeCode("SCRIPT"));
    }

    @Test
    public void convertsProjectIdToFixedBase36ScopeCode() {
        assertEquals("0001", TraceIdGenerator.projectScopeCode(1L));
        assertEquals("00A7", TraceIdGenerator.projectScopeCode(367L));
        assertEquals("ZZZZ", TraceIdGenerator.projectScopeCode(1679615L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsProjectIdOutsideFourCharacterCapacity() {
        TraceIdGenerator.projectScopeCode(1679616L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidFixedWidthSegments() {
        TraceIdGenerator.generate("RULE", "P", "A7");
    }
}
