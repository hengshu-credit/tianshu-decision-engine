package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.function.FunctionParameterContract;
import com.hengshucredit.rule.model.dto.FunctionMethodCatalog;
import com.hengshucredit.rule.model.entity.RuleFunction;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class FunctionMethodCatalogService {
    @Resource private ApplicationContext applicationContext;

    public FunctionMethodCatalog inspect(RuleFunction function) {
        if (function == null || !"JAVA".equals(function.getImplType()) && !"BEAN".equals(function.getImplType()))
            return unavailable("INVALID", "请选择 Java 类或 Spring Bean 实现", null);
        Class<?> implementation;
        boolean javaClass = "JAVA".equals(function.getImplType());
        String target = javaClass ? function.getImplClass() : function.getImplBeanName();
        if (target == null || target.isBlank()) return unavailable("INVALID", javaClass ? "请填写 Java 类名" : "请填写 Bean 名称", null);
        var platformMethod = com.hengshucredit.rule.core.function.AggregateBuiltinFunctionRegistry.managementMethod(function.getFuncCode());
        if (com.hengshucredit.rule.core.function.ManagedFunctionInvoker.NAME.equals(function.getFuncCode())
                || platformMethod != null && (!javaClass || !platformMethod.getDeclaringClass().getName().equals(target)))
            return unavailable("INVALID", "该函数编码由平台内置实现使用，请更换函数编码", target);
        try {
            // Class.forName(..., false, ...) 和 getType(..., false) 均不为探测创建实现实例。
            implementation = javaClass ? FunctionRegistrar.loadFunctionClass(target)
                    : applicationContext == null ? null : applicationContext.getType(target, false);
            if (implementation == null) return unavailable("UNAVAILABLE", "当前管理服务无法读取该 Bean 的类型，请在目标运行端部署并验证精确签名", target);
        } catch (ClassNotFoundException | org.springframework.beans.factory.NoSuchBeanDefinitionException error) {
            return unavailable("UNAVAILABLE", "当前管理服务未安装该实现，尚未验证目标运行端的方法签名", target);
        } catch (LinkageError error) {
            return unavailable("UNAVAILABLE", "当前管理服务无法加载该实现的依赖: " + error.getClass().getSimpleName(), target);
        }

        Map<String, FunctionMethodCatalog.MethodOption> choices = new TreeMap<>();
        try {
            for (Method method : implementation.getMethods()) {
                if (method.getDeclaringClass() == Object.class || method.isBridge() || method.isSynthetic()) continue;
                String unavailableReason = availability(implementation, method, javaClass);
                List<FunctionMethodCatalog.ParameterOption> parameters = Arrays.stream(method.getParameters())
                        .map(parameter -> new FunctionMethodCatalog.ParameterOption(parameter.isNamePresent() ? parameter.getName() : null,
                                parameter.getType().getName(), suggestedType(parameter.getType())))
                        .toList();
                choices.put(signature(method), new FunctionMethodCatalog.MethodOption(signature(method), method.getName(),
                        displaySignature(method), parameters, method.getReturnType().getName(), Modifier.isStatic(method.getModifiers()),
                        unavailableReason == null, unavailableReason));
            }
        } catch (LinkageError error) {
            return unavailable("UNAVAILABLE", "该实现的方法依赖不完整: " + error.getClass().getSimpleName(), implementation.getName());
        }
        List<FunctionMethodCatalog.MethodOption> methods = List.copyOf(choices.values());
        boolean legacy = false;
        try {
            var parameters = FunctionParameterContract.parse(function.getParamsJson());
            legacy = !parameters.isEmpty() && parameters.get(0).javaType() == null;
            Method selected = FunctionParameterContract.resolveMethod(implementation, function.getImplMethod(), function.getFuncCode(), function.getParamsJson());
            var platform = com.hengshucredit.rule.core.function.AggregateBuiltinFunctionRegistry.managementMethod(function.getFuncCode());
            if (platform != null && (!javaClass || !platform.equals(selected)))
                return new FunctionMethodCatalog("AVAILABLE", "该函数编码属于平台内置函数，请保留其原方法或使用其他函数编码", implementation.getName(), signature(selected), legacy, false, methods);
            if (com.hengshucredit.rule.core.function.ManagedFunctionInvoker.NAME.equals(function.getFuncCode()))
                return new FunctionMethodCatalog("AVAILABLE", "该函数编码由引擎内部使用，请更换函数编码", implementation.getName(), signature(selected), legacy, false, methods);
            // 候选默认隐藏 Object 的通用方法，但已有显式绑定仍按真实公开方法核对。
            String unavailableReason = availability(implementation, selected, javaClass);
            return new FunctionMethodCatalog("AVAILABLE", unavailableReason == null ? (legacy ? "当前配置沿用旧参数映射；选择具体方法可固定精确签名" : "方法签名已在当前管理服务核对") : unavailableReason,
                    implementation.getName(), signature(selected), legacy, unavailableReason == null, methods);
        } catch (IllegalArgumentException error) {
            return new FunctionMethodCatalog("AVAILABLE", error.getMessage(), implementation.getName(), null, legacy, false, methods);
        }
    }

    private FunctionMethodCatalog unavailable(String status, String message, String implementation) {
        return new FunctionMethodCatalog(status, message, implementation, null, false, false, List.of());
    }

    private String availability(Class<?> implementation, Method method, boolean javaClass) {
        if (!Modifier.isPublic(implementation.getModifiers()) || !Modifier.isPublic(method.getDeclaringClass().getModifiers()))
            return "实现类和方法声明类必须公开";
        if (!javaClass || Modifier.isStatic(method.getModifiers())) return null;
        try {
            if (Modifier.isAbstract(implementation.getModifiers()) || !Modifier.isPublic(implementation.getDeclaredConstructor().getModifiers()))
                return "实例方法需要公开无参构造方法，或通过 Spring Bean 提供实例";
        } catch (NoSuchMethodException error) { return "实例方法需要公开无参构造方法，或通过 Spring Bean 提供实例"; }
        return null;
    }

    private String signature(Method method) {
        return method.getName() + "(" + Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(",")) + ")";
    }

    private String displaySignature(Method method) {
        return method.getName() + "(" + Arrays.stream(method.getParameterTypes()).map(Class::getTypeName).collect(Collectors.joining(", ")) + ") → " + method.getReturnType().getTypeName();
    }

    private String suggestedType(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) return "BOOLEAN";
        if (type == String.class || type == char.class || type == Character.class) return "STRING";
        if (type.isPrimitive() && type != void.class || Number.class.isAssignableFrom(type)) return "NUMBER";
        if (java.util.Map.class.isAssignableFrom(type)) return "MAP";
        if (type.isArray() || java.util.Collection.class.isAssignableFrom(type)) return "LIST";
        if (java.util.Date.class.isAssignableFrom(type) || java.time.temporal.TemporalAccessor.class.isAssignableFrom(type)) return "DATE";
        return "OBJECT";
    }
}
