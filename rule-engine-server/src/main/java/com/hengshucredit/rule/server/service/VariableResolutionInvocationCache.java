package com.hengshucredit.rule.server.service;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import com.hengshucredit.rule.server.derived.HistoricalFieldDefinition;
import com.hengshucredit.rule.server.derived.HistoryFieldValues;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;

public class VariableResolutionInvocationCache {

    private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> apiResponses =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<SourceResolutionResult>> variableResults =
            new ConcurrentHashMap<>();
    private final Map<String, HistoricalFieldDefinition> historyDefinitions = new ConcurrentHashMap<>();
    private final Map<String, HistoricalFieldDefinition> historyDefaults = new ConcurrentHashMap<>();
    private final Map<String, Object> fieldResults = new ConcurrentHashMap<>();
    private final Map<String, Object> originalObjectInputs = new ConcurrentHashMap<>();
    private static final Object NULL_VALUE = new Object();

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
                .map(key -> Long.valueOf(key.substring(4))).sorted().toList();
        return Map.of("version", 1, "fields", fields, "apiIds", apiIds);
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
}
