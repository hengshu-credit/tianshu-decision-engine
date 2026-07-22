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

        assertExactNames("Sample input", new ArrayList<>(sample.keySet()), actualInputs);
        Map<String, Object> result = "PMML".equals(modelFormat)
                ? pmmlExecutor.evaluate(Base64.getEncoder().encodeToString(bytes), sample)
                : executeOnnx(bytes, runtimeConfigJson, sample);
        if (result == null) {
            throw new IllegalArgumentException("Model sample execution returned no result");
        }
        Set<String> missingOutputs = new LinkedHashSet<>(actualOutputs);
        missingOutputs.removeAll(result.keySet());
        if (!missingOutputs.isEmpty()) {
            throw new IllegalArgumentException("Model sample execution is missing exact output fields: "
                    + missingOutputs);
        }
        report.setSampleStatus("PASSED");
        return report;
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
