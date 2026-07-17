package com.hengshucredit.rule.server.service.onnx;

import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class YunetDetectorCacheTest {

    @Test
    public void reusesDetectorForSameModelAndConfiguration() throws Exception {
        Path model = Paths.get("C:/Users/Administrator/Downloads/face-antispoof-onnx-main/models/detector.onnx");
        Assume.assumeTrue(Files.isRegularFile(model));
        byte[] bytes = Files.readAllBytes(model);
        OnnxTaskConfig config = OnnxTaskConfig.parse("{\"onnxTaskType\":\"YUNET_FACE_DETECTION\"}");
        YunetDetectorCache cache = new YunetDetectorCache();

        cache.preload(bytes, config);
        cache.preload(bytes, config);

        assertEquals(1, cache.getCachedDetectorCount());
    }
}
