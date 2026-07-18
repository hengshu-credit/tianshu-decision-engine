package com.hengshucredit.rule.server.service.onnx;

import org.junit.Test;

import java.io.InputStream;
import java.util.Arrays;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OnnxTestAssetsTest {

    @Test
    public void allOnnxTestAssetsAreAvailableFromClasspath() throws Exception {
        for (String resource : Arrays.asList(
                "/assets/docs/face.jpg",
                "/assets/onnx/yunet/detector.onnx",
                "/assets/onnx/facenox/best_model.onnx",
                "/assets/onnx/mn3/anti-spoof-mn3.onnx",
                "/assets/onnx/buffalo_l/det_10g.onnx",
                "/assets/onnx/buffalo_l/w600k_r50.onnx",
                "/assets/onnx/buffalo_l/2d106det.onnx",
                "/assets/onnx/buffalo_l/1k3d68.onnx",
                "/assets/onnx/buffalo_l/genderage.onnx")) {
            try (InputStream input = OnnxTestAssetsTest.class.getResourceAsStream(resource)) {
                assertNotNull("missing classpath resource: " + resource, input);
                assertTrue("empty classpath resource: " + resource, input.read() >= 0);
            }
        }
    }
}
