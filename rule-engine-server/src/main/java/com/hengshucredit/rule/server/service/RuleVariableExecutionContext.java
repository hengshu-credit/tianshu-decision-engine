package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.server.derived.HistoryFieldValues;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 图规则保留根字段按需解析；对象仅显式开启的引用型叶子延迟取值。 */
final class RuleVariableExecutionContext {
    private RuleVariableExecutionContext() { }

    static Scope prepare(String modelType, Map<String, Object> values,
            VariableResolveOptions options, DataObjectFieldReferenceResolver.ReferencePlan plan,
            Set<String> explicitTargets, Runnable resolve) {
        return new Scope(modelType, values, options, plan, explicitTargets, resolve);
    }

    static final class Scope extends AbstractMap<String, Object> implements AutoCloseable {
        private final Map<String, Object> values;
        private final VariableResolveOptions options;
        private final DataObjectFieldReferenceResolver.ReferencePlan plan;
        private final Set<String> explicitTargets;
        private final Runnable resolve;
        private final Set<String> required;
        private final Set<String> direct;
        private final Set<String> statusKeys;
        private final com.hengshucredit.rule.core.engine.RequestContext.SourceStateScope statusScope;
        private final Set<String> pendingRoots = new LinkedHashSet<>();
        private final boolean graph;
        private boolean active = true;

        private Scope(String modelType, Map<String, Object> values, VariableResolveOptions options,
                DataObjectFieldReferenceResolver.ReferencePlan plan, Set<String> explicitTargets, Runnable resolve) {
            this.values = values;
            this.options = options;
            this.plan = plan;
            this.explicitTargets = explicitTargets;
            this.resolve = resolve;
            required = options.getRequiredScriptNames() == null ? Set.of() : new LinkedHashSet<>(options.getRequiredScriptNames());
            direct = plan.withoutBindingNames(required);
            statusKeys = options.getStatusReferenceKeys() == null ? Set.of() : new LinkedHashSet<>(options.getStatusReferenceKeys());
            Set<String> eagerStatus = new LinkedHashSet<>(statusKeys); eagerStatus.removeAll(plan.deferredStatusKeys());
            graph = ("FLOW".equalsIgnoreCase(modelType) || "TREE".equalsIgnoreCase(modelType))
                    && eagerStatus.isEmpty();
            for (String name : required) if (name != null && !name.isBlank()) pendingRoots.add(root(name));
            for (String name : plan.targetPaths()) pendingRoots.add(root(name));
            options.setRequiredNamesUpstreamOnly(true);
            Set<String> initial = graph ? new LinkedHashSet<>() : new LinkedHashSet<>(direct);
            if (!graph) initial.addAll(plan.missingSources(null, values, false));
            resolveNames(initial);
            statusScope = RuntimeContextBridge.currentContext().bindSourceStateResolver(key -> {
                Set<String> selected = plan.sourcesForStatus(key, values);
                if (!selected.isEmpty()) resolveNames(selected);
            });
        }

        private static String root(String path) { return path.split("[.\\[]", 2)[0]; }

        private void resolveNames(Set<String> selected) {
            options.setRequiredScriptNames(selected);
            options.setStatusReferenceKeys(plan.sourceStatusKeys(selected, statusKeys));
            try {
                resolve.run();
                plan.apply(values, explicitTargets, selected);
                plan.copyTargetStates(values, options);
                RuntimeContextBridge.replaceSourceStates(options.getSourceStates());
            } finally { options.setRequiredScriptNames(required); options.setStatusReferenceKeys(statusKeys); }
        }

        private void read(String path) {
            if (!active) return;
            Set<String> selected = new LinkedHashSet<>();
            if (graph && pendingRoots.remove(path)) {
                for (String name : direct) if (name != null && (name.equals(path) || name.startsWith(path + ".") || name.startsWith(path + "["))) selected.add(name);
            }
            selected.addAll(plan.missingSources(path, values, false));
            if (!selected.isEmpty()) resolveNames(selected);
        }

        private Object value(String path) {
            read(path);
            Object value = HistoryFieldValues.read(values, path);
            boolean pendingChildren = plan.lazyPaths().stream().anyMatch(target -> target.startsWith(path + ".") && !HistoryFieldValues.present(values, target));
            if (active && pendingChildren && (value instanceof Map || !HistoryFieldValues.present(values, path))) return new ObjectView(path);
            return value;
        }

        @Override public Object get(Object key) { return key instanceof String name ? value(name) : values.get(key); }
        @Override public boolean containsKey(Object key) { return values.containsKey(key) || key instanceof String name && plan.lazyPaths().stream().anyMatch(path -> path.startsWith(name + ".")); }
        @Override public Set<Entry<String, Object>> entrySet() { return values.entrySet(); }
        @Override public Object put(String key, Object value) {
            pendingRoots.remove(key);
            return values.put(key, unwrap(value));
        }

        private Object unwrap(Object value) { return value instanceof ObjectView view ? HistoryFieldValues.read(values, view.path) : value; }

        private final class ObjectView extends AbstractMap<String, Object> {
            private final String path;
            private ObjectView(String path) { this.path = path; }
            @Override public Object get(Object key) { return key instanceof String name ? value(path + "." + name) : null; }
            @Override public boolean containsKey(Object key) {
                return key instanceof String name && (HistoryFieldValues.present(values, path + "." + name) || plan.targetPaths().contains(path + "." + name));
            }
            @Override @SuppressWarnings("unchecked") public Set<Entry<String, Object>> entrySet() {
                // Trace/log inspection is a snapshot of values already read, never a reason to call a provider.
                Object current = HistoryFieldValues.read(values, path);
                if (!(current instanceof Map<?, ?> map)) return Set.of();
                return ((Map<String, Object>) map).entrySet();
            }
            @Override @SuppressWarnings("unchecked") public Object put(String key, Object value) {
                Map<String, Object> current = values;
                for (String part : path.split("\\.")) {
                    Object child = current.get(part);
                    if (child == null) { child = new LinkedHashMap<String, Object>(); current.put(part, child); }
                    if (!(child instanceof Map)) throw new IllegalArgumentException("对象字段路径不是对象: " + path);
                    current = (Map<String, Object>) child;
                }
                return current.put(key, unwrap(value));
            }
        }

        @Override public void close() { active = false; statusScope.close(); }
    }
}
