package com.hengshucredit.rule.server.service.onnx;

import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class OnnxModelExecutionServiceTest {

    @Test
    public void dispatchesYunetTaskUsingLogicalImageInput() throws Exception {
        Path model = Paths.get("../.tmp/facenox-face-antispoof-onnx/models/detector.onnx");
        Path image = Paths.get("../assets/docs/face.jpg");
        Assume.assumeTrue(Files.isRegularFile(model) && Files.isRegularFile(image));
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("image", Base64.getEncoder().encodeToString(Files.readAllBytes(image)));

            Map<String, Object> output = new OnnxModelExecutionService(manager, new YunetDetectorCache()).execute(
                    Files.readAllBytes(model), "{\"onnxTaskType\":\"YUNET_FACE_DETECTION\"}", params);

            assertEquals(1, ((List<?>) output.get("faces")).size());
        } finally {
            manager.close();
        }
    }
}
