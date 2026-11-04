package com.hengshucredit.rule.server.service.onnx;

import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class GenericOnnxExecutionTest {
    private byte[] model(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/onnx-smoke/" + name + ".onnx")) {
            assertNotNull(stream);
            return stream.readAllBytes();
        }
    }

    @Test
    public void executesMultipleNamedInputsAndOutputsWithDynamicBatch() throws Exception {
        var manager = new OnnxRuntimeSessionManager();
        try {
            var result = new OnnxModelExecutionService(manager).execute(model("generic-add"),
                    "{\"onnxTaskType\":\"GENERIC\"}", Map.of(
                            "Feature.Input", List.of(List.of(1, 2), List.of(3, 4)),
                            "Offset", List.of(List.of(10, 20), List.of(30, 40))));
            assertEquals(List.of(List.of(11f, 22f), List.of(33f, 44f)), result.get("Score.Output"));
            assertEquals(List.of(List.of(1f, 2f), List.of(3f, 4f)), result.get("Original"));
        } finally { manager.close(); }
    }

    @Test
    public void rejectsMissingExtraWrongShapeAndNonNumericInputs() throws Exception {
        var manager = new OnnxRuntimeSessionManager();
        try {
            var service = new OnnxModelExecutionService(manager);
            byte[] bytes = model("generic-add");
            for (Map<String, Object> input : List.<Map<String, Object>>of(
                    Map.of("Feature.Input", List.of(List.of(1, 2))),
                    Map.of("Feature.Input", List.of(List.of(1, 2)), "Offset", List.of(List.of(1, 2)), "extra", 0),
                    Map.of("Feature.Input", List.of(1, 2), "Offset", List.of(List.of(1, 2))),
                    Map.of("Feature.Input", List.of(List.of(1, 2), List.of(3)), "Offset", List.of(List.of(1, 2))),
                    Map.of("Feature.Input", List.of(List.of("1", 2)), "Offset", List.of(List.of(1, 2))))) {
                assertThrows(IllegalArgumentException.class, () -> service.execute(bytes,
                        "{\"onnxTaskType\":\"GENERIC\"}", input));
            }
        } finally { manager.close(); }
    }

    @Test
    public void convertsClassifierZipMapToPlainJsonValues() throws Exception {
        var manager = new OnnxRuntimeSessionManager();
        try {
            var result = new OnnxModelExecutionService(manager).execute(model("generic-zipmap"),
                    "{\"onnxTaskType\":\"GENERIC\"}", Map.of("scores", List.of(List.of(0.25, 0.75))));
            assertEquals(List.of(Map.of("approved", 0.25f, "review", 0.75f)), result.get("probabilities"));
            var json = com.alibaba.fastjson.JSON.parseObject(com.alibaba.fastjson.JSON.toJSONString(result));
            assertEquals(0.25f, json.getJSONArray("probabilities").getJSONObject(0).getFloatValue("approved"), 0f);
        } finally { manager.close(); }
    }

    @Test
    public void preservesIntegerBooleanStringAndScalarTypes() throws Exception {
        var manager = new OnnxRuntimeSessionManager();
        try {
            var service = new OnnxModelExecutionService(manager);
            byte[] bytes = model("generic-types");
            Map<String, Object> input = Map.of("count", List.of(9007199254740993L),
                    "enabled", List.of(true, false), "name", List.of("原样", "A"), "scalar", 2.5d,
                    "unsigned", List.of(0, 128, 255), "scalarLong", 9007199254740993L);
            var result = service.execute(bytes, "{\"onnxTaskType\":\"GENERIC\"}", input);
            assertEquals(List.of(9007199254740993L), result.get("countOut"));
            assertEquals(List.of(true, false), result.get("enabledOut"));
            assertEquals(List.of("原样", "A"), result.get("nameOut"));
            assertEquals(2.5d, result.get("scalarOut"));
            assertEquals(List.of(0, 128, 255), result.get("unsignedOut"));
            assertEquals(9007199254740993L, result.get("scalarLongOut"));
            var invalid = new java.util.LinkedHashMap<>(input);
            invalid.put("count", List.of(1.5));
            assertThrows(IllegalArgumentException.class, () -> service.execute(bytes,
                    "{\"onnxTaskType\":\"GENERIC\"}", invalid));
        } finally { manager.close(); }
    }
}
