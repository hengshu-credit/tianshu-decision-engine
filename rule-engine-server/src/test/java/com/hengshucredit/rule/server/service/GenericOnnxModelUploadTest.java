package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.model.ModelArtifactValidator;
import com.hengshucredit.rule.server.service.onnx.OnnxModelExecutionService;
import com.hengshucredit.rule.server.service.onnx.OnnxRuntimeSessionManager;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class GenericOnnxModelUploadTest {
    @Test
    public void uploadAutomaticallyBuildsExactFieldsAndExecutesFrozenModel() throws Exception {
        var manager = new OnnxRuntimeSessionManager();
        var timeout = new ModelExecutionTimeoutExecutor();
        try (var stream = getClass().getResourceAsStream("/onnx-smoke/generic-add.onnx")) {
            var service = new RuleModelService();
            var executor = new OnnxModelExecutionService(manager);
            var validator = new ModelArtifactValidator();
            ReflectionTestUtils.setField(validator, "onnxRuntimeSessionManager", manager);
            ReflectionTestUtils.setField(validator, "onnxModelExecutionService", executor);
            ReflectionTestUtils.setField(service, "onnxSessionManager", manager);
            ReflectionTestUtils.setField(service, "onnxModelExecutionService", executor);
            ReflectionTestUtils.setField(service, "modelArtifactValidator", validator);
            ReflectionTestUtils.setField(service, "executionParameterBinder", new ExecutionParameterBinder());
            ReflectionTestUtils.setField(service, "modelExecutionTimeoutExecutor", timeout);
            var file = new MockMultipartFile("file", "generic.onnx", "application/octet-stream", stream.readAllBytes());
            var model = service.buildUploadSnapshot(file, null, "GLOBAL", "Generic_Model", "通用模型",
                    null, null, null, null, null, 0, 120000);
            assertEquals("ML", model.getModelType());
            assertEquals("GENERIC", com.alibaba.fastjson.JSON.parseObject(model.getModelConfig()).getString("onnxTaskType"));
            assertEquals(List.of("Feature.Input", "Offset"), model.getInputFields().stream().map(f -> f.getFieldName()).toList());
            assertTrue(model.getInputFields().stream().allMatch(f -> "LIST".equals(f.getFieldType()) && f.getScriptName() == null));
            assertEquals(List.of("Score.Output", "Original"), model.getOutputFields().stream().map(f -> f.getFieldName()).toList());
            assertTrue(model.getValidationReportJson().contains("NOT_PROVIDED"));
            var result = service.executeSnapshot(model, Map.of("Feature.Input", List.of(List.of(1, 2)),
                    "Offset", List.of(List.of(10, 20))), Map.of());
            assertEquals(Boolean.TRUE, result.get("success"));
            assertEquals(List.of(List.of(11f, 22f)), ((Map<?, ?>) result.get("outputs")).get("Score.Output"));
            var labelled = service.buildUploadSnapshot(file, null, "GLOBAL", "Generic_Model", "通用模型",
                    "XGBOOST", null, null, "GENERIC", "{}", 0, 120000);
            assertEquals("XGBOOST", labelled.getModelType());
            try (var typed = getClass().getResourceAsStream("/onnx-smoke/generic-types.onnx")) {
                var typedModel = service.buildUploadSnapshot(new MockMultipartFile("file", "types.onnx",
                        "application/octet-stream", typed.readAllBytes()), null, "GLOBAL", "types", "类型模型",
                        null, null, null, null, null, 0, 120000);
                assertEquals("LONG", typedModel.getInputFields().stream()
                        .filter(f -> "scalarLong".equals(f.getFieldName())).findFirst().orElseThrow().getFieldType());
                var typedResult = service.executeSnapshot(typedModel, Map.of("count", List.of(9007199254740993L),
                        "enabled", List.of(true, false), "name", List.of("原样", "A"), "scalar", 2.5d,
                        "unsigned", List.of(0, 128, 255), "scalarLong", 9007199254740993L), Map.of());
                assertEquals(Boolean.TRUE, typedResult.get("success"));
                assertEquals(9007199254740993L, ((Map<?, ?>) typedResult.get("outputs")).get("scalarLongOut"));
                assertEquals(List.of(0, 128, 255), ((Map<?, ?>) typedResult.get("outputs")).get("unsignedOut"));
            }
        } finally { timeout.close(); manager.close(); }
    }
}
