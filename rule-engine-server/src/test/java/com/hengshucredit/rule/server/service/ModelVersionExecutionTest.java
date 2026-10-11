package com.hengshucredit.rule.server.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.model.dto.RuleTestSchema;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleModelVersion;
import com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService;
import com.hengshucredit.rule.server.artifact.RuleDependencyClosureService;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.controller.RuleModelController;
import com.hengshucredit.rule.server.service.onnx.OnnxModelExecutionService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class ModelVersionExecutionTest {
    @Test
    public void versionExecutionHttpPreservesBusinessNumberTokens() throws Exception {
        var received = new java.util.concurrent.atomic.AtomicReference<Map<String, Object>>();
        RuleModelService service = new RuleModelService() {
            @Override public RuleModel getDetail(Long id) { return model(1, "OldAge", 1); }
            @Override public Map<String, Object> executeVersion(Long id, Integer version, Map<String, Object> params) {
                received.set(params); return Map.of("success", true, "version", version);
            }
        };
        RuleModelController controller = new RuleModelController();
        ReflectionTestUtils.setField(controller, "modelService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/api/rule/model/execute/7").param("version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":9007199254740993,\"rate\":0.1234567890123456789}"));
        assertNotNull(received.get());
        assertEquals(9007199254740993L, ((Number) received.get().get("customerId")).longValue());
        assertEquals(new BigDecimal("0.1234567890123456789"), received.get().get("rate"));
    }
    @Test
    public void pmmlVersionUsesItsOwnFileAndMappingsAfterCurrentConfigurationChanges() {
        try (Fixture fixture = new Fixture()) {
            RuleModel model = model(1, "OldAge", 1);
            RuleModelVersion first = fixture.capture(model);
            model.setCurrentVersion(2);
            model.setModelContent(pmml(100));
            model.setModelDigest(null);
            model.getInputFields().get(0).setSourceOperand(path("NewAge"));
            RuleModelVersion second = fixture.capture(model);
            assertScore(5, fixture.tests.execute(first, Map.of("OldAge", 2, "NewAge", 99)));
            assertScore(106, fixture.tests.execute(second, Map.of("OldAge", 99, "NewAge", 3)));
            assertScore(9, fixture.tests.execute(first, Map.of("OldAge", 4)));
            assertEquals(1, fixture.tests.context(first).get("version"));
            assertNotEquals(fixture.tests.context(first).get("configurationDigest"), fixture.tests.context(second).get("configurationDigest"));
        }
    }

    @Test
    public void onnxVersionPassesFrozenBytesConfigurationAndBoundInputToExecutor() {
        try (Fixture fixture = new Fixture()) {
            List<String> calls = new java.util.ArrayList<>();
            ReflectionTestUtils.setField(fixture.models, "onnxModelExecutionService", new OnnxModelExecutionService(null) {
                @Override public Map<String, Object> execute(byte[] bytes, String config, Map<String, Object> params) {
                    calls.add(bytes[0] + ":" + config + ":" + params.get("exact_input_name"));
                    return Map.of("prediction", ((Number) params.get("exact_input_name")).doubleValue());
                }
            });
            RuleModel model = model(1, "OldAge", 0);
            model.setModelFormat("ONNX"); model.setModelContent("AQ==");
            model.setModelConfig("{\"executionProvider\":\"CPU\",\"onnxTaskType\":\"YUNET_FACE_DETECTION\"}");
            RuleModelVersion first = fixture.capture(model);
            model.setCurrentVersion(2); model.setModelDigest(null); model.setModelContent("Ag==");
            model.setModelConfig("{\"executionProvider\":\"CUDA\",\"cudaDeviceId\":2}");
            fixture.capture(model);
            fixture.tests.execute(first, Map.of("OldAge", 12));
            assertEquals(1, calls.size());
            assertTrue(calls.get(0), calls.get(0).startsWith("1:"));
            assertTrue(calls.get(0), calls.get(0).contains("CPU"));
            assertFalse(calls.get(0), calls.get(0).contains("CUDA"));
            assertTrue(calls.get(0), calls.get(0).endsWith(":12.0"));
        }
    }

    @Test
    public void absentOrTamperedHistoricalSnapshotIsExplicitlyUnavailable() {
        try (Fixture fixture = new Fixture()) {
            RuleModelVersion empty = new RuleModelVersion(); empty.setModelId(7L); empty.setVersion(1);
            assertTrue(assertThrows(IllegalArgumentException.class, () -> fixture.tests.context(empty)).getMessage().contains("完整执行快照"));
            RuleModelVersion snapshot = fixture.capture(model(1, "OldAge", 1));
            snapshot.setExecutionSnapshotJson(snapshot.getExecutionSnapshotJson().replace("OldAge", "NewAge"));
            assertTrue(assertThrows(IllegalArgumentException.class, () -> fixture.tests.execute(snapshot, Map.of())).getMessage().contains("摘要"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void contextStringifiesOnlyIdentityFieldsAndRetainsBusinessSampleTypes() {
        try (Fixture fixture = new Fixture()) {
            RuleModel model = model(1, "OldAge", 1);
            model.setId(9007199254740993L);
            model.getInputFields().get(0).setId(9007199254740995L);
            model.getInputFields().get(0).setModelId(model.getId());
            model.getOutputFields().get(0).setModelId(model.getId());
            fixture.sample = new LinkedHashMap<>(Map.of("customerId", 9007199254740997L,
                    "rate", new BigDecimal("0.1234567890123456789")));
            Map<String, Object> context = fixture.tests.context(fixture.capture(model));
            assertEquals("9007199254740993", context.get("modelId"));
            Map<String, Object> metadata = (Map<String, Object>) context.get("model");
            assertEquals("9007199254740993", metadata.get("id"));
            assertEquals("9007199254740995", ((Map<?, ?>) ((List<?>) metadata.get("inputFields")).get(0)).get("id"));
            RuleTestSchema schema = (RuleTestSchema) context.get("schema");
            assertTrue(schema.getSampleParams().get("customerId") instanceof Number);
            assertEquals("9007199254740997", schema.getSampleParams().get("customerId").toString());
            assertEquals(new BigDecimal("0.1234567890123456789"), schema.getSampleParams().get("rate"));
        }
    }

    private static void assertScore(double expected, Map<String, Object> result) {
        assertEquals(Boolean.TRUE, result.get("success"));
        assertEquals(expected, ((Number) ((Map<?, ?>) result.get("outputs")).get("prediction")).doubleValue(), 0d);
    }

    static class Fixture implements AutoCloseable {
        final ModelVersionTestService tests = new ModelVersionTestService();
        final RuleModelService models = new RuleModelService();
        final ModelExecutionTimeoutExecutor timeouts = new ModelExecutionTimeoutExecutor();
        Map<String, Object> sample = Map.of("OldAge", 2);

        Fixture() {
            VariableSourceResolver resolver = new VariableSourceResolver();
            ReflectionTestUtils.setField(resolver, "ruleModelService", models);
            ReflectionTestUtils.setField(models, "executionParameterBinder", new ExecutionParameterBinder());
            ReflectionTestUtils.setField(models, "modelExecutionTimeoutExecutor", timeouts);
            ReflectionTestUtils.setField(tests, "variableSourceResolver", resolver);
            ReflectionTestUtils.setField(tests, "dependencyClosure", new RuleDependencyClosureService());
            ReflectionTestUtils.setField(tests, "runtimeSnapshots", new ArtifactRuntimeSnapshotService());
            ReflectionTestUtils.setField(tests, "objectMapper", JsonMapper.builder().addModule(new JavaTimeModule()).build());
            RuleTestSchemaService schemas = new RuleTestSchemaService() {
                @Override public RuleTestSchema build(com.hengshucredit.rule.model.dto.ResolutionPlan plan) {
                    RuleTestSchema schema = super.build(plan);
                    schema.setSampleParams(sample);
                    return schema;
                }
            };
            FieldDependencyResolver fields = new FieldDependencyResolver();
            ReflectionTestUtils.setField(fields, "ruleFieldAnalyzer", new RuleFieldAnalyzer());
            ReflectionTestUtils.setField(schemas, "fieldDependencyResolver", fields);
            ReflectionTestUtils.setField(tests, "schemas", schemas);
        }

        RuleModelVersion capture(RuleModel model) {
            String raw = tests.capture(model);
            RuleModelVersion version = new RuleModelVersion();
            version.setModelId(model.getId()); version.setVersion(model.getCurrentVersion());
            version.setModelContent(model.getModelContent()); version.setModelConfig(model.getModelConfig());
            version.setModelFormat(model.getModelFormat()); version.setModelDigest(model.getModelDigest());
            version.setExecutionSnapshotJson(raw); version.setExecutionSnapshotDigest(Sha256Digests.text(raw));
            return version;
        }

        @Override public void close() { timeouts.close(); RuntimeContextBridge.clear(); }
    }

    static RuleModel model(int version, String inputPath, int intercept) {
        RuleModel model = new RuleModel();
        model.setId(7L); model.setProjectId(3L); model.setScope("PROJECT");
        model.setModelCode("HistoricalModel"); model.setModelName("历史模型");
        model.setCurrentVersion(version); model.setPublishedVersion(version); model.setExecutionTimeoutMs(5000); model.setStatus(1);
        model.setModelFormat("PMML"); model.setModelContent(pmml(intercept)); model.setModelConfig("{}");
        RuleModelInputField input = new RuleModelInputField();
        input.setId(51L); input.setModelId(7L); input.setFieldName("exact_input_name");
        input.setFieldType("DOUBLE"); input.setSourceOperand(path(inputPath)); input.setStatus(1);
        RuleModelOutputField output = new RuleModelOutputField();
        output.setId(61L); output.setModelId(7L); output.setFieldName("prediction");
        output.setFieldType("DOUBLE"); output.setTargetOperand(path("Result.score"));
        model.setInputFields(List.of(input)); model.setOutputFields(List.of(output));
        return model;
    }

    private static String path(String path) { return "{\"kind\":\"PATH\",\"value\":\"" + path + "\"}"; }

    private static String pmml(int intercept) {
        String xml = "<PMML xmlns=\"http://www.dmg.org/PMML-4_4\" version=\"4.4\"><Header/>"
                + "<DataDictionary numberOfFields=\"2\"><DataField name=\"exact_input_name\" optype=\"continuous\" dataType=\"double\"/>"
                + "<DataField name=\"prediction\" optype=\"continuous\" dataType=\"double\"/></DataDictionary>"
                + "<RegressionModel functionName=\"regression\"><MiningSchema><MiningField name=\"exact_input_name\"/>"
                + "<MiningField name=\"prediction\" usageType=\"target\"/></MiningSchema><RegressionTable intercept=\"" + intercept + "\">"
                + "<NumericPredictor name=\"exact_input_name\" coefficient=\"2\"/></RegressionTable></RegressionModel></PMML>";
        return Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
    }
}
