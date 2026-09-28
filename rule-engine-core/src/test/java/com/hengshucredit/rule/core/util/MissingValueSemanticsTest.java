package com.hengshucredit.rule.core.util;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MissingValueSemanticsTest {

    @Test
    public void emptyStringsStayStringsWhileContainersBecomeNull() {
        assertEquals("", MissingValueSemantics.normalize(""));
        assertEquals("  \t", MissingValueSemantics.normalize("  \t"));
        assertNull(MissingValueSemantics.normalize(List.of()));
        assertNull(MissingValueSemantics.normalize(Map.of()));
    }

    @Test
    public void nonBlankStringContentIsPreserved() {
        String value = "  Infinity  ";
        assertSame(value, MissingValueSemantics.normalize(value));
    }

    @Test
    public void infinitiesUseFiniteTypeBounds() {
        assertEquals(Double.MAX_VALUE, MissingValueSemantics.normalize(Double.POSITIVE_INFINITY));
        assertEquals(-Double.MAX_VALUE, MissingValueSemantics.normalize(Double.NEGATIVE_INFINITY));
        assertEquals(Float.MAX_VALUE, MissingValueSemantics.normalize(Float.POSITIVE_INFINITY));
        assertEquals(-Float.MAX_VALUE, MissingValueSemantics.normalize(Float.NEGATIVE_INFINITY));
        assertNull(MissingValueSemantics.normalize(Double.NaN));
        assertNull(MissingValueSemantics.normalize(Float.NaN));
        assertEquals("NaN", MissingValueSemantics.normalize("NaN"));
        assertEquals("null", MissingValueSemantics.normalize("null"));
    }

    @Test
    public void missingContainersAreRecognized() {
        assertTrue(MissingValueSemantics.isMissing(null));
        assertTrue(MissingValueSemantics.isMissing(" \n"));
        assertTrue(MissingValueSemantics.isMissing(""));
        assertTrue(MissingValueSemantics.isMissing(List.of()));
        assertTrue(MissingValueSemantics.isMissing(Map.of()));
    }
}
