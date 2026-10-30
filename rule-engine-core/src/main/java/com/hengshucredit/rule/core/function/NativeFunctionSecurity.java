package com.hengshucredit.rule.core.function;

import java.lang.reflect.Method;

/** 原生方法绕过 QLExpress 成员白名单，必须在绑定前单独授权。 */
public final class NativeFunctionSecurity {
    private NativeFunctionSecurity() { }

    public static boolean isAllowed(Method method, String functionCode) {
        return method.isAnnotationPresent(ScriptCallable.class)
                || method.equals(AggregateBuiltinFunctionRegistry.managementMethod(functionCode));
    }

    public static void requireAllowed(Method method, String functionCode) {
        if (!isAllowed(method, functionCode)) throw new IllegalArgumentException(
                "原生函数方法未授权脚本调用，请由部署代码使用 @ScriptCallable 显式声明");
    }
}
