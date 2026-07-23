package com.hengshucredit.rule.server.model;

import com.hengshucredit.rule.core.pmml.PMMLModelExecutor;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.service.onnx.OnnxModelExecutionService;
import com.hengshucredit.rule.server.service.onnx.OnnxRuntimeSessionManager;
import jakarta.annotation.Resource;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.InputField;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.jpmml.evaluator.OutputField;
import org.jpmml.evaluator.TargetField;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ModelArtifactValidator {
    @Resource
    private OnnxRuntimeSessionManager onnxRuntimeSessionManager;
    @Resource
    private OnnxModelExecutionService onnxModelExecutionService;

    private final PMMLModelExecutor pmmlExecutor = new PMMLModelExecutor();

    public ModelValidationReport validate(String format, byte[] bytes,
                                          List<String> declaredInputs,
                                          List<String> declaredOutputs,
                                          Map<String, Object> sample) {
        return validate(format, bytes, declaredInputs, declaredOutputs, sample, null);
    }

    public ModelValidationReport validate(String format, byte[] bytes,
                                          List<String> declaredInputs,
                                          List<String> declaredOutputs,
                                          Map<String, Object> sample,
                                          String runtimeConfigJson) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Model file must not be empty");
        }
        String modelFormat = format == null ? null : format.trim().toUpperCase(Locale.ROOT);
        if (!"PMML".equals(modelFormat) && !"ONNX".equals(modelFormat)) {
            throw new IllegalArgumentException("Only PMML and ONNX model formats are supported");
        }

        List<String> actualInputs;
        List<String> actualOutputs;
        Map<String, Object> runtime = new LinkedHashMap<>();
        if ("PMML".equals(modelFormat)) {
            Evaluator evaluator = loadPmml(bytes);
            actualInputs = evaluator.getInputFields().stream().map(InputField::getName).toList();
            actualOutputs = evaluator.getOutputFields().stream().map(OutputField::getName).toList();
            if (actualOutputs.isEmpty()) {
                actualOutputs = evaluator.getTargetFields().stream().map(TargetField::getName).toList();
            }
            runtime.put("jpmmlVersion", "1.7.7");
        } else {
            Map<String, Object> metadata = inspectOnnx(bytes);
            actualInputs = exactNodeNames(metadata.get("inputs"), "inputs");
            actualOutputs = exactNodeNames(metadata.get("outputs"), "outputs");
            runtime.put("onnxRuntimeVersion", "1.26.0");
            runtime.put("nodeMetadata", metadata);
        }

        assertExactNames("Input", declaredInputs, actualInputs);
        assertExactNames("Output", declaredOutputs, actualOutputs);

        ModelValidationReport report = new ModelValidationReport();
        report.setValid(true);
        report.setModelFormat(modelFormat);
        report.setModelDigest(Sha256Digests.bytes(bytes));
        report.setInputSchema(schema(actualInputs));
        report.setOutputSchema(schema(actualOutputs));
        report.setRuntimeConstraints(runtime);
        if (sample == null || sample.isEmpty()) {
            report.setSampleStatus("NOT_PROVIDED");
            report.getWarnings().add("No model sample was supplied; sample execution was skipped");
            return report;
        }

        SamplePayload samplePayload = samplePayload(sample);
        assertExactNames("Sample input", new ArrayList<>(samplePayload.input.keySet()), actualInputs);
        Map<String, Object> result = "PMML".equals(modelFormat)
                ? pmmlExecutor.evaluate(Base64.getEncoder().encodeToString(bytes), samplePayload.input)
                : executeOnnx(bytes, runtimeConfigJson, samplePayload.input);
        if (result == null) {
            throw new IllegalArgumentException("Model sample execution returned no result");
        }
        Set<String> missingOutputs = new LinkedHashSet<>(actualOutputs);
        missingOutputs.removeAll(result.keySet());
        if (!missingOutputs.isEmpty()) {
            throw new IllegalArgumentException("Model sample execution is missing exact output fields: "
                    + missingOutputs);
        }
        if (samplePayload.expectedOutput != null) {
            assertExactNames("Sample expected output",
                    new ArrayList<>(samplePayload.expectedOutput.keySet()), actualOutputs);
            for (String output : actualOutputs) {
                Object expected = samplePayload.expectedOutput.get(output);
                Object actual = result.get(output);
                if (!equivalent(expected, actual)) {
                    throw new IllegalArgumentException("Model sample expected output mismatch for exact field '"
                            + output + "': expected " + expected + ", actual " + actual);
                }
            }
        }
        report.setSampleStatus("PASSED");
        return report;
    }

    @SuppressWarnings("unchecked")
    private SamplePayload samplePayload(Map<String, Object> sample) {
        if (!sample.containsKey("$input") && !sample.containsKey("$expectedOutput")) {
            return new SamplePayload(sample, null);
        }
        Set<String> wrapperKeys = new LinkedHashSet<>(sample.keySet());
        wrapperKeys.remove("$input");
        wrapperKeys.remove("$expectedOutput");
        if (!wrapperKeys.isEmpty() || !(sample.get("$input") instanceof Map<?, ?> input)) {
            throw new IllegalArgumentException("Model sample envelope only supports $input and optional "
                    + "$expectedOutput objects");
        }
        Object expected = sample.get("$expectedOutput");
        if (expected != null && !(expected instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Model sample $expectedOutput must be an object");
        }
        return new SamplePayload((Map<String, Object>) input,
                expected == null ? null : (Map<String, Object>) expected);
    }

    private boolean equivalent(Object expected, Object actual) {
        if (expected instanceof Number left && actual instanceof Number right) {
            try {
                return new java.math.BigDecimal(left.toString())
                        .compareTo(new java.math.BigDecimal(right.toString())) == 0;
            } catch (NumberFormatException ignored) {
                return Double.compare(left.doubleValue(), right.doubleValue()) == 0;
            }
        }
        if (expected instanceof Map<?, ?> left && actual instanceof Map<?, ?> right) {
            if (!left.keySet().equals(right.keySet())) return false;
            for (Object key : left.keySet()) {
                if (!equivalent(left.get(key), right.get(key))) return false;
            }
            return true;
        }
        if (expected instanceof List<?> left && actual instanceof List<?> right) {
            if (left.size() != right.size()) return false;
            for (int index = 0; index < left.size(); index++) {
                if (!equivalent(left.get(index), right.get(index))) return false;
            }
            return true;
        }
        return java.util.Objects.equals(expected, actual);
    }

    private static final class SamplePayload {
        private final Map<String, Object> input;
        private final Map<String, Object> expectedOutput;

        private SamplePayload(Map<String, Object> input, Map<String, Object> expectedOutput) {
            this.input = input;
            this.expectedOutput = expectedOutput;
        }
    }

    private Evaluator loadPmml(byte[] bytes) {
        try {
            Evaluator evaluator = new LoadingModelEvaluatorBuilder()
                    .load(new ByteArrayInputStream(bytes)).build();
            evaluator.verify();
            return evaluator;
        } catch (Exception e) {
            throw new IllegalArgumentException("PMML format or runtime compatibility validation failed: "
                    + e.getMessage(), e);
        }
    }

    private List<String> exactNodeNames(Object value, String label) {
        if (!(value instanceof Map<?, ?> nodes)) {
            throw new IllegalArgumentException("ONNX metadata is missing " + label);
        }
        List<String> result = new ArrayList<>();
        for (Object key : nodes.keySet()) {
            if (!(key instanceof String name)) {
                throw new IllegalArgumentException("ONNX node name is invalid");
            }
            result.add(name);
        }
        return result;
    }

    private void assertExactNames(String label, List<String> declared, List<String> actual) {
        List<String> safeDeclared = declared == null ? Collections.emptyList() : declared;
        Set<String> declaredSet = new LinkedHashSet<>(safeDeclared);
        Set<String> actualSet = new LinkedHashSet<>(actual);
        if (declaredSet.size() != safeDeclared.size()) {
            throw new IllegalArgumentException(label + " field names contain duplicates: " + safeDeclared);
        }
        if (!declaredSet.equals(actualSet)) {
            Set<String> missing = new LinkedHashSet<>(actualSet);
            missing.removeAll(declaredSet);
            Set<String> unexpected = new LinkedHashSet<>(declaredSet);
            unexpected.removeAll(actualSet);
            throw new IllegalArgumentException(label + " field names must match exactly; missing "
                    + missing + ", undeclared " + unexpected);
        }
    }

    private Map<String, Object> schema(List<String> names) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String name : names) {
            properties.put(name, Map.of());
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", names);
        schema.put("additionalProperties", false);
        return schema;
    }

    protected Map<String, Object> inspectOnnx(byte[] bytes) {
        if (onnxRuntimeSessionManager == null) {
            throw new IllegalStateException("ONNX Runtime is not configured");
        }
        return onnxRuntimeSessionManager.inspect(bytes);
    }

    protected Map<String, Object> executeOnnx(byte[] bytes, String configJson,
                                              Map<String, Object> sample) {
        if (onnxModelExecutionService == null) {
            throw new IllegalStateException("ONNX execution service is not configured");
        }
        return onnxModelExecutionService.execute(bytes, configJson, sample);
    }
}
