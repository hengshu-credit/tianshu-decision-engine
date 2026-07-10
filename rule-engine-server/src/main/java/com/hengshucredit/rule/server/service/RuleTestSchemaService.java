package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ResolutionPlan;
import com.hengshucredit.rule.model.dto.ResolvedField;
import com.hengshucredit.rule.model.dto.RuleTestSchema;
import com.hengshucredit.rule.model.dto.RuleTestSchemaRequest;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 根据统一依赖计划生成测试字段和嵌套样例参数。 */
@Service
public class RuleTestSchemaService {

    @Resource
    private FieldDependencyResolver fieldDependencyResolver;

    public RuleTestSchema build(RuleTestSchemaRequest request) {
        ResolutionPlan plan = fieldDependencyResolver.resolve(request);
        RuleTestSchema schema = new RuleTestSchema();
        schema.setInputs(new ArrayList<>(plan.getExternalInputs()));
        schema.setOutputs(new ArrayList<>(plan.getOutputs()));
        schema.setDiagnostics(new ArrayList<>(plan.getDiagnostics()));
        Map<String, Object> params = new LinkedHashMap<>();
        for (ResolvedField field : plan.getExternalInputs()) {
            String path = firstText(field.getScriptName(), field.getCode());
            if (path != null) {
                setPathValue(params, path, sampleValue(field));
            }
        }
        schema.setSampleParams(params);
        return schema;
    }

    private Object sampleValue(ResolvedField field) {
        if (field.getExampleValue() != null && !field.getExampleValue().isEmpty()) return field.getExampleValue();
        if (field.getDefaultValue() != null && !field.getDefaultValue().isEmpty()) return field.getDefaultValue();
        String type = field.getValueType() == null ? "STRING" : field.getValueType().toUpperCase(Locale.ROOT);
        if ("INTEGER".equals(type) || "INT".equals(type) || "LONG".equals(type)) return 0;
        if ("NUMBER".equals(type) || "DOUBLE".equals(type) || "FLOAT".equals(type)
                || "DECIMAL".equals(type) || "PROBABILITY".equals(type)) return 0d;
        if ("BOOLEAN".equals(type) || "BOOL".equals(type)) return false;
        if ("ARRAY".equals(type) || "LIST".equals(type) || "VECTOR".equals(type)) return new ArrayList<>();
        if ("OBJECT".equals(type) || "MAP".equals(type)) return new LinkedHashMap<>();
        return "";
    }

    @SuppressWarnings("unchecked")
    private void setPathValue(Map<String, Object> target, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;
            if (i == parts.length - 1) {
                current.put(part, value);
            } else {
                Object child = current.get(part);
                if (!(child instanceof Map)) {
                    child = new LinkedHashMap<String, Object>();
                    current.put(part, child);
                }
                current = (Map<String, Object>) child;
            }
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }
}
