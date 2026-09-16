package com.hengshucredit.rule.server.service.parser;

import org.junit.Test;

import static org.junit.Assert.*;

public class JavaEntityParserTest {
    @Test
    public void entityArraysAndGenericWhitespaceKeepNamesAndTypes() {
        var fields = new JavaEntityParser().parseEntities("public class Mixed_Request { private String[] Tags; private Map<String, Object> Attributes; }").get(0).getFields();
        assertEquals(2, fields.size());
        assertEquals("Tags", fields.get(0).getFieldName());
        assertEquals("LIST", fields.get(0).getVarType());
        assertEquals("STRING", fields.get(0).getGenericType());
        assertEquals("Attributes", fields.get(1).getFieldName());
        assertEquals("MAP", fields.get(1).getVarType());
    }

    @Test
    public void constantStringExpressionsAreNotSilentlyStoredAsText() {
        assertThrows(IllegalArgumentException.class, () -> new JavaEntityParser().parseConstants(
                "public class Limits { public static final String VALUE = \"A\" + \"B\"; }"));
    }

    @Test
    public void constantsSupportEscapesArraysAndMapDeclarationsWithSpaces() {
        var constants = new JavaEntityParser().parseConstants("public class Limits { public static final String LINE = \"a\\nb\"; public static final int[] VALUES = {1, 2}; public static final Map<String, Integer> LIMITS = Map.of(\"max\", 65); }").getConstants();
        assertEquals(3, constants.size());
        assertEquals("a\nb", constants.get(0).getConstValue());
        assertEquals("[1,2]", constants.get(1).getConstValue());
        assertEquals("MAP", constants.get(2).getConstType());
        assertEquals("{\"max\":65}", constants.get(2).getConstValue());
    }
}
