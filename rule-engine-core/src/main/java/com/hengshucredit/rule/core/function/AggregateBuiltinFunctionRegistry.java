package com.hengshucredit.rule.core.function;

import com.alibaba.qlexpress4.Express4Runner;

/**
 * 注册 QLExpress 的内置函数。
 */
public final class AggregateBuiltinFunctionRegistry {

    private static final AggregateBuiltinFunctions DELEGATE = new AggregateBuiltinFunctions();
    private static final IdentityStringBuiltinFunctions IDENTITY_STRING_DELEGATE = new IdentityStringBuiltinFunctions();
    private static final Class<?>[] SINGLE_OBJECT = {Object.class};
    private static final Class<?>[] TWO_OBJECTS = {Object.class, Object.class};
    private static final Class<?>[] THREE_OBJECTS = {Object.class, Object.class, Object.class};

    private AggregateBuiltinFunctionRegistry() {
    }

    /**
     * 注册聚合、空值、集合、字符串和身份证辅助函数。
     *
     * @param runner QLExpress 执行器
     */
    public static void register(Express4Runner runner) {
        if (runner == null) {
            return;
        }
        runner.addFunctionOfServiceMethod("sum", DELEGATE, "sum", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("count", DELEGATE, "count", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("max", DELEGATE, "max", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("min", DELEGATE, "min", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("avg", DELEGATE, "avg", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("isNull", DELEGATE, "isNull", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("isNotNull", DELEGATE, "isNotNull", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("isBlank", DELEGATE, "isBlank", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("isNotBlank", DELEGATE, "isNotBlank", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("containsValue", DELEGATE, "containsValue", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("containsAnyValue", DELEGATE, "containsAnyValue", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("containsAllValues", DELEGATE, "containsAllValues", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("startsWithValue", DELEGATE, "startsWithValue", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("endsWithValue", DELEGATE, "endsWithValue", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("hasKey", DELEGATE, "hasKey", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("nvl", DELEGATE, "nvl", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("roundScale", DELEGATE, "roundScale", THREE_OBJECTS);

        // 仅供 rule_function 中 SCRIPT 类型函数调用的安全桥接函数。
        runner.addFunctionOfServiceMethod("idCardGenderValue", IDENTITY_STRING_DELEGATE,
                "idCardGenderValue", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("idCardBirthDateValue", IDENTITY_STRING_DELEGATE,
                "idCardBirthDateValue", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("leftStringValue", IDENTITY_STRING_DELEGATE,
                "leftStringValue", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("rightStringValue", IDENTITY_STRING_DELEGATE,
                "rightStringValue", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("idCardAgeValue", IDENTITY_STRING_DELEGATE,
                "idCardAgeValue", THREE_OBJECTS);
        runner.addFunctionOfServiceMethod("regexMatchValue", IDENTITY_STRING_DELEGATE,
                "regexMatchValue", TWO_OBJECTS);
    }
}
