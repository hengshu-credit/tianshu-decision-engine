package com.hengshucredit.rule.core.engine;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuleTerminationResultCollectorTest {

    @Test
    public void collectsAssignedNestedAndMissingRootOutputsInOrder() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "杭州");
        Map<String, Object> applicant = new LinkedHashMap<>();
        applicant.put("address", address);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("decision", "STOP");
        values.put("applicant", applicant);

        Map<String, Object> result = RuleTerminationResultCollector.collect(
                values, Arrays.asList("decision", "applicant.address.city", "notAssigned"));

        assertEquals(Arrays.asList("decision", "applicant.address.city", "notAssigned"),
                new java.util.ArrayList<>(result.keySet()));
        assertEquals("STOP", result.get("decision"));
        assertEquals("杭州", result.get("applicant.address.city"));
        assertTrue(result.containsKey("notAssigned"));
        assertEquals(null, result.get("notAssigned"));
    }
}
