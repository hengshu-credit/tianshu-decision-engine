package com.hengshucredit.rule.core.function;

import com.alibaba.qlexpress4.Express4Runner;

/**
 * 将内置聚合函数注册到 {@link Express4Runner}，使用 addOrReplace 语义以便重复调用。
 */
public final class AggregateBuiltinFunctionRegistry {

    private static final AggregateBuiltinFunctions DELEGATE = new AggregateBuiltinFunctions();
    private static final DecisionBuiltinFunctions DECISION_DELEGATE = new DecisionBuiltinFunctions();
    private static final Class<?>[] SINGLE_OBJECT = {Object.class};
    private static final Class<?>[] TWO_OBJECTS = {Object.class, Object.class};
    private static final Class<?>[] THREE_OBJECTS = {Object.class, Object.class, Object.class};
    private static final Class<?>[] SINGLE_STRING = {String.class};
    private static final Class<?>[] TWO_STRINGS = {String.class, String.class};
    private static final Class<?>[] STRING_STRING_DOUBLE = {String.class, String.class, double.class};
    private static final Class<?>[] THREE_STRINGS = {String.class, String.class, String.class};
    private static final Class<?>[] OBJECT_STRING = {Object.class, String.class};
    private static final Class<?>[] OBJECT_STRING_OBJECT = {Object.class, String.class, Object.class};
    private static final Class<?>[] DOUBLE_DOUBLE = {double.class, double.class};
    private static final Class<?>[] DOUBLE_DOUBLE_DOUBLE = {double.class, double.class, double.class};

    private AggregateBuiltinFunctionRegistry() {
    }

    /**
     * 注册 sum、count、max、min、avg；同名已存在则覆盖。
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

        runner.addFunctionOfServiceMethod("numAdd", DECISION_DELEGATE, "numAdd", DOUBLE_DOUBLE);
        runner.addFunctionOfServiceMethod("numSub", DECISION_DELEGATE, "numSub", DOUBLE_DOUBLE);
        runner.addFunctionOfServiceMethod("numMul", DECISION_DELEGATE, "numMul", DOUBLE_DOUBLE);
        runner.addFunctionOfServiceMethod("numDiv", DECISION_DELEGATE, "numDiv", DOUBLE_DOUBLE_DOUBLE);
        runner.addFunctionOfServiceMethod("numRound", DECISION_DELEGATE, "numRound", DOUBLE_DOUBLE);
        runner.addFunctionOfServiceMethod("numAbs", DECISION_DELEGATE, "numAbs", new Class<?>[]{double.class});
        runner.addFunctionOfServiceMethod("numPow", DECISION_DELEGATE, "numPow", DOUBLE_DOUBLE);
        runner.addFunctionOfServiceMethod("numBetween", DECISION_DELEGATE, "numBetween", DOUBLE_DOUBLE_DOUBLE);

        runner.addFunctionOfServiceMethod("strLength", DECISION_DELEGATE, "strLength", SINGLE_STRING);
        runner.addFunctionOfServiceMethod("strTrim", DECISION_DELEGATE, "strTrim", SINGLE_STRING);
        runner.addFunctionOfServiceMethod("strContains", DECISION_DELEGATE, "strContains", TWO_STRINGS);
        runner.addFunctionOfServiceMethod("strReplace", DECISION_DELEGATE, "strReplace", THREE_STRINGS);
        runner.addFunctionOfServiceMethod("strRegexExtract", DECISION_DELEGATE, "strRegexExtract", STRING_STRING_DOUBLE);
        runner.addFunctionOfServiceMethod("strSplit", DECISION_DELEGATE, "strSplit", TWO_STRINGS);
        runner.addFunctionOfServiceMethod("strJoin", DECISION_DELEGATE, "strJoin", OBJECT_STRING);

        runner.addFunctionOfServiceMethod("arrSize", DECISION_DELEGATE, "arrSize", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("arrGet", DECISION_DELEGATE, "arrGet", new Class<?>[]{Object.class, double.class});
        runner.addFunctionOfServiceMethod("arrFirst", DECISION_DELEGATE, "arrFirst", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("arrLast", DECISION_DELEGATE, "arrLast", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("arrDistinct", DECISION_DELEGATE, "arrDistinct", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("arrSort", DECISION_DELEGATE, "arrSort", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("arrContains", DECISION_DELEGATE, "arrContains", TWO_OBJECTS);

        runner.addFunctionOfServiceMethod("jsonParse", DECISION_DELEGATE, "jsonParse", SINGLE_STRING);
        runner.addFunctionOfServiceMethod("jsonGet", DECISION_DELEGATE, "jsonGet", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("jsonList", DECISION_DELEGATE, "jsonList", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("jsonExists", DECISION_DELEGATE, "jsonExists", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("jsonCount", DECISION_DELEGATE, "jsonCount", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("jsonSum", DECISION_DELEGATE, "jsonSum", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("jsonAvg", DECISION_DELEGATE, "jsonAvg", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("jsonMin", DECISION_DELEGATE, "jsonMin", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("jsonMax", DECISION_DELEGATE, "jsonMax", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("objGet", DECISION_DELEGATE, "objGet", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("objGetOrDefault", DECISION_DELEGATE, "objGetOrDefault", OBJECT_STRING_OBJECT);
        runner.addFunctionOfServiceMethod("objHas", DECISION_DELEGATE, "objHas", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("objSize", DECISION_DELEGATE, "objSize", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("objKeys", DECISION_DELEGATE, "objKeys", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("objValues", DECISION_DELEGATE, "objValues", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("toJson", DECISION_DELEGATE, "toJson", SINGLE_OBJECT);
    }
}
