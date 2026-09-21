package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;
import com.alibaba.qlexpress4.runtime.function.QMethodFunction;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.server.functions.RuleListFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
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

    private static final Logger log = LoggerFactory.getLogger(FunctionRegistrar.class);

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
            if (script == null || script.trim().isEmpty()) {
                continue;
            }
            List<String> paramNames = extractParamNames(func.getParamsJson());
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
        Map<String, RuleFunction> selected = new LinkedHashMap<>();
        for (RuleFunction function : functions == null ? Collections.<RuleFunction>emptyList() : functions) {
            if (function == null) continue;
            RuleFunction previous = selected.get(function.getFuncCode());
            if (previous == null || "GLOBAL".equals(previous.getScope())) {
                selected.put(function.getFuncCode(), function);
            } else if (!"GLOBAL".equals(function.getScope()) && !Objects.equals(previous.getId(), function.getId())) {
                throw new IllegalStateException("同一函数编码关联多个 ID: " + function.getFuncCode());
            }
        }
        Map<String, CustomFunction> bindings = new LinkedHashMap<>();
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
            bindings.put(code, prepareTarget(function));
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
                if ("JAVA".equals(key.type())) {
                    instance = javaInstanceCache.computeIfAbsent(key.className(), name -> {
                        try { return loadFunctionClass(name).getDeclaredConstructor().newInstance(); }
                        catch (Exception e) { throw new IllegalStateException("无法实例化 Java 类: " + name, e); }
                    });
                } else {
                    instance = applicationContext.getBean(key.beanName());
                }
                CustomFunction target = new QMethodFunction(instance,
                        instance.getClass().getMethod(key.method(), resolveParamTypes(key.params())));
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
        for (String code : runner.getOutFunctions(script)) {
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
                && Objects.equals(resolveMethodName(builtin), resolveMethodName(function));
    }

    private Class<?> loadFunctionClass(String className) throws ClassNotFoundException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            String[] legacyPrefixes = {
                    "com.hengshucredit.rule.example.functions.",
                    "com.bjjw.rule.example.functions."
            };
            for (String legacyPrefix : legacyPrefixes) {
                if (className != null && className.startsWith(legacyPrefix)) {
                    String simpleName = className.substring(legacyPrefix.length());
                    return Class.forName("com.hengshucredit.rule.server.functions." + simpleName);
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
     * 从 params_json 中提取参数名列表
     */
    private List<String> extractParamNames(String paramsJson) {
        List<String> names = new ArrayList<>();
        if (paramsJson == null || paramsJson.trim().isEmpty()) return names;
        try {
            JSONArray arr = JSON.parseArray(paramsJson);
            for (int i = 0; i < arr.size(); i++) {
                JSONObject p = arr.getJSONObject(i);
                String name = p.getString("name");
                if (name != null && !name.trim().isEmpty()) {
                    names.add(name.trim());
                }
            }
        } catch (Exception e) {
            log.warn("[FunctionRegistrar] 解析 params_json 失败: {}", e.getMessage());
        }
        return names;
    }

    /**
     * 获取方法名：优先使用 implMethod，若未配置则使用 funcCode
     */
    private String resolveMethodName(RuleFunction func) {
        String method = func.getImplMethod();
        return (method != null && !method.trim().isEmpty()) ? method.trim() : func.getFuncCode();
    }

    /**
     * 根据 params_json 中的类型映射为 Java Class 数组。
     * 用于 addFunctionOfServiceMethod 的参数类型签名。
     */
    private Class<?>[] resolveParamTypes(String paramsJson) {
        if (paramsJson == null || paramsJson.trim().isEmpty()) {
            return new Class<?>[0];
        }
        try {
            JSONArray arr = JSON.parseArray(paramsJson);
            Class<?>[] types = new Class<?>[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                String type = arr.getJSONObject(i).getString("type");
                types[i] = mapParamType(type);
            }
            return types;
        } catch (Exception e) {
            log.warn("[FunctionRegistrar] 解析参数类型失败: {}", e.getMessage());
            return new Class<?>[0];
        }
    }

    /**
     * 规则参数类型 → Java 类型映射
     */
    private Class<?> mapParamType(String type) {
        if (type == null) return Object.class;
        switch (type.toUpperCase()) {
            case "NUMBER":  return double.class;
            case "STRING":  return String.class;
            case "BOOLEAN": return boolean.class;
            default:        return Object.class;
        }
    }
}
