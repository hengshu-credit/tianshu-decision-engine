package com.hengshucredit.rule.server.artifact;

import com.hengshucredit.rule.server.model.ModelArtifactValidator;
import com.hengshucredit.rule.server.model.ModelValidationReport;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

public class RuntimeCompatibilityServiceTest {

    @Test
    public void validatesEmbeddedModelAgainstTargetRuntime() {
        RuntimeCompatibilityService service = new RuntimeCompatibilityService();
        RecordingModelValidator validator = new RecordingModelValidator();
        ReflectionTestUtils.setField(service, "modelArtifactValidator", validator);
        DecisionArtifactPackage artifactPackage = new DecisionArtifactPackage();
        artifactPackage.addComponent("models/11/3.pmml", "application/octet-stream",
                new byte[]{1, 2, 3}, Map.of(
                        "resourceType", "MODEL",
                        "embeddingMode", "EMBEDDED",
                        "model", Map.of("modelFormat", "PMML"),
                        "inputFields", List.of(Map.of("fieldName", "CreditScore")),
                        "outputFields", List.of(Map.of("fieldName", "Decision"))));

        RuntimeCompatibilityService.CompatibilityReport report = service.validate(artifactPackage);

        Assert.assertTrue(report.getErrors().toString(), report.isCompatible());
        Assert.assertTrue(validator.called);
        Assert.assertEquals("PMML", validator.format);
        Assert.assertEquals(List.of("CreditScore"), validator.inputs);
        Assert.assertEquals(List.of("Decision"), validator.outputs);
    }

    private static final class RecordingModelValidator extends ModelArtifactValidator {
        private boolean called;
        private String format;
        private List<String> inputs;
        private List<String> outputs;

        @Override
        public ModelValidationReport validate(String format, byte[] bytes,
                                              List<String> declaredInputs,
                                              List<String> declaredOutputs,
                                              Map<String, Object> sample,
                                              String runtimeConfigJson) {
            called = true;
            this.format = format;
            inputs = declaredInputs;
            outputs = declaredOutputs;
            ModelValidationReport report = new ModelValidationReport();
            report.setValid(true);
            return report;
        }
    }
}
