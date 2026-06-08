package com.bjjw.rule.core.pmml;

import org.jpmml.evaluator.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * PMML 模型执行器，基于 JPMML-Evaluator 1.5.x 实现。
 * 支持缓存已加载的模型，避免重复解析。
 */
public class PMMLModelExecutor {

    private static final Logger log = LoggerFactory.getLogger(PMMLModelExecutor.class);

    private final Map<String, ModelEvaluator<?>> modelCache = new LinkedHashMap<>();
    private final int maxCacheSize;

    public PMMLModelExecutor() {
        this(10);
    }

    public PMMLModelExecutor(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    /**
     * 执行 PMML 模型预测
     *
     * @param pmmlContentBase64 Base64 编码的 PMML 文件内容
     * @param params 输入参数（字段名 -> 值）
     * @return 预测结果（字段名 -> 值）
     */
    public Map<String, Object> evaluate(String pmmlContentBase64, Map<String, Object> params) {
        if (pmmlContentBase64 == null || pmmlContentBase64.isEmpty()) {
            throw new IllegalArgumentException("PMML 模型内容为空");
        }

        ModelEvaluator<?> evaluator = getEvaluator(pmmlContentBase64);
        return evaluateModel(evaluator, params);
    }

    /**
     * 执行已缓存的模型
     *
     * @param modelKey 模型缓存 key（通常为 modelCode 或模型 ID）
     * @param modelContentBase64 Base64 编码的 PMML 内容
     * @param params 输入参数
     * @return 预测结果
     */
    public Map<String, Object> evaluateWithKey(String modelKey, String modelContentBase64, Map<String, Object> params) {
        ModelEvaluator<?> evaluator = getEvaluatorWithKey(modelKey, modelContentBase64);
        return evaluateModel(evaluator, params);
    }

    /**
     * 清除模型缓存
     */
    public void clearCache() {
        modelCache.clear();
    }

    /**
     * 根据 Base64 内容获取模型评估器（带缓存）
     */
    private ModelEvaluator<?> getEvaluatorWithKey(String key, String contentBase64) {
        ModelEvaluator<?> cached = modelCache.get(key);
        if (cached != null) {
            return cached;
        }

        ModelEvaluator<?> evaluator = loadEvaluator(contentBase64);
        cacheModel(key, evaluator);
        return evaluator;
    }

    /**
     * 根据 Base64 内容加载评估器（不带缓存，按内容 hash 作为 key）
     */
    private ModelEvaluator<?> getEvaluator(String contentBase64) {
        String key = String.valueOf(contentBase64.hashCode());
        ModelEvaluator<?> cached = modelCache.get(key);
        if (cached != null) {
            return cached;
        }

        ModelEvaluator<?> evaluator = loadEvaluator(contentBase64);
        cacheModel(key, evaluator);
        return evaluator;
    }

    private ModelEvaluator<?> loadEvaluator(String contentBase64) {
        try {
            byte[] contentBytes = Base64.getDecoder().decode(contentBase64);
            ByteArrayInputStream is = new ByteArrayInputStream(contentBytes);
            PMML pmml = new PMMLReader().load(is);
            ModelEvaluatorFactory factory = ModelEvaluatorFactory.newInstance();
            ModelEvaluator<?> evaluator = factory.newModelEvaluator(pmml);
            log.info("PMML 模型加载成功: {}", evaluator.getSummary());
            return evaluator;
        } catch (Exception e) {
            throw new RuntimeException("PMML 模型加载失败: " + e.getMessage(), e);
        }
    }

    private void cacheModel(String key, ModelEvaluator<?> evaluator) {
        if (modelCache.size() >= maxCacheSize) {
            Iterator<String> iter = modelCache.keySet().iterator();
            if (iter.hasNext()) {
                String oldestKey = iter.next();
                modelCache.remove(oldestKey);
                log.debug("缓存满，移除旧模型: {}", oldestKey);
            }
        }
        modelCache.put(key, evaluator);
    }

    /**
     * 执行模型预测
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> evaluateModel(ModelEvaluator<?> evaluator, Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // 1. 准备输入字段值
            Map<String, FieldValue> arguments = new LinkedHashMap<>();
            List<? extends InputField> inputFields = evaluator.getInputFields();

            for (InputField inputField : inputFields) {
                String name = inputField.getName();
                Object rawValue = params.get(name);

                if (rawValue == null) {
                    // 尝试用别名或下划线变体查找
                    rawValue = params.get(underscoreToCamel(name));
                    if (rawValue == null) {
                        rawValue = params.get(camelToUnderscore(name));
                    }
                }

                if (rawValue == null) {
                    rawValue = getDefaultValue(inputField);
                }

                FieldValue fieldValue = inputField.prepare(rawValue);
                arguments.put(name, fieldValue);
            }

            // 2. 执行预测
            EvaluationContext context = new DefaultEvaluationContext();
            Map<FieldName, ?> entityResult = evaluator.evaluate(arguments, context);

            // 3. 提取输出字段
            List<? extends OutputField> outputFields = evaluator.getOutputFields();
            for (OutputField outputField : outputFields) {
                Object value = entityResult.get(outputField.getName());
                if (value == null) {
                    // 尝试从 TargetField 或原始结果获取
                    value = entityResult.get(FieldName.create(outputField.getName().getValue()));
                }
                result.put(outputField.getName().getValue(), convertValue(value));
            }

            // 4. 如果没有显式输出字段，取目标变量
            if (result.isEmpty()) {
                Object targetValue = entityResult.get(evaluator.getTargetField().getName());
                if (targetValue != null) {
                    result.put(evaluator.getTargetField().getName().getValue(), convertValue(targetValue));
                }
            }

            return result;

        } catch (Exception e) {
            throw new RuntimeException("PMML 模型执行失败: " + e.getMessage(), e);
        }
    }

    private Object getDefaultValue(InputField inputField) {
        Object defaultValue = inputField.getDefaultValue();
        if (defaultValue != null) {
            return defaultValue;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object convertValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.size() == 1) {
                return convertValue(list.get(0));
            }
            return list.stream().map(this::convertValue).toArray();
        }
        if (value instanceof Number) {
            Number n = (Number) value;
            if (n.doubleValue() == Math.floor(n.doubleValue()) && !Double.isInfinite(n.doubleValue())) {
                return n.longValue();
            }
            return n.doubleValue();
        }
        return value;
    }

    private String underscoreToCamel(String name) {
        if (name == null || !name.contains("_")) return name;
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String camelToUnderscore(String name) {
        if (name == null) return null;
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}