package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import org.springframework.beans.BeanUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/** 正式模型版本执行期间持有的外数配置，仅覆盖该版本明确绑定的接口。 */
public final class FrozenExternalApiScope implements AutoCloseable {
    private static final ThreadLocal<FrozenExternalApiScope> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<FrozenExternalApiScope> INVOCATION = new ThreadLocal<>();
    private final FrozenExternalApiScope previous;
    private final Map<Long, RuleExternalApiConfig> apis;
    private final Map<Long, RuleExternalDatasource> datasources;
    private final Map<String, String> paths;
    private final String identity;

    private FrozenExternalApiScope(Map<Long, RuleExternalApiConfig> apis, Map<Long, RuleExternalDatasource> datasources,
                                   Map<String, String> paths, String identity) {
        this.previous = CURRENT.get();
        this.apis = apis;
        this.datasources = datasources;
        this.paths = paths;
        this.identity = identity;
        CURRENT.set(this);
    }

    public static FrozenExternalApiScope open(Map<Long, RuleExternalApiConfig> apis,
                                               Map<Long, RuleExternalDatasource> datasources,
                                               Map<String, String> paths, String identity) {
        if (identity == null || identity.isBlank()) throw new IllegalArgumentException("模型外数版本身份不能为空");
        Map<Long, RuleExternalApiConfig> savedApis = new HashMap<>();
        Map<Long, RuleExternalDatasource> savedSources = new HashMap<>();
        datasources.forEach((id, source) -> {
            if (id == null || !id.equals(source.getId())) throw new IllegalArgumentException("冻结数据源 ID 不一致");
            RuleExternalDatasource copy = new RuleExternalDatasource();
            BeanUtils.copyProperties(source, copy);
            savedSources.put(id, copy);
        });
        apis.forEach((id, api) -> {
            if (id == null || !id.equals(api.getId()) || !savedSources.containsKey(api.getDatasourceId()))
                throw new IllegalArgumentException("冻结 API 身份或所属数据源缺失");
            RuleExternalApiConfig copy = new RuleExternalApiConfig();
            BeanUtils.copyProperties(api, copy);
            savedApis.put(id, copy);
        });
        return new FrozenExternalApiScope(Map.copyOf(savedApis), Map.copyOf(savedSources), Map.copyOf(paths), identity);
    }

    static FrozenExternalApiScope forApi(Long id) {
        var scope = CURRENT.get();
        return scope != null && id != null && scope.apis.containsKey(id) ? scope : null;
    }

    static boolean active() { return CURRENT.get() != null; }

    RuleExternalApiConfig api(Long id) { return Objects.requireNonNull(apis.get(id)); }
    RuleExternalDatasource datasource(Long id) { return Objects.requireNonNull(datasources.get(id)); }
    Map<String, String> paths() { return paths; }
    String identity() { return identity; }

    /** 只在匹配的 API 请求内启用冻结数据源、表达式和鉴权，其他 API 即使共用数据源也走常规链路。 */
    static Binding bindApi(Long id) {
        var previous = INVOCATION.get();
        var selected = forApi(id);
        if (previous == selected) return () -> {};
        setInvocation(selected);
        return () -> setInvocation(previous);
    }

    static FrozenExternalApiScope invocation() { return INVOCATION.get(); }

    private static void setInvocation(FrozenExternalApiScope scope) {
        if (scope == null) INVOCATION.remove();
        else INVOCATION.set(scope);
    }

    interface Binding extends AutoCloseable {
        @Override void close();
    }

    static <T> Callable<T> wrap(Callable<T> task) {
        var captured = CURRENT.get();
        if (captured == null) return task;
        return () -> {
            try (var ignored = new FrozenExternalApiScope(captured.apis, captured.datasources, captured.paths, captured.identity)) {
                return task.call();
            }
        };
    }

    @Override public void close() {
        if (CURRENT.get() != this) throw new IllegalStateException("模型外数作用域须在打开线程按嵌套顺序关闭");
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
