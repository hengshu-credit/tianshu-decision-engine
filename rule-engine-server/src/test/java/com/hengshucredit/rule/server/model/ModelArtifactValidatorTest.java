package com.hengshucredit.rule.server.model;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModelArtifactValidatorTest {

    @Test
    public void pmmlNamesAreComparedExactlyAndRawDigestIsReturned() {
        ModelArtifactValidator validator = new ModelArtifactValidator();
        byte[] bytes = pmml().getBytes(StandardCharsets.UTF_8);

        ModelValidationReport report = validator.validate("PMML", bytes,
                List.of("risk_score"), List.of("prediction"), null);

        Assert.assertTrue(report.isValid());
        Assert.assertEquals("NOT_PROVIDED", report.getSampleStatus());
        Assert.assertEquals(com.hengshucredit.rule.server.artifact.Sha256Digests.bytes(bytes),
                report.getModelDigest());
        Assert.assertThrows(IllegalArgumentException.class, () -> validator.validate("PMML", bytes,
                List.of("riskScore"), List.of("prediction"), null));
    }

    @Test
    public void suppliedSampleMustUseExactInputNamesAndExecute() {
        ModelArtifactValidator validator = new ModelArtifactValidator();
        byte[] bytes = pmml().getBytes(StandardCharsets.UTF_8);

        ModelValidationReport report = validator.validate("PMML", bytes,
                List.of("risk_score"), List.of("prediction"), Map.of("risk_score", 3d));

        Assert.assertEquals("PASSED", report.getSampleStatus());
        Assert.assertThrows(IllegalArgumentException.class, () -> validator.validate("PMML", bytes,
                List.of("risk_score"), List.of("prediction"), Map.of("riskScore", 3d)));
    }

    @Test
    public void onnxDeclaredNodeNamesAreCaseSensitive() {
        ModelArtifactValidator validator = new ModelArtifactValidator() {
            @Override
            protected Map<String, Object> inspectOnnx(byte[] bytes) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("inputs", Map.of("Input_Tensor", Map.of("type", "FLOAT")));
                metadata.put("outputs", Map.of("Risk_Score", Map.of("type", "FLOAT")));
                return metadata;
            }

            @Override
            protected Map<String, Object> executeOnnx(byte[] bytes, String configJson,
                                                      Map<String, Object> sample) {
                return Map.of("Risk_Score", 0.8d);
            }
        };

        Assert.assertTrue(validator.validate("ONNX", new byte[]{1, 2},
                List.of("Input_Tensor"), List.of("Risk_Score"), Map.of("Input_Tensor", 1)).isValid());
        Assert.assertThrows(IllegalArgumentException.class, () -> validator.validate("ONNX",
                new byte[]{1, 2}, List.of("input_tensor"), List.of("Risk_Score"), null));
    }

    @Test
    public void optionalSampleEnvelopeChecksExpectedOutputsWhenProvided() {
        ModelArtifactValidator validator = new ModelArtifactValidator();
        byte[] bytes = pmml().getBytes(StandardCharsets.UTF_8);
        Map<String, Object> passing = Map.of(
                "$input", Map.of("risk_score", 3d),
                "$expectedOutput", Map.of("prediction", 7d));

        ModelValidationReport report = validator.validate("PMML", bytes,
                List.of("risk_score"), List.of("prediction"), passing);

        Assert.assertEquals("PASSED", report.getSampleStatus());
        Map<String, Object> failing = Map.of(
                "$input", Map.of("risk_score", 3d),
                "$expectedOutput", Map.of("prediction", 8d));
        Assert.assertThrows(IllegalArgumentException.class, () -> validator.validate("PMML", bytes,
                List.of("risk_score"), List.of("prediction"), failing));
    }

    private static String pmml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<PMML xmlns=\"http://www.dmg.org/PMML-4_4\" version=\"4.4\">\n"
                + "<Header/><DataDictionary numberOfFields=\"2\">"
                + "<DataField name=\"risk_score\" optype=\"continuous\" dataType=\"double\"/>"
                + "<DataField name=\"prediction\" optype=\"continuous\" dataType=\"double\"/>"
                + "</DataDictionary><RegressionModel functionName=\"regression\">"
                + "<MiningSchema><MiningField name=\"risk_score\"/>"
                + "<MiningField name=\"prediction\" usageType=\"target\"/></MiningSchema>"
                + "<Output><OutputField name=\"prediction\" resultFeature=\"predictedValue\""
                + " targetField=\"prediction\"/></Output>"
                + "<RegressionTable intercept=\"1\"><NumericPredictor name=\"risk_score\""
                + " coefficient=\"2\"/></RegressionTable></RegressionModel></PMML>";
    }
}
