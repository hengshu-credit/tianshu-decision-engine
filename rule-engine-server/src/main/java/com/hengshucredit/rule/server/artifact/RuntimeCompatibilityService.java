package com.hengshucredit.rule.server.artifact;

import com.hengshucredit.rule.server.service.onnx.OnnxRuntimeSessionManager;
import com.hengshucredit.rule.server.model.ModelArtifactValidator;
import com.hengshucredit.rule.server.model.ModelValidationReport;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class RuntimeCompatibilityService {
    private static final int JAVA_MAJOR = 17;
    private static final String QL_EXPRESS_VERSION = "4.1.0";
    private static final String JPMML_VERSION = "1.7.7";
    private static final String ONNX_RUNTIME_VERSION = "1.26.0";

    @Resource
    private OnnxRuntimeSessionManager onnxRuntimeSessionManager;
    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private ModelArtifactValidator modelArtifactValidator;

    public CompatibilityReport validate(DecisionArtifactPackage artifactPackage) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> metadata = artifactPackage.getMetadata();
        int requiredJava = number(metadata.get("javaMajor"), JAVA_MAJOR);
        if (requiredJava > JAVA_MAJOR) {
            errors.add("制品要求 Java " + requiredJava + "，当前运行时为 Java " + JAVA_MAJOR);
        }
        requireVersion(metadata, "qlExpressVersion", QL_EXPRESS_VERSION, "QLExpress", errors);
        requireVersion(metadata, "jpmmlVersion", JPMML_VERSION, "JPMML", errors);
        requireVersion(metadata, "onnxRuntimeVersion", ONNX_RUNTIME_VERSION, "ONNX Runtime", errors);

        for (DecisionArtifactPackage.Component component : artifactPackage.getComponents().values()) {
            Map<String, Object> componentMetadata = component.getMetadata();
            String resourceType = text(componentMetadata.get("resourceType"));
            if ("MODEL".equals(resourceType)) {
                validateEmbeddedModel(component, errors);
            }
            if ("RUNTIME_REQUIREMENT".equals(componentMetadata.get("embeddingMode"))
                    && "FUNCTION".equals(resourceType)) {
                validateRuntimeFunction(component, errors);
            }
            Object constraints = componentMetadata.get("runtimeConstraintsJson");
            if (constraints instanceof String json && json.toUpperCase(java.util.Locale.ROOT).contains("CUDA")) {
                Map<String, Object> capabilities = onnxRuntimeSessionManager == null
                        ? Collections.emptyMap() : onnxRuntimeSessionManager.runtimeCapabilities();
                if (!Boolean.TRUE.equals(capabilities.get("cudaAvailable"))) {
                    errors.add("制品要求 ONNX CUDA Execution Provider，但目标环境不可用");
                }
            }
        }
        return new CompatibilityReport(errors, warnings);
    }

    private void validateEmbeddedModel(DecisionArtifactPackage.Component component,
                                       List<String> errors) {
        if (modelArtifactValidator == null) {
            errors.add("目标环境未配置模型制品校验器");
            return;
        }
        try {
            Map<String, Object> metadata = component.getMetadata();
            Map<String, Object> model = asMap(metadata.get("model"));
            String format = text(model.get("modelFormat"));
            List<String> inputs = fieldNames(metadata.get("inputFields"));
            List<String> outputs = fieldNames(metadata.get("outputFields"));
            String runtimeConfigJson = null;
            if ("ONNX".equalsIgnoreCase(format)) {
                Map<String, Object> config = parseMap(model.get("modelConfig"));
                Map<String, Object> nodeMetadata = asMap(config.get("nodeMetadata"));
                inputs = nodeNames(nodeMetadata, "inputs");
                outputs = nodeNames(nodeMetadata, "outputs");
                runtimeConfigJson = CanonicalJson.write(config);
            }
            ModelValidationReport report = modelArtifactValidator.validate(format,
                    component.getContent(), inputs, outputs, null, runtimeConfigJson);
            if (report == null || !report.isValid()) {
                errors.add("模型在目标运行时校验失败: " + component.getPath());
            }
        } catch (RuntimeException error) {
            errors.add("模型在目标运行时不兼容[" + component.getPath() + "]: "
                    + error.getMessage());
        }
    }

    private List<String> fieldNames(Object value) {
        if (!(value instanceof List<?> fields)) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (Object field : fields) {
            String name = text(asMap(field).get("fieldName"));
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("模型字段名不能为空");
            }
            names.add(name);
        }
        return names;
    }

    private List<String> nodeNames(Map<String, Object> metadata, String section) {
        Object nodesValue = metadata.get(section);
        if (!(nodesValue instanceof Map<?, ?> nodes)) {
            throw new IllegalArgumentException("ONNX 元数据缺少 " + section);
        }
        List<String> names = new ArrayList<>();
        for (Object key : nodes.keySet()) {
            if (!(key instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException("ONNX 节点名称无效");
            }
            names.add(name);
        }
        return names;
    }

    private Map<String, Object> parseMap(Object value) {
        if (value instanceof Map<?, ?>) return asMap(value);
        return value == null ? Collections.emptyMap() : CanonicalJson.readMap(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Collections.emptyMap();
    }

    private void validateRuntimeFunction(DecisionArtifactPackage.Component component,
                                         List<String> errors) {
        Map<String, Object> function = CanonicalJson.readMap(component.getContent());
        String implType = text(function.get("implType"));
        String methodName = text(function.get("implMethod"));
        try {
            if ("JAVA".equals(implType)) {
                Class<?> type = Class.forName(text(function.get("implClass")));
                if (!hasMethod(type, methodName)) {
                    errors.add("JAVA 函数方法不存在: " + type.getName() + "#" + methodName);
                }
            } else if ("BEAN".equals(implType)) {
                String beanName = text(function.get("implBeanName"));
                Object bean = applicationContext == null ? null : applicationContext.getBean(beanName);
                if (bean == null || !hasMethod(bean.getClass(), methodName)) {
                    errors.add("Spring Bean 函数不可用: " + beanName + "#" + methodName);
                }
            }
        } catch (RuntimeException | ClassNotFoundException | LinkageError e) {
            errors.add("函数运行时不兼容: " + e.getMessage());
        }
    }

    private boolean hasMethod(Class<?> type, String name) {
        if (type == null || name == null || name.isBlank()) return false;
        for (Method method : type.getMethods()) {
            if (name.equals(method.getName())) return true;
        }
        return false;
    }

    private void requireVersion(Map<String, Object> metadata, String key, String current,
                                String label, List<String> errors) {
        Object required = metadata.get(key);
        if (required != null && !current.equals(required.toString())) {
            errors.add(label + " 版本不兼容，制品要求 " + required + "，当前为 " + current);
        }
    }

    private int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    public static final class CompatibilityReport {
        private final List<String> errors;
        private final List<String> warnings;

        public CompatibilityReport(List<String> errors, List<String> warnings) {
            this.errors = List.copyOf(errors);
            this.warnings = List.copyOf(warnings);
        }

        public static CompatibilityReport compatible() {
            return new CompatibilityReport(Collections.emptyList(), Collections.emptyList());
        }

        public static CompatibilityReport incompatible(String error) {
            return new CompatibilityReport(Collections.singletonList(error), Collections.emptyList());
        }

        public boolean isCompatible() {
            return errors.isEmpty();
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }
}
