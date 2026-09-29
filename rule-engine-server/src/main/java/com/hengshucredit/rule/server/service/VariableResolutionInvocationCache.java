package com.hengshucredit.rule.server.service;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import com.hengshucredit.rule.server.derived.HistoricalFieldDefinition;
import com.hengshucredit.rule.server.derived.HistoryFieldValues;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.server.artifact.CanonicalJson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class VariableResolutionInvocationCache {

    private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> apiResponses =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<SourceResolutionResult>> variableResults =
            new ConcurrentHashMap<>();
    private final Map<String, HistoricalFieldDefinition> historyDefinitions = new ConcurrentHashMap<>();
    private final Map<String, HistoricalFieldDefinition> historyDefaults = new ConcurrentHashMap<>();
    private final Map<String, Object> fieldResults = new ConcurrentHashMap<>();
    private final Map<String, Object> originalObjectInputs = new ConcurrentHashMap<>();
    /** source key -> the response cache entries it populated. */
    private final Map<String, Set<String>> sourceResponseKeys = new ConcurrentHashMap<>();
    /** response cache key -> source keys that share it. */
    private final Map<String, Set<String>> responseSourceKeys = new ConcurrentHashMap<>();
    /** source fingerprints from the current runtime snapshot. */
    private final Map<String, String> sourceFingerprints = new ConcurrentHashMap<>();
    /** Durable, non-repeatable source steps completed by this logical execution. */
    private final Map<String, SourceStep> completedSteps = new ConcurrentHashMap<>();
    /** 离线回溯中没有历史快照而被置为 null 的来源。 */
    private final Map<String, Map<String, Object>> replayMissingSources = new ConcurrentHashMap<>();
    private volatile SourceStepListener sourceStepListener;
    private static final Object NULL_VALUE = new Object();

    /**
     * Durable sink supplied by the logical execution coordinator. The cache remains usable
     * without a listener for previews and ordinary executions.
     */
    @FunctionalInterface
    public interface SourceStepListener {
        void onCompleted(SourceStep step);
    }

    /** A stable source checkpoint; it never contains pure QL assignment state. */
    public static final class SourceStep {
        private final String sourceKey;
        private final String sourceType;
        private final String configDigest;
        private final String inputDigest;
        private final String dependencyDigest;
        private final String status;
        private final Object value;
        private final Map<String, Object> response;
        private final Map<String, Map<String, Object>> sourceStates;
        private final Map<String, Object> metadata;

        public SourceStep(String sourceKey, String sourceType, String configDigest,
                          String inputDigest, String dependencyDigest, String status,
                          Object value, Map<String, Object> response,
                          Map<String, Map<String, Object>> sourceStates,
                          Map<String, Object> metadata) {
            this.sourceKey = sourceKey;
            this.sourceType = sourceType;
            this.configDigest = configDigest;
            this.inputDigest = inputDigest;
            this.dependencyDigest = dependencyDigest;
            this.status = status;
            this.value = copyValueStatic(value);
            this.response = copyMapStatic(response);
            this.sourceStates = copyStatesStatic(sourceStates);
            this.metadata = copyMapStatic(metadata);
        }

        public String getSourceKey() { return sourceKey; }
        public String getSourceType() { return sourceType; }
        public String getConfigDigest() { return configDigest; }
        public String getInputDigest() { return inputDigest; }
        public String getDependencyDigest() { return dependencyDigest; }
        public String getStatus() { return status; }
        public Object getValue() { return copyValueStatic(value); }
        public Map<String, Object> getResponse() { return copyMapStatic(response); }
        public Map<String, Map<String, Object>> getSourceStates() { return sourceStates; }
        public Map<String, Object> getMetadata() { return metadata; }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sourceKey", sourceKey);
            result.put("sourceType", sourceType);
            result.put("configDigest", configDigest);
            result.put("inputDigest", inputDigest);
            result.put("dependencyDigest", dependencyDigest);
            result.put("status", status);
            result.put("value", "MODEL".equalsIgnoreCase(sourceType) ? null : copyValueStatic(value));
            result.put("response", copyMapStatic(response));
            result.put("sourceStates", copyStatesStatic(sourceStates));
            result.put("metadata", copyMapStatic(metadata));
            return result;
        }

        @SuppressWarnings("unchecked")
        public static SourceStep fromMap(String sourceKey, Object raw) {
            if (!(raw instanceof Map<?, ?> map)) return null;
            Map<String, Object> values = new LinkedHashMap<>();
            map.forEach((key, value) -> values.put(String.valueOf(key), value));
            Map<String, Map<String, Object>> states = new LinkedHashMap<>();
            Object rawStates = values.get("sourceStates");
            if (rawStates instanceof Map<?, ?> stateMap) {
                stateMap.forEach((key, value) -> {
                    if (value instanceof Map<?, ?> item) {
                        Map<String, Object> state = new LinkedHashMap<>();
                        item.forEach((stateKey, stateValue) -> state.put(String.valueOf(stateKey), stateValue));
                        states.put(String.valueOf(key), state);
                    }
                });
            }
            Map<String, Object> response = values.get("response") instanceof Map<?, ?> responseMap
                    ? copyMapStatic(responseMap) : null;
            Map<String, Object> metadata = values.get("metadata") instanceof Map<?, ?> metadataMap
                    ? copyMapStatic(metadataMap) : null;
            return new SourceStep(sourceKey,
                    stringValue(values.get("sourceType")),
                    stringValue(values.get("configDigest")),
                    stringValue(values.get("inputDigest")),
                    stringValue(values.get("dependencyDigest")),
                    stringValue(values.get("status")), values.get("value"), response,
                    states, metadata);
        }

        private static String stringValue(Object value) {
            return value == null ? null : String.valueOf(value);
        }
    }

    public void setSourceStepListener(SourceStepListener listener) {
        this.sourceStepListener = listener;
    }

    public boolean hasSourceStepListener() {
        return sourceStepListener != null;
    }

    public SourceStep completedStep(String sourceKey) {
        return sourceKey == null ? null : completedSteps.get(sourceKey);
    }

    public void recordReplayMissing(String sourceType, String sourceKey, String scriptName) {
        if (sourceKey == null) return;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sourceType", sourceType);
        item.put("sourceKey", sourceKey);
        item.put("scriptName", scriptName);
        replayMissingSources.putIfAbsent(sourceKey, item);
    }

    public List<Map<String, Object>> replayMissingSources() {
        return new ArrayList<>(replayMissingSources.values());
    }

    public boolean hasReusableStep(String sourceKey, String configDigest,
                                   String inputDigest, String dependencyDigest) {
        SourceStep step = completedStep(sourceKey);
        return step != null
                && java.util.Objects.equals(step.getConfigDigest(), configDigest)
                && java.util.Objects.equals(step.getInputDigest(), inputDigest)
                && java.util.Objects.equals(step.getDependencyDigest(), dependencyDigest)
                && !"FAILED".equalsIgnoreCase(step.getStatus());
    }

    /** Persist first, then expose the step to the in-memory cache. */
    public void completeStep(SourceStep step) {
        if (step == null || step.getSourceKey() == null) return;
        SourceStepListener listener = sourceStepListener;
        if (listener != null) listener.onCompleted(step);
        completedSteps.put(step.getSourceKey(), step);
    }

    public void invalidateStep(String sourceKey) {
        if (sourceKey == null) return;
        completedSteps.remove(sourceKey);
        variableResults.remove(sourceKey);
        fieldResults.remove(sourceKey);
        if (sourceKey.startsWith("MODEL:")) apiResponses.remove(sourceKey);
        Set<String> responseKeys = sourceResponseKeys.remove(sourceKey);
        if (responseKeys != null) {
            for (String responseKey : responseKeys) {
                Set<String> owners = responseSourceKeys.get(responseKey);
                if (owners != null) {
                    owners.remove(sourceKey);
                    if (!owners.isEmpty()) continue;
                    responseSourceKeys.remove(responseKey);
                }
                apiResponses.remove(responseKey);
            }
        }
    }

    public Map<String, Object> snapshotCompletedSteps() {
        Map<String, Object> result = new LinkedHashMap<>();
        completedSteps.forEach((key, step) -> result.put(key, step.toMap()));
        return result;
    }

    /**
     * 仅导出不可重复来源写入的上下文根字段。纯 QL 赋值不进入检查点，
     * 这样规则版本变化后不会把旧的计算变量带入新脚本。
     */
    public Map<String, Object> snapshotSourceValues() {
        Map<String, Object> result = new LinkedHashMap<>();
        completedSteps.values().forEach(step -> {
            if (step == null) return;
            Object path = step.getMetadata().get("scriptName");
            if (path == null) path = step.getMetadata().get("modelCode");
            if (path == null || String.valueOf(path).isBlank()) return;
            putPath(result, String.valueOf(path), step.getValue());
        });
        return result;
    }

    public void restoreCompletedSteps(Map<String, ?> values) {
        if (values == null) return;
        values.forEach((key, value) -> {
            SourceStep step = SourceStep.fromMap(String.valueOf(key), value);
            if (step == null) return;
            completedSteps.put(String.valueOf(key), step);
            if ("VARIABLE".equalsIgnoreCase(step.getSourceType())) {
                Object scriptName = step.getMetadata().get("scriptName");
                restoreVariableResult(step.getSourceKey(), scriptName == null ? null : String.valueOf(scriptName),
                        step.getValue(), step.getSourceStates());
            } else if ("MODEL".equalsIgnoreCase(step.getSourceType())) {
                Map<String, Object> response = step.getResponse();
                if (response == null && step.getValue() instanceof Map<?, ?> map) {
                    response = copyMapStatic(map);
                }
                if (response != null) restoreResponse(step.getSourceKey(), response);
                associateResponse(step.getSourceKey(), step.getSourceKey());
            }
        });
    }

    public String sourceValueDigest(String sourceKey) {
        SourceStep step = completedStep(sourceKey);
        if (step == null) return null;
        Object value = step.getValue();
        if (value == null && step.getResponse() != null) value = step.getResponse().get("outputs");
        return sha256(CanonicalJson.write(value));
    }

    public String sourceValueDigestForPath(String path) {
        if (path == null) return null;
        for (SourceStep step : completedSteps.values()) {
            Object scriptName = step.getMetadata().get("scriptName");
            Object modelCode = step.getMetadata().get("modelCode");
            String candidate = scriptName == null ? modelCode == null ? null
                    : String.valueOf(modelCode) : String.valueOf(scriptName);
            if (candidate != null && (path.equals(candidate) || path.startsWith(candidate + ".")
                    || path.startsWith(candidate + "["))) {
                return sourceValueDigest(step.getSourceKey());
            }
        }
        return null;
    }

    public String sourceFingerprint(String sourceKey) {
        return sourceKey == null ? null : sourceFingerprints.get(sourceKey);
    }

    public void registerObjectInputs(Map<String, String> paths, Map<String, Object> original) {
        paths.forEach((key, path) -> {
            if (key.startsWith("DATA_OBJECT:") && HistoryFieldValues.present(original, path)) {
                Object value = HistoryFieldValues.read(original, path);
                originalObjectInputs.put(key, value == null ? NULL_VALUE : copyValue(value));
            }
        });
    }

    public void registerHistoryFields(Map<String, HistoricalFieldDefinition> definitions) {
        if (definitions != null) definitions.forEach(historyDefinitions::putIfAbsent);
    }

    public void registerHistoryDefaults(Map<String, HistoricalFieldDefinition> definitions) {
        if (definitions != null) definitions.forEach(historyDefaults::putIfAbsent);
    }

    public void rememberFieldResult(String key, Object value) {
        fieldResults.putIfAbsent(key, value == null ? NULL_VALUE : copyValue(value));
    }

    /** 返回当前根执行已成功取得的来源结果，供跨请求检查点持久化。 */
    public Map<String, Object> snapshotFieldResults() {
        Map<String, Object> result = new LinkedHashMap<>();
        fieldResults.forEach((key, value) -> result.put(key, value == NULL_VALUE ? null : copyValue(value)));
        return result;
    }

    /** 返回已完成的 API/模型响应；未完成或失败的 Future 不进入检查点。 */
    public Map<String, Object> snapshotResponses() {
        Map<String, Object> result = new LinkedHashMap<>();
        apiResponses.forEach((key, future) -> {
            if (future != null && future.isDone() && !future.isCompletedExceptionally()) {
                result.put(key, copyMap(future.getNow(null)));
            }
        });
        return result;
    }

    public Map<String, Object> snapshotVariableResults() {
        Map<String, Object> result = new LinkedHashMap<>();
        variableResults.forEach((key, future) -> {
            if (future != null && future.isDone() && !future.isCompletedExceptionally()) {
                SourceResolutionResult value = future.getNow(null);
                if (value != null && value.isResolved()) result.put(key, value.getValue());
            }
        });
        return result;
    }

    public Map<String, String> snapshotSourceFingerprints() {
        return new LinkedHashMap<>(sourceFingerprints);
    }

    public void restoreSourceFingerprints(Map<String, String> values) {
        if (values != null) sourceFingerprints.putAll(values);
    }

    public void replaceSourceFingerprints(Map<String, String> values) {
        sourceFingerprints.clear();
        if (values != null) sourceFingerprints.putAll(values);
    }

    /**
     * Drops only source results whose ID-bound configuration changed. Shared API responses are
     * retained while another unchanged source still owns the same request cache entry.
     */
    public Set<String> invalidateMismatchedSources(Map<String, String> current) {
        Set<String> changed = new HashSet<>();
        if (current == null) current = Collections.emptyMap();
        for (Map.Entry<String, String> entry : new LinkedHashMap<>(sourceFingerprints).entrySet()) {
            if (!java.util.Objects.equals(entry.getValue(), current.get(entry.getKey()))) {
                changed.add(entry.getKey());
            }
        }
        return changed;
    }

    public void recordSourceFingerprint(String sourceKey, String fingerprint) {
        if (sourceKey != null && fingerprint != null) sourceFingerprints.put(sourceKey, fingerprint);
    }

    public void associateResponse(String sourceKey, String responseKey) {
        if (sourceKey == null || responseKey == null) return;
        sourceResponseKeys.computeIfAbsent(sourceKey, ignored -> ConcurrentHashMap.newKeySet()).add(responseKey);
        responseSourceKeys.computeIfAbsent(responseKey, ignored -> ConcurrentHashMap.newKeySet()).add(sourceKey);
    }

    public Map<String, List<String>> snapshotSourceResponseKeys() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        sourceResponseKeys.forEach((key, values) -> result.put(key, new ArrayList<>(values)));
        return result;
    }

    public void restoreSourceResponseKeys(Map<String, ?> values) {
        if (values == null) return;
        values.forEach((source, rawKeys) -> {
            if (!(rawKeys instanceof Iterable<?> iterable)) return;
            for (Object rawKey : iterable) {
                if (rawKey != null) associateResponse(source, String.valueOf(rawKey));
            }
        });
    }

    public void restoreResponse(String key, Map<String, Object> value) {
        if (key == null || value == null) return;
        apiResponses.putIfAbsent(key, CompletableFuture.completedFuture(copyMap(value)));
    }

    public void restoreVariableResult(String key, String scriptName, Object value) {
        restoreVariableResult(key, scriptName, value, Collections.emptyMap());
    }

    public void restoreVariableResult(String key, String scriptName, Object value,
                                      Map<String, Map<String, Object>> sourceStates) {
        if (key == null) return;
        variableResults.putIfAbsent(key, CompletableFuture.completedFuture(
                new SourceResolutionResult(0, scriptName, true, copyValue(value),
                sourceStates, Collections.emptyList())));
    }

    public void restoreFieldResult(String key, Object value) {
        if (key != null) fieldResults.putIfAbsent(key, value == null ? NULL_VALUE : copyValue(value));
    }

    public Map<String, Object> historySnapshot(List<RuleDefinitionInputField> inputs,
            Map<String, Object> original, Map<String, Object> values) {
        Map<String, Object> fields = new LinkedHashMap<>();
        originalObjectInputs.forEach((key, value) -> fields.put(key, value == NULL_VALUE ? null : copyValue(value)));
        Map<String, HistoricalFieldDefinition> definitions = new LinkedHashMap<>(historyDefaults);
        definitions.putAll(historyDefinitions);
        java.util.Set<String> inputKeys = new java.util.HashSet<>();
        for (RuleDefinitionInputField input : inputs == null ? List.<RuleDefinitionInputField>of() : inputs) {
            if (input.getVarId() == null || input.getRefType() == null) continue;
            String key = input.getRefType() + ":" + input.getVarId();
            HistoricalFieldDefinition definition = definitions.get(key);
            if (definition != null && !"INPUT".equals(definition.source())) continue;
            inputKeys.add(key);
            String path = input.getScriptName() == null ? input.getFieldName() : input.getScriptName();
            fields.put(key, HistoryFieldValues.read(original, path));
        }
        for (HistoricalFieldDefinition field : definitions.values()) {
            Object originalValue = HistoryFieldValues.read(original, field.path());
            if ("INPUT".equals(field.source()) && (originalValue != null || inputKeys.contains(field.inputRootKey()))) {
                fields.put(field.key(), originalValue);
            }
            if (field.recordResult() && !"API".equals(field.source()) && !fields.containsKey(field.key())) {
                Object result = fieldResults.get(field.key());
                HistoricalFieldDefinition root = definitions.get(field.inputRootKey());
                if (result == null && root != null && root.path() != null && field.path() != null
                        && fieldResults.containsKey(root.key())
                        && (field.path().equals(root.path()) || field.path().startsWith(root.path() + ".")
                            || field.path().startsWith(root.path() + "["))) {
                    Object source = fieldResults.get(root.key());
                    String suffix = field.path().substring(root.path().length());
                    result = source == NULL_VALUE ? null : suffix.isEmpty() ? copyValue(source)
                            : HistoryFieldValues.read(Map.of("value", source), "value" + suffix);
                    if (result == null) result = NULL_VALUE;
                }
                if (result != null) fields.put(field.key(), result == NULL_VALUE ? null : copyValue(result));
                else if ("INPUT".equals(field.source()) || "COMPUTED".equals(field.source()) || "CONSTANT".equals(field.source())) fields.put(field.key(), HistoryFieldValues.read(values, field.path()));
            }
        }
        List<Long> apiIds = apiResponses.keySet().stream().filter(key -> key.startsWith("API:"))
                .map(VariableResolutionInvocationCache::apiIdFromCacheKey)
                .filter(java.util.Objects::nonNull).distinct().sorted().toList();
        return Map.of("version", 1, "fields", fields, "apiIds", apiIds);
    }

    private static Long apiIdFromCacheKey(String key) {
        String value = key.substring(4);
        int separator = value.indexOf(':');
        if (separator >= 0) value = value.substring(0, separator);
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public boolean hasVariableResult(String key) {
        CompletableFuture<SourceResolutionResult> value = variableResults.get(key);
        return value != null && value.isDone();
    }

    public SourceResolutionResult resolveVariable(String key, Supplier<SourceResolutionResult> supplier) {
        CompletableFuture<SourceResolutionResult> created = new CompletableFuture<>();
        CompletableFuture<SourceResolutionResult> existing = variableResults.putIfAbsent(key, created);
        if (existing == null) {
            try { created.complete(supplier.get()); }
            catch (Throwable error) { created.completeExceptionally(error); }
        }
        return (existing == null ? created : existing).join();
    }

    public SourceResolutionResult variableResult(String key) {
        return variableResults.get(key).join();
    }

    public boolean hasResponse(String key) {
        CompletableFuture<Map<String, Object>> value = apiResponses.get(key);
        return value != null && value.isDone();
    }

    public Map<String, Object> resolve(String key, Supplier<Map<String, Object>> supplier) {
        if (key == null || supplier == null) {
            throw new IllegalArgumentException("API response cache key and supplier must not be null");
        }
        CompletableFuture<Map<String, Object>> created = new CompletableFuture<>();
        CompletableFuture<Map<String, Object>> existing = apiResponses.putIfAbsent(key, created);
        CompletableFuture<Map<String, Object>> shared = existing == null ? created : existing;
        if (existing == null) {
            try {
                created.complete(copyMap(supplier.get()));
            } catch (Throwable e) {
                created.completeExceptionally(e);
            }
        }
        try {
            return copyMap(shared.join());
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return copy;
    }

    private Object copyValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                copy.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<?>) value) {
                copy.add(copyValue(item));
            }
            return copy;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object[] copy = new Object[length];
            for (int i = 0; i < length; i++) {
                copy[i] = copyValue(Array.get(value, i));
            }
            return copy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyMapStatic(Map<?, ?> source) {
        if (source == null) return null;
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), copyValueStatic(value)));
        return copy;
    }

    private static Map<String, Map<String, Object>> copyStatesStatic(
            Map<String, Map<String, Object>> source) {
        if (source == null || source.isEmpty()) return Collections.emptyMap();
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, copyMapStatic(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object copyValueStatic(Object value) {
        if (value instanceof Map<?, ?> map) return copyMapStatic(map);
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(item -> copy.add(copyValueStatic(item)));
            return copy;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> copy = new ArrayList<>(length);
            for (int i = 0; i < length; i++) copy.add(copyValueStatic(Array.get(value, i)));
            return copy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static void putPath(Map<String, Object> values, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = values;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;
            if (i == parts.length - 1) {
                current.put(part, copyValueStatic(value));
                return;
            }
            Object child = current.get(part);
            if (!(child instanceof Map<?, ?>)) {
                child = new LinkedHashMap<String, Object>();
                current.put(part, child);
            }
            current = (Map<String, Object>) child;
        }
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("来源结果摘要计算失败", e);
        }
    }
}
