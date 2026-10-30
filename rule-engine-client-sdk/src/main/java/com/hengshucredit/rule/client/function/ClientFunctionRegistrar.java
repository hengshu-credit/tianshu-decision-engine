package com.hengshucredit.rule.client.function;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;
import com.alibaba.qlexpress4.runtime.function.QMethodFunction;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.core.function.AggregateBuiltinFunctionRegistry;
import com.hengshucredit.rule.core.function.FunctionParameterContract;
import com.hengshucredit.rule.core.function.ManagedFunctionInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
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
    private static final int MAX_FROZEN_TARGETS = 1024;
    private final Map<FrozenFunctionKey, CustomFunction> frozenTargets = new LinkedHashMap<>(16, 0.75f, true);

    public ClientFunctionRegistrar(QLExpressEngine engine, ApplicationContext applicationContext) {
        this(engine, applicationContext, null);
    }

    public ClientFunctionRegistrar(QLExpressEngine engine, ApplicationContext applicationContext,
                                   String projectCode) {
        this.engine = engine;
        this.applicationContext = applicationContext;
        this.configuredProjectCode = trimToNull(projectCode);
    }

    /** 仅准备本地类/Bean的冻结定义，不替换远端当前函数，也不发起服务端执行。 */
    public Map<String, CustomFunction> prepareFrozen(List<JSONObject> definitions) {
        Map<String, CustomFunction> bindings = new LinkedHashMap<>();
        Map<String, FrozenFunctionKey> identities = new LinkedHashMap<>();
        Map<String, FrozenFunctionKey> legacyCodes = new LinkedHashMap<>();
        Set<String> ambiguousCodes = new HashSet<>();
        for (JSONObject definition : definitions == null ? List.<JSONObject>of() : definitions) {
            if (definition == null) throw new IllegalStateException("制品冻结函数定义缺失");
            String code = trimToNull(definition.getString("funcCode"));
            String type = trimToNull(definition.getString("implType"));
            if (code == null) throw new IllegalStateException("制品冻结函数缺少编码");
            // SCRIPT函数已经作为函数定义嵌入该规则的compiledScript。
            if ("SCRIPT".equals(type)) continue;
            if (!"JAVA".equals(type) && !"BEAN".equals(type)) throw new IllegalStateException("不支持的冻结函数类型: " + type);
            FrozenFunctionKey key = new FrozenFunctionKey(definition.getLong("id"), code, type,
                    definition.getString("implScript"), definition.getString("implClass"),
                    definition.getString("implBeanName"), definition.getString("implMethod"), definition.getString("paramsJson"));
            String identity = key.id() == null ? "CODE:" + code : ManagedFunctionInvoker.bindingKey(key.id());
            FrozenFunctionKey previous = identities.putIfAbsent(identity, key);
            if (previous != null && !previous.equals(key)) throw new IllegalStateException("同一冻结函数 ID 对应多个定义: " + identity);
            FrozenFunctionKey sameCode = legacyCodes.putIfAbsent(code, key);
            if (sameCode != null && !sameCode.equals(key)) ambiguousCodes.add(code);
            boolean platformBuiltin = isPlatformBuiltin(definition);
            synchronized (functionLock) {
                if (!platformBuiltin && !ensureDispatcher(code)) throw new IllegalStateException("冻结函数编码与已有本地函数冲突: " + code);
            }
            CustomFunction target = frozenTarget(key, definition);
            if (key.id() != null) bindings.put(ManagedFunctionInvoker.bindingKey(key.id()), target);
            if (!platformBuiltin) bindings.put(code, target);
        }
        ambiguousCodes.forEach(bindings::remove);
        return Map.copyOf(bindings);
    }

    private CustomFunction frozenTarget(FrozenFunctionKey key, JSONObject definition) {
        synchronized (frozenTargets) {
            CustomFunction cached = frozenTargets.get(key);
            if (cached != null) return cached;
        }
        CustomFunction prepared;
        try {
            prepared = buildFunction(definition);
            if (prepared == null) throw new IllegalStateException("客户端没有对应实现类或Bean");
        } catch (Exception error) {
            throw new IllegalStateException("无法准备制品冻结函数 " + key.code() + "，请核对客户端本地实现", error);
        }
        synchronized (frozenTargets) {
            CustomFunction existing = frozenTargets.get(key);
            if (existing != null) return existing;
            if (frozenTargets.size() >= MAX_FROZEN_TARGETS) frozenTargets.remove(frozenTargets.keySet().iterator().next());
            frozenTargets.put(key, prepared);
        }
        return prepared;
    }

    private record FrozenFunctionKey(Long id, String code, String type, String script,
                                     String className, String beanName, String method, String params) { }

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
        Set<RemoteFunctionKey> platformBuiltins = new HashSet<>();
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
                CustomFunction target = buildRemoteFunction(function);
                if (target != null) {
                    snapshot.put(key, target);
                    if (isPlatformBuiltin(function)) platformBuiltins.add(key); else platformBuiltins.remove(key);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to prepare remote function " + funcCode, e);
            }
        }
        synchronized (functionLock) {
            for (RemoteFunctionKey key : snapshot.keySet()) {
                if (!platformBuiltins.contains(key) && !ensureDispatcher(key.funcCode)) {
                    throw new IllegalStateException("Function code conflicts with a non-client registration: "
                            + key.funcCode);
                }
            }
            remoteFunctions = snapshot;
        }
        AggregateBuiltinFunctionRegistry.register(engine.getRunner());
    }

    private CustomFunction buildRemoteFunction(JSONObject function) throws Exception {
        if (!"BEAN".equals(function.getString("implType"))) return buildFunction(function);
        String beanName = trimToNull(function.getString("implBeanName"));
        if (beanName == null) return buildFunction(function);
        FunctionParameterContract.parse(function.getString("paramsJson"));
        try {
            CustomFunction target = buildBeanFunction(function);
            if (target != null) return target;
        } catch (Exception error) {
            if (!isMissingBeanFailure(error)) throw error;
        }
        // 同步目录也包含仅服务端部署的 Bean；它不能阻断其他函数的完整快照。
        // 保留明确的失败目标，不能落到旧实现或同名手工函数；冻结制品仍使用严格准备。
        JSONObject definition = new JSONObject(function);
        log.info("[ClientFuncReg] 函数 {} 等待本地 Bean {}，调用前须提供该实现或使用 HTTP 执行",
                function.getString("funcCode"), beanName);
        return (context, parameters) -> {
            CustomFunction target;
            try { target = buildBeanFunction(definition); }
            catch (Exception error) {
                if (!isMissingBeanFailure(error)) throw error;
                throw new IllegalStateException("客户端缺少函数 Bean " + beanName + "，请提供本地实现或使用 HTTP 执行", error);
            }
            if (target == null) throw new IllegalStateException("客户端缺少函数 Bean " + beanName + "，请提供本地实现或使用 HTTP 执行");
            return target.call(context, parameters);
        };
    }

    /** 接收 Redis 函数更新；scope 缺失的旧消息按“有 projectCode 为 PROJECT，否则 GLOBAL”兼容。 */
    public void registerRemoteFromPush(String scope, String projectCode, String funcCode, String implType,
                                       String implScript, String implClass, String implMethod,
                                       String implBeanName, String paramsJson) {
        tryRegisterRemoteFromPush(scope, projectCode, funcCode, implType, implScript, implClass,
                implMethod, implBeanName, paramsJson);
    }

    /**
     * 接收 Redis 函数更新并返回处理结果，供消息幂等层判断是否可以消费 operationId。
     * 保留 {@link #registerRemoteFromPush(String, String, String, String, String, String, String, String, String)}
     * 的 void 签名，兼容已编译的旧客户端调用方。
     */
    public boolean tryRegisterRemoteFromPush(String scope, String projectCode, String funcCode, String implType,
                                       String implScript, String implClass, String implMethod,
                                       String implBeanName, String paramsJson) {
        RemoteFunctionKey key = remoteKey(scope, projectCode, funcCode, false);
        if (key == null) {
            return false;
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
                return false;
            }
            synchronized (functionLock) {
                if (!isPlatformBuiltin(function) && !ensureDispatcher(key.funcCode)) {
                    log.warn("[ClientFuncReg] 函数 {} 与非客户端注册冲突，忽略远端更新", key.funcCode);
                    return false;
                }
                Map<RemoteFunctionKey, CustomFunction> next = new ConcurrentHashMap<>(remoteFunctions);
                next.put(key, target);
                remoteFunctions = next;
            }
            AggregateBuiltinFunctionRegistry.register(engine.getRunner());
            log.info("[ClientFuncReg] 注册/更新远端 {} 函数: {}", key.scope, key.funcCode);
            return true;
        } catch (Exception e) {
            if (isMissingBeanFailure(e)) {
                log.warn("[ClientFuncReg] 远端函数 {} 暂不可注册：Bean {} 尚未就绪，将在下一次推送或同步时重试",
                        funcCode, trimToNull(function.getString("implBeanName")));
            } else {
                log.error("[ClientFuncReg] 注册远端函数 {} 失败: {}", funcCode, e.getMessage(), e);
            }
            return false;
        }
    }

    private boolean isMissingBeanFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if ("org.springframework.beans.factory.NoSuchBeanDefinitionException".equals(
                    current.getClass().getName())) return true;
            current = current.getCause();
        }
        return false;
    }

    private boolean isPlatformBuiltin(JSONObject function) {
        try {
            return function != null && "JAVA".equals(function.getString("implType"))
                    && AggregateBuiltinFunctionRegistry.matchesManagementMethod(function.getString("funcCode"), function.getString("implClass"),
                    function.getString("implMethod"), function.getString("paramsJson"));
        } catch (IllegalArgumentException error) { throw new IllegalStateException("内置函数声明的签名无效", error); }
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
        tryRemoveRemote(scope, projectCode, funcCode);
    }

    /** 删除远端函数并返回处理结果，供消息幂等层判断是否可以消费 operationId。 */
    public boolean tryRemoveRemote(String scope, String projectCode, String funcCode) {
        RemoteFunctionKey key = remoteKey(scope, projectCode, funcCode, false);
        if (key == null) {
            return false;
        }
        synchronized (functionLock) {
            if (!remoteFunctions.containsKey(key)) {
                return true;
            }
            Map<RemoteFunctionKey, CustomFunction> next = new ConcurrentHashMap<>(remoteFunctions);
            next.remove(key);
            remoteFunctions = next;
        }
        log.info("[ClientFuncReg] 已移除远端 {} 函数: {}", key.scope, key.funcCode);
        return true;
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
        var request = RuntimeContextBridge.currentContext();
        if (request.hasFunctionBindings()) {
            CustomFunction frozen = request.function(funcCode);
            if (frozen == null) throw new IllegalStateException("调用函数不在当前规则的冻结快照中: " + funcCode);
            return frozen;
        }
        Map<RemoteFunctionKey, CustomFunction> remoteSnapshot = remoteFunctions;
        String effectiveProjectCode = configuredProjectCode;
        if (effectiveProjectCode == null) {
            Map<String, Object> currentRule = RuntimeContextBridge.currentRule();
            Object currentProjectCode = currentRule == null ? null : currentRule.get("projectCode");
            if (currentProjectCode != null) {
                effectiveProjectCode = trimToNull(String.valueOf(currentProjectCode));
            }
        }
        if (effectiveProjectCode != null) {
            CustomFunction project = remoteSnapshot.get(new RemoteFunctionKey(PROJECT, effectiveProjectCode, funcCode));
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
        List<String> paramNames = FunctionParameterContract.names(function.getString("paramsJson"));
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
        Class<?> implementation = Class.forName(className, false, getClass().getClassLoader());
        Method method = FunctionParameterContract.resolveMethod(implementation, methodName, funcCode, function.getString("paramsJson"));
        com.hengshucredit.rule.core.function.NativeFunctionSecurity.requireAllowed(method, funcCode);
        Object instance = Modifier.isStatic(method.getModifiers()) ? null : implementation.getDeclaredConstructor().newInstance();
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
        FunctionParameterContract.parse(function.getString("paramsJson"));
        Class<?> implementation = applicationContext.getType(beanName, false);
        if (implementation == null) throw new IllegalArgumentException("无法确认 Bean 方法的脚本授权");
        Method declared = FunctionParameterContract.resolveMethod(implementation, methodName, funcCode, function.getString("paramsJson"));
        com.hengshucredit.rule.core.function.NativeFunctionSecurity.requireAllowed(declared, funcCode);
        Object bean = applicationContext.getBean(beanName);
        Method method = FunctionParameterContract.resolveMethod(bean.getClass(), methodName, funcCode, function.getString("paramsJson"));
        com.hengshucredit.rule.core.function.NativeFunctionSecurity.requireAllowed(method, funcCode);
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
