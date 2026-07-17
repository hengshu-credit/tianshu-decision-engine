package com.hengshucredit.rule.server.service.onnx;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class OnnxTaskConfigTest {

    @Test
    public void parsesTaskAndAppliesDetectorDefaults() {
        OnnxTaskConfig config = OnnxTaskConfig.parse("{\"onnxTaskType\":\"SCRFD_FACE_DETECTION\"}");

        assertEquals(OnnxTaskType.SCRFD_FACE_DETECTION, config.getTaskType());
        assertEquals(0.5d, config.getDouble("confidenceThreshold"), 0d);
        assertEquals(0.4d, config.getDouble("nmsThreshold"), 0d);
        assertEquals(640, config.getInt("inputWidth"));
        assertEquals(640, config.getInt("inputHeight"));
    }

    @Test
    public void taskTemplatesExposeStableLogicalFields() {
        assertEquals(Arrays.asList("image"), OnnxTaskType.YUNET_FACE_DETECTION.inputNames());
        assertEquals(Arrays.asList("faces"), OnnxTaskType.YUNET_FACE_DETECTION.outputNames());
        assertEquals(Arrays.asList("image", "faces"), OnnxTaskType.FACENOX_ANTISPOOF.inputNames());
        assertEquals(Arrays.asList("results", "rawOutputs"), OnnxTaskType.FACENOX_ANTISPOOF.outputNames());
        assertEquals(Arrays.asList("image", "faces"), OnnxTaskType.ARCFACE_RECOGNITION.inputNames());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingTaskType() {
        OnnxTaskConfig.parse("{}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownTaskType() {
        OnnxTaskConfig.parse("{\"onnxTaskType\":\"UNKNOWN\"}");
    }
}
