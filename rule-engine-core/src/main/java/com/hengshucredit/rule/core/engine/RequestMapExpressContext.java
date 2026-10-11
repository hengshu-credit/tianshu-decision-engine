package com.hengshucredit.rule.core.engine;

import com.alibaba.qlexpress4.runtime.Value;
import com.alibaba.qlexpress4.runtime.context.ExpressContext;
import com.alibaba.qlexpress4.runtime.data.MapItemValue;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** QL 全局变量直接读写请求上下文；局部变量仍由 QL 的词法作用域管理。 */
final class RequestMapExpressContext extends AbstractMap<String, Object> implements ExpressContext, RuntimeWriteTarget {
    private final Map<String, Object> source;
    private final RequestContext request;
    private final Map<String, Value> handles = new HashMap<>();
    private Map<String, Object> readOnlyWrites;

    RequestMapExpressContext(Map<String, Object> source, RequestContext request) {
        this.source = source;
        this.request = request;
    }

    @Override public Value get(Map<String, Object> attachments, String name) {
        return handles.computeIfAbsent(name, key -> new MapItemValue(key, this, key) {
            @Override public void setInner(Object value) { request.setValue(key, value); }
        });
    }

    @Override public Object get(Object key) {
        if (readOnlyWrites != null && readOnlyWrites.containsKey(key)) return readOnlyWrites.get(key);
        return source.get(key);
    }

    @Override public Object runtimeValue(String key) {
        if (readOnlyWrites != null && readOnlyWrites.containsKey(key)) return readOnlyWrites.get(key);
        return source instanceof RuntimeWriteTarget target ? target.runtimeValue(key) : source.get(key);
    }

    @Override public Object put(String key, Object value) {
        try { return source.put(key, value); }
        catch (UnsupportedOperationException readOnly) {
            Object previous = runtimeValue(key);
            if (readOnlyWrites == null) readOnlyWrites = new LinkedHashMap<>();
            readOnlyWrites.put(key, value);
            return previous;
        }
    }

    @Override public boolean containsKey(Object key) {
        return readOnlyWrites != null && readOnlyWrites.containsKey(key) || source.containsKey(key);
    }

    @Override public Set<Entry<String, Object>> entrySet() {
        if (readOnlyWrites == null) return source.entrySet();
        Map<String, Object> combined = new LinkedHashMap<>(source);
        combined.putAll(readOnlyWrites);
        return combined.entrySet();
    }
}
