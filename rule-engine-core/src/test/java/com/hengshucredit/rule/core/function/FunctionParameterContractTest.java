package com.hengshucredit.rule.core.function;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class FunctionParameterContractTest {
    private final ClassLoader loader = getClass().getClassLoader();

    @Test public void emptyDatabaseValueAndEmptyArrayRetainZeroArgumentMeaning() {
        assertArrayEquals(new Class<?>[0], FunctionParameterContract.resolveTypes(null, loader));
        assertArrayEquals(new Class<?>[0], FunctionParameterContract.resolveTypes(" ", loader));
        assertArrayEquals(new Class<?>[0], FunctionParameterContract.resolveTypes("[]", loader));
    }

    @Test public void explicitTypesPreservePrimitiveWrapperArrayAndInterfaceDistinctions() {
        String[] names = {"int", "java.lang.Integer", "java.math.BigDecimal", "java.util.List", "java.util.Map", "[I", "[Ljava.lang.String;"};
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) json.append(',');
            json.append("{\"name\":\"p").append(i).append("\",\"type\":\"OBJECT\",\"javaType\":\"").append(names[i]).append("\"}");
        }
        assertArrayEquals(new Class<?>[]{int.class, Integer.class, BigDecimal.class, List.class, Map.class, int[].class, String[].class},
                FunctionParameterContract.resolveTypes(json.append(']').toString(), loader));
    }

    @Test public void brokenOrPartialContractsAreRejectedRatherThanDropped() {
        for (String json : List.of("null", "{}", "[null]", "[1]", "[{}]", "[{\"name\":1}]",
                "[{\"name\":\"a\",\"javaType\":null}]", "[{\"name\":\"a\",\"javaType\":\" double\"}]",
                "[{\"name\":\"a\",\"type\":5}]", "[{\"name\":\"a\"},{\"name\":\"a\"}]",
                "[{\"name\":\"a\",\"javaType\":\"double\"},{\"name\":\"b\"}]", "[] trailing")) {
            assertThrows(json, IllegalArgumentException.class, () -> FunctionParameterContract.parse(json));
        }
        assertThrows(IllegalArgumentException.class, () -> FunctionParameterContract.resolveTypes(
                "[{\"name\":\"a\",\"javaType\":\"void\"}]", loader));
        assertThrows(IllegalArgumentException.class, () -> FunctionParameterContract.resolveTypes(
                "[{\"name\":\"a\",\"javaType\":\"no.such.Type\"}]", loader));
    }

    @Test public void namesAndTypesRetainOrderAndKeepBusinessAndJvmTypesSeparate() {
        String json = "[{\"name\":\"second\",\"type\":\"DOUBLE\",\"javaType\":\"int\",\"label\":\"原始名称\"},{\"name\":\"first\",\"type\":\"NUMBER\",\"javaType\":\"java.math.BigDecimal\"}]";
        assertEquals(List.of("second", "first"), FunctionParameterContract.names(json));
        assertEquals("DOUBLE", FunctionParameterContract.parse(json).get(0).type());
        assertEquals("int", FunctionParameterContract.parse(json).get(0).javaType());
    }
}
