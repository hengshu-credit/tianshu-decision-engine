package com.bjjw.rule.core.pmml;

import org.dmg.pmml.FieldName;
import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segment;
import org.dmg.pmml.mining.Segmentation;
import org.jpmml.evaluator.*;
import org.jpmml.model.PMMLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PMML 模型执行器，基于 JPMML-Evaluator 1.5.x 实现。
 * 使用 ModelEvaluatorBuilder 正确初始化(configuration + valueFactoryFactory)。
 * 支持缓存已加载的模型，避免重复解析。
 */
public class PMMLModelExecutor {

    private static final Logger log = LoggerFactory.getLogger(PMMLModelExecutor.class);

    private final Map<String, ModelContext> modelCache = new LinkedHashMap<>();
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

        ModelContext ctx = getModelContext(pmmlContentBase64);
        return evaluateModel(ctx, params);
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
        ModelContext ctx = getModelContextWithKey(modelKey, modelContentBase64);
        return evaluateModel(ctx, params);
    }

    /**
     * 清除模型缓存
     */
    public void clearCache() {
        modelCache.clear();
    }

    /**
     * 根据 Base64 内容获取模型上下文（带缓存）
     */
    private ModelContext getModelContext(String contentBase64) {
        String key = String.valueOf(contentBase64.hashCode());
        ModelContext cached = modelCache.get(key);
        if (cached != null) {
            return cached;
        }

        ModelContext ctx = loadModelContext(contentBase64);
        cacheModel(key, ctx);
        return ctx;
    }

    /**
     * 根据 Base64 内容获取模型上下文（带缓存，按指定 key）
     */
    private ModelContext getModelContextWithKey(String key, String contentBase64) {
        ModelContext cached = modelCache.get(key);
        if (cached != null) {
            return cached;
        }

        ModelContext ctx = loadModelContext(contentBase64);
        cacheModel(key, ctx);
        return ctx;
    }

    private ModelContext loadModelContext(String contentBase64) {
        try {
            byte[] contentBytes = Base64.getDecoder().decode(contentBase64);
            ByteArrayInputStream is = new ByteArrayInputStream(contentBytes);
            PMML pmml = PMMLUtil.unmarshal(is);
            List<Model> models = pmml.getModels();
            if (models == null || models.isEmpty()) {
                throw new RuntimeException("PMML 模型文件中未找到任何模型");
            }
            // 使用 ModelEvaluatorBuilder 正确初始化 configuration 和 valueFactoryFactory
            ModelEvaluatorBuilder builder = new ModelEvaluatorBuilder(pmml);
            Evaluator evaluator = builder.build();
            evaluator.verify();
            log.info("PMML 模型加载成功: {}", evaluator.getSummary());
            return new ModelContext(evaluator, models.get(0));
        } catch (Exception e) {
            throw new RuntimeException("PMML 模型加载失败: " + e.getMessage(), e);
        }
    }

    private void cacheModel(String key, ModelContext ctx) {
        if (modelCache.size() >= maxCacheSize) {
            Iterator<String> iter = modelCache.keySet().iterator();
            if (iter.hasNext()) {
                String oldestKey = iter.next();
                modelCache.remove(oldestKey);
                log.debug("缓存满，移除旧模型: {}", oldestKey);
            }
        }
        modelCache.put(key, ctx);
    }

    /**
     * 执行模型预测
     */
    private Map<String, Object> evaluateModel(ModelContext ctx, Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // 1. 准备输入字段值
            Map<FieldName, Object> arguments = new LinkedHashMap<>();
            List<? extends InputField> inputFields = tryGetInputFields(ctx.evaluator);
            if (inputFields != null) {
                log.debug("使用 evaluator.getInputFields() 获取到 {} 个入参", inputFields.size());
                for (InputField inputField : inputFields) {
                    FieldName name = inputField.getName();
                    arguments.put(name, inputField.prepare(resolveFieldValue(name.getValue(), params)));
                }
            } else {
                log.debug("使用 PMML MiningSchema 获取入参");
                for (org.dmg.pmml.MiningField mf : getInputFieldNames(ctx.pmmlModel)) {
                    FieldName name = FieldName.create(mf.getName().getValue());
                    arguments.put(name, resolveFieldValue(mf.getName().getValue(), params));
                }
            }
            log.info("入参 arguments: {}", arguments);

            // 2. 执行预测
            Map<FieldName, ?> entityResult = ctx.evaluator.evaluate(arguments);
            log.info("evaluate 成功，entityResult keys: {}", entityResult.keySet());

            // 3. 提取输出字段
            List<? extends org.jpmml.evaluator.OutputField> outputFields = tryGetOutputFields(ctx.evaluator);
            if (outputFields != null && !outputFields.isEmpty()) {
                log.debug("使用 evaluator.getOutputFields() 获取到 {} 个出参", outputFields.size());
                for (org.jpmml.evaluator.OutputField outputField : outputFields) {
                    FieldName outputName = outputField.getName();
                    Object value = entityResult.get(outputName);
                    result.put(outputName.getValue(), convertValue(value));
                }
            } else {
                List<String> outputFieldNames = getOutputFieldNames(ctx.pmmlModel);
                log.debug("使用 PMML Output 获取出参: {}", outputFieldNames);
                for (String fieldName : outputFieldNames) {
                    FieldName fn = FieldName.create(fieldName);
                    Object value = entityResult.get(fn);
                    result.put(fieldName, convertValue(value));
                }
            }

            // 4. 如果没有显式输出字段，取目标变量
            if (result.isEmpty()) {
                try {
                    List<? extends TargetField> targetFields = ctx.evaluator.getTargetFields();
                    if (!targetFields.isEmpty()) {
                        FieldName targetName = targetFields.get(0).getName();
                        Object targetValue = entityResult.get(targetName);
                        if (targetValue != null) {
                            result.put(targetName.getValue(), convertValue(targetValue));
                        }
                    }
                } catch (Exception ignored) {}
            }

            log.info("最终结果: {}", result);
            return result;

        } catch (Exception e) {
            log.error("PMML 模型执行失败: {}", e.getMessage(), e);
            String causeMsg = e.getMessage();
            if (causeMsg == null || causeMsg.isEmpty()) causeMsg = e.toString();
            throw new RuntimeException("PMML 模型执行失败: " + causeMsg, e);
        }
    }

    /**
     * 尝试获取输入字段列表，可能因链式模型失败返回 null
     */
    private List<? extends InputField> tryGetInputFields(Evaluator evaluator) {
        try {
            return evaluator.getInputFields();
        } catch (Exception e) {
            log.debug("getInputFields 失败，fallback 到 PMML MiningSchema: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 尝试获取输出字段列表，可能因链式模型失败返回 null
     */
    private List<? extends org.jpmml.evaluator.OutputField> tryGetOutputFields(Evaluator evaluator) {
        try {
            return evaluator.getOutputFields();
        } catch (Exception e) {
            log.debug("getOutputFields 失败，fallback 到 PMML Output: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 PMML Model 的 MiningSchema 读取入参字段名（排除 target）
     */
    private List<org.dmg.pmml.MiningField> getInputFieldNames(Model pmmlModel) {
        List<org.dmg.pmml.MiningField> fields = new ArrayList<>();
        if (pmmlModel == null) return fields;
        org.dmg.pmml.MiningSchema miningSchema = pmmlModel.getMiningSchema();
        if (miningSchema == null) return fields;
        for (org.dmg.pmml.MiningField mf : miningSchema.getMiningFields()) {
            if (mf.getUsageType() == org.dmg.pmml.MiningField.UsageType.TARGET) continue;
            if (mf.getUsageType() == org.dmg.pmml.MiningField.UsageType.GROUP) continue;
            fields.add(mf);
        }
        return fields;
    }

    /**
     * 从 PMML Output 读取出参字段名（仅 isFinalResult=true）
     * 链式模型顶层无 Output 时，遍历 Segmentation 找最后一个有 Output 的 segment
     */
    private List<String> getOutputFieldNames(Model pmmlModel) {
        List<String> names = new ArrayList<>();
        if (pmmlModel == null) return names;
        org.dmg.pmml.Output pmmlOutput = pmmlModel.getOutput();

        if (pmmlOutput == null && pmmlModel instanceof MiningModel) {
            MiningModel miningModel = (MiningModel) pmmlModel;
            Segmentation segmentation = miningModel.getSegmentation();
            if (segmentation != null && segmentation.hasSegments()) {
                List<Segment> segments = segmentation.getSegments();
                for (int i = segments.size() - 1; i >= 0; i--) {
                    Segment seg = segments.get(i);
                    if (seg.getModel() != null && seg.getModel().getOutput() != null) {
                        pmmlOutput = seg.getModel().getOutput();
                        break;
                    }
                }
            }
        }

        if (pmmlOutput == null) return names;
        for (org.dmg.pmml.OutputField of : pmmlOutput.getOutputFields()) {
            if (!of.isFinalResult()) continue;
            names.add(of.getName().getValue());
        }
        return names;
    }

    /**
     * 从参数 map 中解析字段值，支持下划线/驼峰变体
     */
    private Object resolveFieldValue(String fieldName, Map<String, Object> params) {
        Object rawValue = params.get(fieldName);
        if (rawValue == null) rawValue = params.get(underscoreToCamel(fieldName));
        if (rawValue == null) rawValue = params.get(camelToUnderscore(fieldName));
        return rawValue;
    }

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

    /**
     * 模型上下文：同时持有 JPMML Evaluator 和 PMML Model 对象
     */
    private static class ModelContext {
        final Evaluator evaluator;
        final Model pmmlModel;

        ModelContext(Evaluator evaluator, Model pmmlModel) {
            this.evaluator = evaluator;
            this.pmmlModel = pmmlModel;
        }
    }
}