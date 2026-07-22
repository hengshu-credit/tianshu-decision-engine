package com.hengshucredit.rule.core.pmml;

import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.EvaluatorUtil;
import org.jpmml.evaluator.InputField;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.jpmml.evaluator.OutputField;
import org.jpmml.evaluator.TargetField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PMML 模型执行器，基于 JPMML-Evaluator 1.7.x 实现。
 * 字段名严格按 PMML 原值匹配，并缓存已经校验的线程安全 Evaluator。
 */
public class PMMLModelExecutor {

    private static final Logger log = LoggerFactory.getLogger(PMMLModelExecutor.class);

    private final Map<String, ModelContext> modelCache = new LinkedHashMap<>(16, 0.75f, true);
    private final int maxCacheSize;

    public PMMLModelExecutor() {
        this(10);
    }

    public PMMLModelExecutor(int maxCacheSize) {
        if (maxCacheSize <= 0) {
            throw new IllegalArgumentException("PMML 模型缓存容量必须大于 0");
        }
        this.maxCacheSize = maxCacheSize;
    }

    /**
     * 执行 PMML 模型预测。
     *
     * @param pmmlContentBase64 Base64 编码的 PMML 文件内容
     * @param params 输入参数（字段名 -> 值）
     * @return 预测结果（字段名 -> 值）
     */
    public Map<String, Object> evaluate(String pmmlContentBase64, Map<String, Object> params) {
        byte[] contentBytes = decodeContent(pmmlContentBase64);
        ModelContext ctx = getOrLoadModelContext(contentDigest(contentBytes), contentBytes);
        return evaluateModel(ctx, params);
    }

    /**
     * 执行已缓存的模型。缓存身份同时包含稳定模型 key 和内容摘要，模型内容更新后不会复用旧实例。
     *
     * @param modelKey 模型缓存 key（通常为模型 ID）
     * @param modelContentBase64 Base64 编码的 PMML 内容
     * @param params 输入参数
     * @return 预测结果
     */
    public Map<String, Object> evaluateWithKey(String modelKey, String modelContentBase64,
                                               Map<String, Object> params) {
        byte[] contentBytes = decodeContent(modelContentBase64);
        String stableKey = modelKey == null ? "" : modelKey;
        ModelContext ctx = getOrLoadModelContext(
                stableKey + ":" + contentDigest(contentBytes), contentBytes);
        return evaluateModel(ctx, params);
    }

    /**
     * 清除模型缓存。
     */
    public void clearCache() {
        synchronized (modelCache) {
            modelCache.clear();
        }
    }

    private ModelContext getOrLoadModelContext(String cacheKey, byte[] contentBytes) {
        synchronized (modelCache) {
            ModelContext cached = modelCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            ModelContext ctx = loadModelContext(contentBytes);
            while (modelCache.size() >= maxCacheSize) {
                Iterator<String> iter = modelCache.keySet().iterator();
                if (!iter.hasNext()) {
                    break;
                }
                String oldestKey = iter.next();
                iter.remove();
                log.debug("缓存满，移除旧模型: {}", oldestKey);
            }
            modelCache.put(cacheKey, ctx);
            return ctx;
        }
    }

    private ModelContext loadModelContext(byte[] contentBytes) {
        try {
            Evaluator evaluator = new LoadingModelEvaluatorBuilder()
                    .load(new ByteArrayInputStream(contentBytes))
                    .build();
            evaluator.verify();
            log.info("PMML 模型加载成功，输入字段数: {}", evaluator.getInputFields().size());
            return new ModelContext(evaluator);
        } catch (Exception e) {
            throw new RuntimeException("PMML 模型加载失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> evaluateModel(ModelContext ctx, Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> inputParams = params == null ? Collections.emptyMap() : params;

        try {
            Set<String> expectedNames = new LinkedHashSet<>();
            for (InputField inputField : ctx.evaluator.getInputFields()) {
                expectedNames.add(inputField.getName());
            }
            Set<String> suppliedNames = new LinkedHashSet<>(inputParams.keySet());
            if (!expectedNames.equals(suppliedNames)) {
                Set<String> missing = new LinkedHashSet<>(expectedNames);
                missing.removeAll(suppliedNames);
                Set<String> unexpected = new LinkedHashSet<>(suppliedNames);
                unexpected.removeAll(expectedNames);
                throw new IllegalArgumentException("PMML input field names must match exactly; missing "
                        + missing + ", unexpected " + unexpected);
            }

            Map<String, Object> arguments = new LinkedHashMap<>();
            for (InputField inputField : ctx.evaluator.getInputFields()) {
                String name = inputField.getName();
                arguments.put(name, inputField.prepare(inputParams.get(name)));
            }
            log.debug("PMML 入参 arguments: {}", arguments);

            Map<String, ?> entityResult = ctx.evaluator.evaluate(arguments);
            Map<String, ?> decodedResult = EvaluatorUtil.decodeAll(entityResult);
            log.debug("PMML evaluate 成功，entityResult keys: {}", decodedResult.keySet());

            List<OutputField> outputFields = ctx.evaluator.getOutputFields();
            for (OutputField outputField : outputFields) {
                String outputName = outputField.getName();
                result.put(outputName, convertValue(decodedResult.get(outputName)));
            }

            if (result.isEmpty()) {
                List<TargetField> targetFields = ctx.evaluator.getTargetFields();
                for (TargetField targetField : targetFields) {
                    String targetName = targetField.getName();
                    if (decodedResult.containsKey(targetName)) {
                        result.put(targetName, convertValue(decodedResult.get(targetName)));
                    }
                }
            }

            log.debug("PMML 最终结果: {}", result);
            return result;
        } catch (Exception e) {
            log.error("PMML 模型执行失败: {}", e.getMessage(), e);
            String causeMsg = e.getMessage();
            if (causeMsg == null || causeMsg.isEmpty()) {
                causeMsg = e.toString();
            }
            throw new RuntimeException("PMML 模型执行失败: " + causeMsg, e);
        }
    }

    private static byte[] decodeContent(String contentBase64) {
        if (contentBase64 == null || contentBase64.isEmpty()) {
            throw new IllegalArgumentException("PMML 模型内容为空");
        }
        try {
            return Base64.getDecoder().decode(contentBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("PMML 模型内容不是有效的 Base64", e);
        }
    }

    private static String contentDigest(byte[] contentBytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(contentBytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
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
            Number number = (Number) value;
            if (number.doubleValue() == Math.floor(number.doubleValue())
                    && !Double.isInfinite(number.doubleValue())) {
                return number.longValue();
            }
            return number.doubleValue();
        }
        return value;
    }

    private static class ModelContext {
        private final Evaluator evaluator;

        private ModelContext(Evaluator evaluator) {
            this.evaluator = evaluator;
        }
    }
}
