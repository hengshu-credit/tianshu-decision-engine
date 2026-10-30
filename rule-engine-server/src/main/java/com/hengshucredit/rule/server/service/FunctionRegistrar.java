package com.hengshucredit.rule.server.service;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;
import com.alibaba.qlexpress4.runtime.function.QMethodFunction;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.core.function.FunctionParameterContract;
import com.hengshucredit.rule.core.function.FunctionScriptContract;
import com.hengshucredit.rule.core.function.ManagedFunctionInvoker;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.server.functions.RuleListFunctions;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * 函数注册器 —— 将 rule_function 表中的自定义函数注册到 QLExpress 引擎。
 *
 * <ul>
 *   <li>SCRIPT 类型：包装为 QLExpress function 定义，拼接在编译脚本前面</li>
 *   <li>JAVA / BEAN 类型：Runner 注册稳定分发器，具体实现由请求中的冻结函数集选择</li>
 * </ul>
 */
@Service
public class FunctionRegistrar {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private RuleListFunctions ruleListFunctions;

    /** 缓存 JAVA 类型的实例，避免重复反射创建 */
    private final Map<String, Object> javaInstanceCache = new ConcurrentHashMap<>();
    private final Map<FunctionKey, CustomFunction> targets = new LinkedHashMap<>(16, 0.75f, true);
    private static final int MAX_FUNCTION_TARGETS = 1024;
    private static final Map<String, RuleFunction> BUILTINS = builtinDefinitions();

