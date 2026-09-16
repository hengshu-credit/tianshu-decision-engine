package com.hengshucredit.rule.client.function;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;
import com.alibaba.qlexpress4.runtime.function.QMethodFunction;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.function.AggregateBuiltinFunctionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端函数注册器。
 *
 * <p>QLExpress 4 的公开 API 不支持移除或替换已有函数。每个函数编码只注册一个稳定分发器，
 * 其目标函数按 GLOBAL / PROJECT 作用域保存，因此推送更新、删除和 HTTP 全量同步都能即时生效。
 * 手工 {@link #registerOne(JSONObject)} 注册的函数不属于远端快照，不会被远端删除。</p>
 */
public class ClientFunctionRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ClientFunctionRegistrar.class);
    private static final String GLOBAL = "GLOBAL";
    private static final String PROJECT = "PROJECT";

    private final QLExpressEngine engine;
    private final ApplicationContext applicationContext;
    private final String configuredProjectCode;
    private final Object functionLock = new Object();
    private final Map<String, CustomFunction> manualFunctions = new ConcurrentHashMap<>();
    private final Map<String, CustomFunction> dispatchFunctions = new ConcurrentHashMap<>();
    private volatile Map<RemoteFunctionKey, CustomFunction> remoteFunctions = new ConcurrentHashMap<>();

    public ClientFunctionRegistrar(QLExpressEngine engine, ApplicationContext applicationContext) {
        this(engine, applicationContext, null);
    }

    public ClientFunctionRegistrar(QLExpressEngine engine, ApplicationContext applicationContext,
                                   String projectCode) {
        this.engine = engine;
        this.applicationContext = applicationContext;
        this.configuredProjectCode = trimToNull(projectCode);
    }

    /** 兼容原有手工批量注册语义；不会替换远端函数快照。 */
    public void registerAll(List<JSONObject> functions) {
        if (functions == null) {
            return;
        }
        for (JSONObject function : functions) {
            registerOne(function);
        }
        AggregateBuiltinFunctionRegistry.register(engine.getRunner());
    }

    /** 注册手工函数，作为远端 PROJECT/GLOBAL 函数都不存在时的后备实现。 */
    public void registerOne(JSONObject function) {
        if (function == null) {
            return;
        }
        String funcCode = trimToNull(function.getString("funcCode"));
        if (funcCode == null) {
            return;
        }
        try {
            CustomFunction target = buildFunction(function);
            if (target != null) {
                registerManual(funcCode, target);
            }
        } catch (Exception e) {
            log.error("[ClientFuncReg] 注册手工函数 {} 失败: {}", funcCode, e.getMessage(), e);
        }
    }

    /**
     * 使用 HTTP 成功返回的完整函数列表替换远端函数快照。
     * PROJECT 条目未带 projectCode 时，接口上下文决定其属于当前配置项目。
     */
    public void replaceRemoteSnapshot(List<JSONObject> functions) {
        if (functions == null) {
            throw new IllegalArgumentException("Remote function snapshot must not be null");
        }
        Map<RemoteFunctionKey, CustomFunction> snapshot = new ConcurrentHashMap<>();
        for (JSONObject function : functions) {
            if (function == null) {
                continue;
            }
            String funcCode = trimToNull(function.getString("funcCode"));
            if (funcCode == null) {
                continue;
            }
            RemoteFunctionKey key = remoteKey(function.getString("scope"),
                    function.getString("projectCode"), funcCode, true);
            if (key == null) {
                continue;
            }
            try {
                CustomFunction target = buildFunction(function);
                if (target != null) {
                    snapshot.put(key, target);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to prepare remote function " + funcCode, e);
            }
        }
        synchronized (functionLock) {
            for (RemoteFunctionKey key : snapshot.keySet()) {
                if (!ensureDispatcher(key.funcCode)) {
                    throw new IllegalStateException("Function code conflicts with a non-client registration: "
                            + key.funcCode);
                }
            }
            remoteFunctions = snapshot;
        }
        AggregateBuiltinFunctionRegistry.register(engine.getRunner());
    }

    /** 接收 Redis 函数更新；scope 缺失的旧消息按“有 projectCode 为 PROJECT，否则 GLOBAL”兼容。 */
    public void registerRemoteFromPush(String scope, String projectCode, String funcCode, String implType,
                                       String implScript, String implClass, String implMethod,
                                       String implBeanName, String paramsJson) {
        RemoteFunctionKey key = remoteKey(scope, projectCode, funcCode, false);
        if (key == null) {
            return;
        }
        JSONObject function = new JSONObject();
        function.put("funcCode", funcCode);
        function.put("implType", implType);
        function.put("implScript", implScript);
        function.put("implClass", implClass);
        function.put("implMethod", implMethod);
        function.put("implBeanName", implBeanName);
        function.put("paramsJson", paramsJson);
        try {
            CustomFunction target = buildFunction(function);
            if (target == null) {
                return;
            }
            synchronized (functionLock) {
                if (!ensureDispatcher(key.funcCode)) {
                    log.warn("[ClientFuncReg] 函数 {} 与非客户端注册冲突，忽略远端更新", key.funcCode);
                    return;
                }
                Map<RemoteFunctionKey, CustomFunction> next = new ConcurrentHashMap<>(remoteFunctions);
                next.put(key, target);
                remoteFunctions = next;
            }
            AggregateBuiltinFunctionRegistry.register(engine.getRunner());
            log.info("[ClientFuncReg] 注册/更新远端 {} 函数: {}", key.scope, key.funcCode);
        } catch (Exception e) {
            log.error("[ClientFuncReg] 注册远端函数 {} 失败: {}", funcCode, e.getMessage(), e);
        }
    }

    /**
     * 兼容旧版调用方：旧签名不携带 scope/projectCode，按全局远端函数处理。
     * 新的 Redis 推送路径必须调用带 scope/projectCode 的重载。
     */
    public void registerFromPush(String funcCode, String implType, String implScript,
                                 String implClass, String implMethod, String implBeanName,
                                 String paramsJson) {
        registerRemoteFromPush(null, null, funcCode, implType, implScript, implClass,
                implMethod, implBeanName, paramsJson);
    }

    /** 删除指定作用域的远端函数；同名另一作用域或手工函数会继续作为后备实现。 */
    public void removeRemote(String scope, String projectCode, String funcCode) {
        RemoteFunctionKey key = remoteKey(scope, projectCode, funcCode, false);
        if (key == null) {
            return;
        }
        synchronized (functionLock) {
            if (!remoteFunctions.containsKey(key)) {
                return;
            }
            Map<RemoteFunctionKey, CustomFunction> next = new ConcurrentHashMap<>(remoteFunctions);
            next.remove(key);
            remoteFunctions = next;
        }
        log.info("[ClientFuncReg] 已移除远端 {} 函数: {}", key.scope, key.funcCode);
    }

    /** 兼容原有无 scope 的删除调用：按 GLOBAL 处理。 */
    public void remove(String funcCode) {
        removeRemote(null, null, funcCode);
    }

    public boolean hasRemoteFunction(String scope, String projectCode, String funcCode) {
        RemoteFunctionKey key = remoteKey(scope, projectCode, funcCode, false);
        return key != null && remoteFunctions.containsKey(key);
    }

    private void registerManual(String funcCode, CustomFunction target) {
        synchronized (functionLock) {
            if (!ensureDispatcher(funcCode)) {
                log.warn("[ClientFuncReg] 函数 {} 与非客户端注册冲突，忽略手工注册", funcCode);
                return;
            }
            manualFunctions.put(funcCode, target);
        }
        log.info("[ClientFuncReg] 注册/更新手工函数: {}", funcCode);
    }

    private boolean ensureDispatcher(String funcCode) {
        if (dispatchFunctions.containsKey(funcCode)) {
            return true;
        }
        CustomFunction dispatcher = (context, parameters) -> {
            CustomFunction target = resolveTarget(funcCode);
            if (target == null) {
                throw new IllegalStateException("函数不存在或已删除: " + funcCode);
            }
            return target.call(context, parameters);
        };
        if (!engine.getRunner().addFunction(funcCode, dispatcher)) {
            return false;
        }
        dispatchFunctions.put(funcCode, dispatcher);
        return true;
    }

    private CustomFunction resolveTarget(String funcCode) {
        Map<RemoteFunctionKey, CustomFunction> remoteSnapshot = remoteFunctions;
        if (configuredProjectCode != null) {
            CustomFunction project = remoteSnapshot.get(new RemoteFunctionKey(PROJECT, configuredProjectCode, funcCode));
            if (project != null) {
                return project;
            }
        }
        CustomFunction global = remoteSnapshot.get(new RemoteFunctionKey(GLOBAL, null, funcCode));
        return global != null ? global : manualFunctions.get(funcCode);
    }

    private CustomFunction buildFunction(JSONObject function) throws Exception {
        String implType = trimToNull(function.getString("implType"));
        if (implType == null) {
            return null;
        }
        switch (implType) {
            case "SCRIPT":
                return buildScriptFunction(function);
            case "JAVA":
                return buildJavaFunction(function);
            case "BEAN":
                return buildBeanFunction(function);
            default:
                log.warn("[ClientFuncReg] 未知函数类型: {}", implType);
                return null;
        }
    }

    private CustomFunction buildScriptFunction(JSONObject function) {
        String script = trimToNull(function.getString("implScript"));
        if (script == null) {
            return null;
        }
        List<String> paramNames = extractParamNames(function.getString("paramsJson"));
        QLExpressEngine.PreparedScript prepared = engine.prepare(script);
        return (context, parameters) -> {
            Map<String, Object> scriptContext = new HashMap<>();
            for (int i = 0; i < paramNames.size(); i++) {
                scriptContext.put(paramNames.get(i), i < parameters.size() ? parameters.get(i).get() : null);
            }
            com.hengshucredit.rule.model.dto.RuleResult result = engine.execute(prepared, scriptContext, false);
            if (!result.isSuccess()) throw new IllegalStateException(result.getErrorMessage());
            return result.getResult();
        };
    }

    private CustomFunction buildJavaFunction(JSONObject function) throws Exception {
        String funcCode = function.getString("funcCode");
        String className = trimToNull(function.getString("implClass"));
        if (className == null) {
            log.warn("[ClientFuncReg] JAVA 函数 {} 未配置 implClass", funcCode);
            return null;
        }
        String methodName = trimToNull(function.getString("implMethod"));
        if (methodName == null) {
            methodName = funcCode;
        }
        Object instance = Class.forName(className).getDeclaredConstructor().newInstance();
        Method method = instance.getClass().getMethod(methodName, resolveParamTypes(function.getString("paramsJson")));
        return new QMethodFunction(instance, method);
    }

    private CustomFunction buildBeanFunction(JSONObject function) throws Exception {
        String funcCode = function.getString("funcCode");
        String beanName = trimToNull(function.getString("implBeanName"));
        if (beanName == null || applicationContext == null) {
            log.warn("[ClientFuncReg] BEAN 函数 {} 无法注册", funcCode);
            return null;
        }
        String methodName = trimToNull(function.getString("implMethod"));
        if (methodName == null) {
            methodName = funcCode;
        }
        Object bean = applicationContext.getBean(beanName);
        Method method = bean.getClass().getMethod(methodName, resolveParamTypes(function.getString("paramsJson")));
        return new QMethodFunction(bean, method);
    }

    private RemoteFunctionKey remoteKey(String scope, String projectCode, String funcCode,
                                        boolean projectSnapshotFallback) {
        String code = trimToNull(funcCode);
        if (code == null) {
            return null;
        }
        String normalizedScope = trimToNull(scope);
        String normalizedProjectCode = trimToNull(projectCode);
        if (normalizedScope == null) {
            // 旧版消息未携带 scope：有项目编码即项目函数，缺失编码即全局函数。
            return normalizedProjectCode == null
                    ? new RemoteFunctionKey(GLOBAL, null, code)
                    : new RemoteFunctionKey(PROJECT, normalizedProjectCode, code);
        }
        if (GLOBAL.equals(normalizedScope)) {
            return new RemoteFunctionKey(GLOBAL, null, code);
        }
        if (PROJECT.equals(normalizedScope)) {
            String effectiveProjectCode = normalizedProjectCode;
            if (effectiveProjectCode == null && projectSnapshotFallback) {
                effectiveProjectCode = configuredProjectCode;
            }
            if (effectiveProjectCode == null) {
                log.warn("[ClientFuncReg] PROJECT 函数 {} 缺少 projectCode", code);
                return null;
            }
            return new RemoteFunctionKey(PROJECT, effectiveProjectCode, code);
        }
        log.warn("[ClientFuncReg] 未知函数 scope: {}", normalizedScope);
        return null;
    }

    private List<String> extractParamNames(String paramsJson) {
        List<String> names = new ArrayList<>();
        if (paramsJson == null || paramsJson.trim().isEmpty()) return names;
        try {
            JSONArray arr = JSON.parseArray(paramsJson);
            for (int i = 0; i < arr.size(); i++) {
                String name = arr.getJSONObject(i).getString("name");
                if (name != null && !name.trim().isEmpty()) names.add(name.trim());
            }
        } catch (Exception e) {
            log.warn("[ClientFuncReg] 解析 paramsJson 失败: {}", e.getMessage());
        }
        return names;
    }

    private Class<?>[] resolveParamTypes(String paramsJson) {
        if (paramsJson == null || paramsJson.trim().isEmpty()) return new Class<?>[0];
        try {
            JSONArray arr = JSON.parseArray(paramsJson);
            Class<?>[] types = new Class<?>[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                types[i] = mapParamType(arr.getJSONObject(i).getString("type"));
            }
            return types;
        } catch (Exception e) {
            return new Class<?>[0];
        }
    }

    private Class<?> mapParamType(String type) {
        if (type == null) return Object.class;
        switch (type.toUpperCase()) {
            case "NUMBER": return double.class;
            case "STRING": return String.class;
            case "BOOLEAN": return boolean.class;
            default: return Object.class;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class RemoteFunctionKey {
        private final String scope;
        private final String projectCode;
        private final String funcCode;

        private RemoteFunctionKey(String scope, String projectCode, String funcCode) {
            this.scope = scope;
            this.projectCode = projectCode;
            this.funcCode = funcCode;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RemoteFunctionKey)) return false;
            RemoteFunctionKey that = (RemoteFunctionKey) other;
            return scope.equals(that.scope) && java.util.Objects.equals(projectCode, that.projectCode)
                    && funcCode.equals(that.funcCode);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(scope, projectCode, funcCode);
        }
    }
}
