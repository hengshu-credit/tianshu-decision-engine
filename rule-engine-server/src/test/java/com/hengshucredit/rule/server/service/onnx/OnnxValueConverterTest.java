package com.hengshucredit.rule.server.service.onnx;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OnnxValueConverterTest {

    @Test
    public void convertsNestedPrimitiveArraysWithoutFlattening() {
        Object converted = OnnxValueConverter.toJava(new float[][]{{1.25f, -2.5f}, {3f, 4f}});

        assertTrue(converted instanceof List);
        assertEquals(Arrays.asList(
                Arrays.asList(1.25f, -2.5f),
                Arrays.asList(3f, 4f)), converted);
    }

    @Test
    public void preservesIntegerAndLongValues() {
        assertEquals(Arrays.asList(1, 2), OnnxValueConverter.toJava(new int[]{1, 2}));
        assertEquals(Arrays.asList(3L, 4L), OnnxValueConverter.toJava(new long[]{3L, 4L}));
    }

    @Test
    public void convertsTensorShapeToJsonSafeList() {
        assertEquals(Arrays.asList(1L, 3L, -1L, -1L),
                OnnxValueConverter.shape(new long[]{1L, 3L, -1L, -1L}));
    }
}
