package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;

public class SourceStatusUsageTest {

    @Test
    public void scansNestedModelsByStableRefTypeAndIdOnly() {
        String modelJson = "{\"rules\":[{\"conditions\":["
                + "{\"leftOperand\":{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":7,\"code\":\"apiScore\"},\"operator\":\"source_error\"},"
                + "{\"leftOperand\":{\"kind\":\"REFERENCE\",\"refType\":\"MODEL_OUTPUT\",\"refId\":9,\"code\":\"risk.score\"},\"operator\":\"source_output_missing\"},"
                + "{\"leftOperand\":{\"kind\":\"PATH\",\"code\":\"unmanaged\"},\"operator\":\"source_error\"},"
                + "{\"leftOperand\":{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":8},\"operator\":\"==\"}]}]}";

        assertEquals(new LinkedHashSet<>(Arrays.asList("VARIABLE:7", "MODEL_OUTPUT:9")),
                SourceStatusUsage.scan(modelJson));
    }

    @Test
    public void scansLegacyScorecardOperatorField() {
        String modelJson = "{\"scoreItems\":[{\"leftOperand\":{\"kind\":\"REFERENCE\","
                + "\"refType\":\"DATA_OBJECT\",\"refId\":11},\"condOperator\":\"source_field_missing\"}]}";

        assertEquals(new LinkedHashSet<>(Arrays.asList("DATA_OBJECT:11")),
                SourceStatusUsage.scan(modelJson));
    }

    @Test
    public void scansAdvancedCrossTableSegmentsAgainstParentOperand() {
        String modelJson = "{\"rowDimensions\":[{\"operand\":{\"kind\":\"REFERENCE\","
                + "\"refType\":\"VARIABLE\",\"refId\":12,\"code\":\"apiScore\"},"
                + "\"segments\":[{\"operator\":\"source_cache_hit\"},{\"operator\":\"==\"}]}]}";

        assertEquals(new LinkedHashSet<>(Arrays.asList("VARIABLE:12")),
                SourceStatusUsage.scan(modelJson));
    }
}
