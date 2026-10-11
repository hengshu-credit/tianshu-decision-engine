package com.hengshucredit.rule.core.function;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 函数参数的共同协议：业务类型不等同于精确 JVM 方法签名。 */
public final class FunctionParameterContract {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private FunctionParameterContract() { }

    public record Parameter(String name, String type, String javaType) { }

    public static List<Parameter> parse(String json) {
        // 历史数据库空值是零参；JSON文本null或损坏的数组绝不能变成零参。
        if (json == null || json.isBlank()) return List.of();
        if (!JSON.isValidArray(json)) throw new IllegalArgumentException("函数参数配置必须是有效的 JSON 数组");
        JSONArray array = JSON.parseArray(json);
        List<Parameter> parameters = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Boolean explicitSignature = null;
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof JSONObject item))
                throw new IllegalArgumentException("第 " + (i + 1) + " 个函数参数必须是对象");
            if (!(item.get("name") instanceof String name) || !IDENTIFIER.matcher(name.trim()).matches())
                throw new IllegalArgumentException("第 " + (i + 1) + " 个函数参数名不是合法标识符");
            name = name.trim();
            if (!names.add(name)) throw new IllegalArgumentException("函数参数名重复: " + name);
            Object typeValue = item.get("type");
            if (typeValue != null && !(typeValue instanceof String))
                throw new IllegalArgumentException("函数参数 " + name + " 的业务类型必须是文本");
            boolean explicit = item.containsKey("javaType");
            String javaType = null;
            if (explicit) {
                if (!(item.get("javaType") instanceof String value) || value.isBlank() || !value.equals(value.trim()))
                    throw new IllegalArgumentException("函数参数 " + name + " 缺少有效的方法参数类型");
                javaType = (String) item.get("javaType");
            }
            if (explicitSignature != null && explicitSignature != explicit)
                throw new IllegalArgumentException("方法签名必须完整指定每个参数的 javaType，不能与旧类型推断混用");
            explicitSignature = explicit;
            parameters.add(new Parameter(name, (String) typeValue, javaType));
        }
        return List.copyOf(parameters);
    }

    public static List<String> names(String json) {
        return parse(json).stream().map(Parameter::name).toList();
    }

    public static Class<?>[] resolveTypes(String json, ClassLoader loader) {
        List<Parameter> parameters = parse(json);
        Class<?>[] result = new Class<?>[parameters.size()];
        for (int i = 0; i < result.length; i++) {
            Parameter parameter = parameters.get(i);
            String name = parameter.javaType() == null ? legacyJavaType(parameter.type()) : parameter.javaType();
            result[i] = resolveType(name, loader);
        }
        return result;
    }

    public static Method resolveMethod(Class<?> implementation, String method, String functionCode, String json) {
        String name = method == null || method.isBlank() ? functionCode : method.trim();
        if (name == null || name.isBlank()) throw new IllegalArgumentException("请填写实际方法名");
        Class<?>[] parameters = resolveTypes(json, implementation.getClassLoader());
        try { return implementation.getMethod(name, parameters); }
        catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("实现中不存在公开方法 " + name + "(" +
                    java.util.Arrays.stream(parameters).map(Class::getName).collect(java.util.stream.Collectors.joining(", ")) + ")", e);
        }
    }

    /** 旧制品的实际映射必须保持，不能把 DOUBLE/Object 静默改绑到 double。 */
    public static String legacyJavaType(String type) {
        if (type == null) return Object.class.getName();
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "NUMBER" -> "double";
            case "STRING" -> String.class.getName();
            case "BOOLEAN" -> "boolean";
            default -> Object.class.getName();
        };
    }

    private static Class<?> resolveType(String name, ClassLoader loader) {
        return switch (name) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "char" -> char.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            default -> {
                try { yield Class.forName(name, false, loader); }
                catch (ClassNotFoundException e) { throw new IllegalArgumentException("方法参数类型不存在: " + name, e); }
            }
        };
    }
}
