package com.hengshucredit.rule.server.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 内部已绑定模型输入，避免再次执行输入表达式或回读实时字段元信息。 */
final class BoundModelInputs extends LinkedHashMap<String, Object> {
    private final Map<String, Object> references;
    private final Map<String, String> referencePaths;
    private final boolean frozen;

    BoundModelInputs(Map<String, Object> values, Map<String, Object> references, Map<String, String> referencePaths, boolean frozen) {
        super(values);
        this.references = Collections.unmodifiableMap(new LinkedHashMap<>(references));
        this.referencePaths = Collections.unmodifiableMap(new LinkedHashMap<>(referencePaths));
        this.frozen = frozen;
    }

    Map<String, Object> references() { return references; }
    Map<String, String> referencePaths() { return referencePaths; }
    boolean frozen() { return frozen; }
}