    /**
     * 将 SCRIPT 类型函数包装为 QLExpress function 定义脚本，用于拼接在编译脚本之前。
     *
     * @param functions SCRIPT 类型的函数列表
     * @return 拼接好的函数定义脚本（可能为空字符串）
     */
    public String buildScriptFunctionPrefix(List<RuleFunction> functions) {
        if (functions == null || functions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (RuleFunction func : functions) {
            if (!"SCRIPT".equals(func.getImplType())) {
                continue;
            }
            String script = func.getImplScript();
            FunctionScriptContract.requireIdentifier(func.getFuncCode());
            if (script == null || script.trim().isEmpty()) throw new IllegalArgumentException("SCRIPT 函数实现脚本不能为空");
            List<String> paramNames = FunctionParameterContract.names(func.getParamsJson());
            sb.append("function ").append(func.getFuncCode()).append("(");
            for (int i = 0; i < paramNames.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(paramNames.get(i));
            }
            sb.append(") {\n");
            sb.append("    ").append(script.trim().replace("\n", "\n    "));
            sb.append("\n}\n\n");
        }
        return sb.toString();
    }

    /** Prepare without executing user code or installing request state. Safe for startup and publication. */
    public Map<String, CustomFunction> prepareFunctions(List<RuleFunction> functions, Express4Runner runner) {
        registerServerFunctions(runner);
        Map<String, CustomFunction> bindings = new LinkedHashMap<>();
        Map<Long, FunctionKey> nativeIdentities = new LinkedHashMap<>();
        for (RuleFunction function : functions == null ? Collections.<RuleFunction>emptyList() : functions) {
            if (function == null || !"JAVA".equals(function.getImplType()) && !"BEAN".equals(function.getImplType())) continue;
            CustomFunction target = prepareTarget(function);
            if (function.getId() != null) {
                FunctionKey identity = new FunctionKey(function.getId(), function.getImplType(), function.getImplClass(),
                        function.getImplBeanName(), resolveMethodName(function), function.getParamsJson());
                FunctionKey previous = nativeIdentities.putIfAbsent(function.getId(), identity);
                if (previous != null && !previous.equals(identity)) throw new IllegalStateException("同一函数 ID 对应多个实现: " + function.getId());
                bindings.put(ManagedFunctionInvoker.bindingKey(function.getId()), target);
            }
        }
        Map<String, RuleFunction> selected = new LinkedHashMap<>();
        java.util.Set<String> ambiguousCodes = new java.util.HashSet<>();
        for (RuleFunction function : functions == null ? Collections.<RuleFunction>emptyList() : functions) {
            if (function == null) continue;
            RuleFunction previous = selected.get(function.getFuncCode());
            if (previous != null && !Objects.equals(previous.getId(), function.getId())) ambiguousCodes.add(function.getFuncCode());
            if (previous == null || "GLOBAL".equals(previous.getScope())) {
                selected.put(function.getFuncCode(), function);
            }
        }
        for (RuleFunction function : selected.values()) {
            if (!"JAVA".equals(function.getImplType()) && !"BEAN".equals(function.getImplType())) continue;
            String code = function.getFuncCode();
            synchronized (runner) {
                CustomFunction existing = runner.getFunction(code);
                if (existing != null && !(existing instanceof RequestFunction)) {
                    if (isBuiltin(function)) continue;
                    throw new IllegalStateException("函数编码与内置或外部注册冲突: " + code);
                }
                if (existing == null && !runner.addFunction(code, new RequestFunction(code))) {
                    throw new IllegalStateException("注册函数分发器失败: " + code);
                }
            }
            if (!ambiguousCodes.contains(code)) bindings.put(code, prepareTarget(function));
        }
        return Map.copyOf(bindings);
    }

    private CustomFunction prepareTarget(RuleFunction function) {
        FunctionKey key = new FunctionKey(function.getId(), function.getImplType(), function.getImplClass(),
                function.getImplBeanName(), resolveMethodName(function), function.getParamsJson());
        synchronized (targets) {
            CustomFunction cached = targets.get(key);
            if (cached != null) return cached;
            try {
                Object instance;
                Method method;
                if ("JAVA".equals(key.type())) {
                    Class<?> implementation = loadFunctionClass(key.className());
                    method = FunctionParameterContract.resolveMethod(implementation, key.method(), function.getFuncCode(), key.params());
                    com.hengshucredit.rule.core.function.NativeFunctionSecurity.requireAllowed(method, function.getFuncCode());
                    instance = Modifier.isStatic(method.getModifiers()) ? null : javaInstanceCache.computeIfAbsent(key.className(), name -> {
                        try { return implementation.getDeclaredConstructor().newInstance(); }
                        catch (Exception e) { throw new IllegalStateException("无法实例化 Java 类: " + name, e); }
                    });
                } else {
                    FunctionParameterContract.parse(key.params());
                    Class<?> implementation = applicationContext.getType(key.beanName(), false);
                    if (implementation == null) throw new IllegalArgumentException("无法确认 Bean 方法的脚本授权");
                    method = FunctionParameterContract.resolveMethod(implementation, key.method(), function.getFuncCode(), key.params());
                    com.hengshucredit.rule.core.function.NativeFunctionSecurity.requireAllowed(method, function.getFuncCode());
                    instance = applicationContext.getBean(key.beanName());
                    method = FunctionParameterContract.resolveMethod(instance.getClass(), key.method(), function.getFuncCode(), key.params());
                    com.hengshucredit.rule.core.function.NativeFunctionSecurity.requireAllowed(method, function.getFuncCode());
                }
                CustomFunction target = new QMethodFunction(instance, method);
                if (targets.size() >= MAX_FUNCTION_TARGETS) targets.remove(targets.keySet().iterator().next());
                targets.put(key, target);
                return target;
            } catch (Exception e) {
                throw new IllegalStateException("准备函数失败: " + function.getFuncCode(), e);
            }
        }
    }

    /** Static dependency check for publication/warmup, never for each execution. */
    public void validateFunctionBindings(String script, Map<String, CustomFunction> bindings, Express4Runner runner) {
        validateFunctionBindings(runner.getOutFunctions(script), bindings, runner);
    }

    /** Validate the function set captured by QLExpress preparation; execution must not reparse source. */
    public void validateFunctionBindings(Iterable<String> functionNames,
                                         Map<String, CustomFunction> bindings,
                                         Express4Runner runner) {
        for (String code : functionNames == null ? Collections.<String>emptyList() : functionNames) {
            CustomFunction registered = runner.getFunction(code);
            if (registered == null || registered instanceof RequestFunction && !bindings.containsKey(code)) {
                throw new IllegalStateException("函数未绑定到当前规则制品，请从函数选择器插入并重新保存: " + code);
            }
        }
    }

    private record FunctionKey(Long id, String type, String className, String beanName, String method, String params) { }

    private static final class RequestFunction implements CustomFunction {
        private final String code;
        private RequestFunction(String code) { this.code = code; }
        @Override
        public Object call(com.alibaba.qlexpress4.runtime.QContext context,
                           com.alibaba.qlexpress4.runtime.Parameters parameters) throws Throwable {
            CustomFunction target = RuntimeContextBridge.currentContext().function(code);
            if (target == null) throw new IllegalStateException("当前规则未绑定函数: " + code);
            return target.call(context, parameters);
        }
    }

    private static Map<String, RuleFunction> builtinDefinitions() {
        Map<String, RuleFunction> definitions = new LinkedHashMap<>();
        for (RuleFunction function : BuiltinFunctionCatalog.definitions()) definitions.put(function.getFuncCode(), function);
        return definitions;
    }

    private boolean isBuiltin(RuleFunction function) {
        RuleFunction builtin = BUILTINS.get(function.getFuncCode());
        return builtin != null && Objects.equals(builtin.getImplType(), function.getImplType())
                && Objects.equals(builtin.getImplClass(), function.getImplClass())
                && Objects.equals(builtin.getImplBeanName(), function.getImplBeanName())
                && Objects.equals(resolveMethodName(builtin), resolveMethodName(function))
                && java.util.Arrays.equals(FunctionParameterContract.resolveTypes(builtin.getParamsJson(), getClass().getClassLoader()),
                        FunctionParameterContract.resolveTypes(function.getParamsJson(), getClass().getClassLoader()));
    }

    static Class<?> loadFunctionClass(String className) throws ClassNotFoundException {
        try {
            return Class.forName(className, false, FunctionRegistrar.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            String[] legacyPrefixes = {
                    "com.hengshucredit.rule.example.functions.",
                    "com.bjjw.rule.example.functions."
            };
            for (String legacyPrefix : legacyPrefixes) {
                if (className != null && className.startsWith(legacyPrefix)) {
                    String simpleName = className.substring(legacyPrefix.length());
                    return Class.forName("com.hengshucredit.rule.server.functions." + simpleName, false, FunctionRegistrar.class.getClassLoader());
                }
            }
            throw e;
        }
    }

    /** 注册依赖服务端数据源的函数；客户端引擎不会注册这些方法。 */
    public void registerServerFunctions(Express4Runner runner) {
        if (runner == null || ruleListFunctions == null) return;
        Class<?>[] twoObjects = {Object.class, Object.class};
        Class<?>[] listMatchArgs = {Object.class, Object.class, String.class, String.class, Object.class};
        runner.addFunctionOfServiceMethod("isInLists", ruleListFunctions, "isInLists", twoObjects);
        runner.addFunctionOfServiceMethod("isInListsNumber", ruleListFunctions, "isInListsNumber", twoObjects);
        runner.addFunctionOfServiceMethod("listMatch", ruleListFunctions, "listMatch", listMatchArgs);
        runner.addFunctionOfServiceMethod("listMatchNumber", ruleListFunctions, "listMatchNumber", listMatchArgs);
    }

    /**
     * 获取方法名：优先使用 implMethod，若未配置则使用 funcCode
     */
    private String resolveMethodName(RuleFunction func) {
        String method = func.getImplMethod();
        return (method != null && !method.trim().isEmpty()) ? method.trim() : func.getFuncCode();
    }

}
