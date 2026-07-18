package com.hengshucredit.rule.server.service.onnx;

import org.junit.Test;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class RepositoryPathIndependenceTest {

    @Test
    public void onnxTestsDoNotContainMachineOrWorkingDirectoryPaths() throws Exception {
        for (Class<?> testClass : Arrays.asList(
                AntispoofExecutorsTest.class,
                CudaEnvironmentDiagnosticTest.class,
                ImageTensorUtilsTest.class,
                InsightFaceExecutorsTest.class,
                OnnxModelExecutionServiceTest.class,
                OnnxRuntimeSessionManagerTest.class,
                ScrfdFaceDetectionExecutorTest.class,
                YunetFaceDetectionExecutorTest.class)) {
            String resource = "/" + testClass.getName().replace('.', '/') + ".class";
            try (InputStream input = testClass.getResourceAsStream(resource)) {
                assertNotNull(resource, input);
                String classBytes = new String(StreamUtils.copyToByteArray(input), StandardCharsets.ISO_8859_1);
                for (String forbiddenPath : Arrays.asList(
                        "C:" + "/Users/",
                        ".." + "/.tmp/",
                        ".." + "/assets/")) {
                    assertFalse(testClass.getName(), classBytes.contains(forbiddenPath));
                }
            }
        }
    }
}
