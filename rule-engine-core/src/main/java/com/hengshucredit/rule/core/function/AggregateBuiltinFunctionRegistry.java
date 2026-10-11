package com.hengshucredit.rule.core.function;

import com.alibaba.qlexpress4.Express4Runner;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注册平台内置函数；重复调用保留已有绑定，方法身份与管理端共用同一来源。
 */
public final class AggregateBuiltinFunctionRegistry {
    private static final Map<String, Method> MANAGEMENT_METHODS = new ConcurrentHashMap<>();
    private static volatile boolean managementCatalogReady;

    private static final AggregateBuiltinFunctions DELEGATE = new AggregateBuiltinFunctions();
    private static final DecisionBuiltinFunctions DECISION_DELEGATE = new DecisionBuiltinFunctions();
    private static final DigestBuiltinFunctions DIGEST_DELEGATE = new DigestBuiltinFunctions();
    private static final RuntimeContextBuiltinFunctions RUNTIME_CONTEXT_DELEGATE = new RuntimeContextBuiltinFunctions();
    private static final RandomBuiltinFunctions RANDOM_DELEGATE = new RandomBuiltinFunctions();
    private static final ImageInputFunctions IMAGE_DELEGATE = new ImageInputFunctions();
    private static final Class<?>[] NO_ARGS = {};
    private static final Class<?>[] SINGLE_OBJECT = {Object.class};
    private static final Class<?>[] TWO_OBJECTS = {Object.class, Object.class};
    private static final Class<?>[] THREE_OBJECTS = {Object.class, Object.class, Object.class};
    private static final Class<?>[] SINGLE_STRING = {String.class};
    private static final Class<?>[] TWO_STRINGS = {String.class, String.class};
    private static final Class<?>[] STRING_STRING_DOUBLE = {String.class, String.class, double.class};
    private static final Class<?>[] STRING_DOUBLE = {String.class, double.class};
    private static final Class<?>[] STRING_DOUBLE_DOUBLE = {String.class, double.class, double.class};
    private static final Class<?>[] THREE_STRINGS = {String.class, String.class, String.class};
    private static final Class<?>[] FOUR_STRINGS = {String.class, String.class, String.class, String.class};
    private static final Class<?>[] OBJECT_STRING = {Object.class, String.class};
    private static final Class<?>[] OBJECT_STRING_OBJECT = {Object.class, String.class, Object.class};
    private static final Class<?>[] OBJECT_STRING_STRING = {Object.class, String.class, String.class};
    private static final Class<?>[] OBJECT_DOUBLE = {Object.class, double.class};
    private static final Class<?>[] OBJECT_SIX_DOUBLES = {Object.class, double.class, double.class, double.class,
            double.class, double.class, double.class};
    private static final Class<?>[] OBJECT_DOUBLE_STRING = {Object.class, double.class, String.class};
    private static final Class<?>[] OBJECT_OBJECT_STRING = {Object.class, Object.class, String.class};
    private static final Class<?>[] STRING_OBJECT = {String.class, Object.class};
    private static final Class<?>[] TWO_STRINGS_OBJECT = {String.class, String.class, Object.class};
    private static final Class<?>[] STRING_OBJECT_STRING = {String.class, Object.class, String.class};
    private static final Class<?>[] STRING_STRING_STRING = {String.class, String.class, String.class};
    private static final Class<?>[] DOUBLE_STRING = {double.class, String.class};
    private static final Class<?>[] DOUBLE_DOUBLE = {double.class, double.class};
    private static final Class<?>[] DOUBLE_DOUBLE_DOUBLE = {double.class, double.class, double.class};
    private static final Class<?>[] DOUBLE_DOUBLE_DOUBLE_STRING = {double.class, double.class, double.class, String.class};
    private static final Class<?>[] DOUBLE_DOUBLE_DOUBLE_DOUBLE_STRING = {double.class, double.class, double.class, double.class, String.class};
    private static final Class<?>[] OBJECT_VARARGS = {Object[].class};

    private AggregateBuiltinFunctionRegistry() {
    }

    /**
     * 注册平台函数；同名已存在时由 QLExpress 保留原绑定。
     *
     * @param runner QLExpress 执行器
     */
    public static void register(Express4Runner runner) {
        register(runner, true);
    }

    public static void register(Express4Runner runner, boolean includeNetworkFunctions) {
        if (runner == null) {
            return;
        }
        registerConfiguredMethods(runner, includeNetworkFunctions);
        if (includeNetworkFunctions) managementCatalogReady = true;
        ManagedFunctionInvoker.register(runner);
        ResourceVersionFunctions.register(runner);
    }

    private static void registerConfiguredMethods(Express4Runner runner, boolean includeNetworkFunctions) {
        registerMethod(runner, "sum", DELEGATE, "sum", SINGLE_OBJECT);
        registerMethod(runner, "count", DELEGATE, "count", SINGLE_OBJECT);
        registerMethod(runner, "max", DELEGATE, "maxValues", OBJECT_VARARGS);
        registerMethod(runner, "min", DELEGATE, "minValues", OBJECT_VARARGS);
        registerMethod(runner, "avg", DELEGATE, "avg", SINGLE_OBJECT);
        registerMethod(runner, "isNull", DELEGATE, "isNull", SINGLE_OBJECT);
        registerMethod(runner, "isNotNull", DELEGATE, "isNotNull", SINGLE_OBJECT);
        registerMethod(runner, "isBlank", DELEGATE, "isBlank", SINGLE_OBJECT);
        registerMethod(runner, "isNotBlank", DELEGATE, "isNotBlank", SINGLE_OBJECT);
        registerMethod(runner, "containsValue", DELEGATE, "containsValue", TWO_OBJECTS);
        registerMethod(runner, "containsAnyValue", DELEGATE, "containsAnyValue", TWO_OBJECTS);
        registerMethod(runner, "containsAllValues", DELEGATE, "containsAllValues", TWO_OBJECTS);
        registerMethod(runner, "startsWithValue", DELEGATE, "startsWithValue", TWO_OBJECTS);
        registerMethod(runner, "endsWithValue", DELEGATE, "endsWithValue", TWO_OBJECTS);
        registerMethod(runner, "hasKey", DELEGATE, "hasKey", TWO_OBJECTS);
        registerMethod(runner, "hasMapValue", DELEGATE, "hasMapValue", TWO_OBJECTS);
        registerMethod(runner, "regexMatchValue", DELEGATE, "regexMatchValue", TWO_OBJECTS);
        registerMethod(runner, "containsElementValue", DELEGATE, "containsElementValue", TWO_OBJECTS);
        registerMethod(runner, "elementStartsWithValue", DELEGATE, "elementStartsWithValue", TWO_OBJECTS);
        registerMethod(runner, "elementEndsWithValue", DELEGATE, "elementEndsWithValue", TWO_OBJECTS);
        registerMethod(runner, "sizeOfValue", DELEGATE, "sizeOfValue", SINGLE_OBJECT);
        registerMethod(runner, "nvl", DELEGATE, "nvl", TWO_OBJECTS);
        registerMethod(runner, "roundScale", DELEGATE, "roundScale", THREE_OBJECTS);

        registerMethod(runner, "numAdd", DECISION_DELEGATE, "numAdd", DOUBLE_DOUBLE);
        registerMethod(runner, "numSub", DECISION_DELEGATE, "numSub", DOUBLE_DOUBLE);
        registerMethod(runner, "numMul", DECISION_DELEGATE, "numMul", DOUBLE_DOUBLE);
        registerMethod(runner, "numDiv", DECISION_DELEGATE, "numDiv", DOUBLE_DOUBLE_DOUBLE);
        registerMethod(runner, "numRound", DECISION_DELEGATE, "numRound", DOUBLE_DOUBLE);
        registerMethod(runner, "numAbs", DECISION_DELEGATE, "numAbs", new Class<?>[]{double.class});
        registerMethod(runner, "numPow", DECISION_DELEGATE, "numPow", DOUBLE_DOUBLE);
        registerMethod(runner, "numBetween", DECISION_DELEGATE, "numBetween", DOUBLE_DOUBLE_DOUBLE);
        registerMethod(runner, "numMax", DECISION_DELEGATE, "numMax", DOUBLE_DOUBLE);
        registerMethod(runner, "numMin", DECISION_DELEGATE, "numMin", DOUBLE_DOUBLE);
        registerMethod(runner, "numSin", DECISION_DELEGATE, "numSin", new Class<?>[]{double.class});
        registerMethod(runner, "numCos", DECISION_DELEGATE, "numCos", new Class<?>[]{double.class});
        registerMethod(runner, "numTan", DECISION_DELEGATE, "numTan", new Class<?>[]{double.class});
        registerMethod(runner, "numCot", DECISION_DELEGATE, "numCot", new Class<?>[]{double.class});
        registerMethod(runner, "numLn", DECISION_DELEGATE, "numLn", new Class<?>[]{double.class});
        registerMethod(runner, "numLog10", DECISION_DELEGATE, "numLog10", new Class<?>[]{double.class});
        registerMethod(runner, "numCeil", DECISION_DELEGATE, "numCeil", new Class<?>[]{double.class});
        registerMethod(runner, "numFloor", DECISION_DELEGATE, "numFloor", new Class<?>[]{double.class});
        registerMethod(runner, "cosineSimilarity", DECISION_DELEGATE, "cosineSimilarity", TWO_OBJECTS);
        registerMethod(runner, "facenoxLiveness", DECISION_DELEGATE, "facenoxLiveness", OBJECT_DOUBLE);
        registerMethod(runner, "facenoxLivenessList", DECISION_DELEGATE, "facenoxLivenessList", OBJECT_DOUBLE);
        if (includeNetworkFunctions) registerMethod(runner, "imageToBase64", IMAGE_DELEGATE, "imageToBase64", STRING_DOUBLE);
        registerMethod(runner, "numRoundInteger", DECISION_DELEGATE, "numRoundInteger", new Class<?>[]{double.class});
        registerMethod(runner, "randomInt", RANDOM_DELEGATE, "randomInt", OBJECT_VARARGS);
        registerMethod(runner, "randomDecimal", RANDOM_DELEGATE, "randomDecimal", OBJECT_VARARGS);

        registerMethod(runner, "strLength", DECISION_DELEGATE, "strLength", SINGLE_STRING);
        registerMethod(runner, "strTrim", DECISION_DELEGATE, "strTrim", SINGLE_STRING);
        registerMethod(runner, "strContains", DECISION_DELEGATE, "strContains", TWO_STRINGS);
        registerMethod(runner, "strReplace", DECISION_DELEGATE, "strReplace", THREE_STRINGS);
        registerMethod(runner, "strRegexExtract", DECISION_DELEGATE, "strRegexExtract", STRING_STRING_DOUBLE);
        registerMethod(runner, "strSplit", DECISION_DELEGATE, "strSplit", TWO_STRINGS);
        registerMethod(runner, "strJoin", DECISION_DELEGATE, "strJoin", OBJECT_STRING);
        registerMethod(runner, "strSubstring", DECISION_DELEGATE, "strSubstring", STRING_DOUBLE_DOUBLE);
        registerMethod(runner, "strSubstringFrom", DECISION_DELEGATE, "strSubstringFrom", STRING_DOUBLE);
        registerMethod(runner, "strSubstringTo", DECISION_DELEGATE, "strSubstringTo", STRING_DOUBLE);
        registerMethod(runner, "strLower", DECISION_DELEGATE, "strLower", SINGLE_STRING);
        registerMethod(runner, "strUpper", DECISION_DELEGATE, "strUpper", SINGLE_STRING);
        registerMethod(runner, "strCharAt", DECISION_DELEGATE, "strCharAt", STRING_DOUBLE);
        registerMethod(runner, "strIndexOf", DECISION_DELEGATE, "strIndexOf", TWO_STRINGS);
        registerMethod(runner, "strLastIndexOf", DECISION_DELEGATE, "strLastIndexOf", TWO_STRINGS);
        registerMethod(runner, "strReplaceLiteral", DECISION_DELEGATE, "strReplaceLiteral", THREE_STRINGS);
        registerMethod(runner, "md5", DIGEST_DELEGATE, "md5", SINGLE_STRING);
        registerMethod(runner, "sha1", DIGEST_DELEGATE, "sha1", SINGLE_STRING);
        registerMethod(runner, "sha256", DIGEST_DELEGATE, "sha256", SINGLE_STRING);
        registerMethod(runner, "hmacSha256", DIGEST_DELEGATE, "hmacSha256", TWO_STRINGS);

        registerMethod(runner, "arrSize", DECISION_DELEGATE, "arrSize", SINGLE_OBJECT);
        registerMethod(runner, "arrGet", DECISION_DELEGATE, "arrGet", new Class<?>[]{Object.class, double.class});
        registerMethod(runner, "arrFirst", DECISION_DELEGATE, "arrFirst", SINGLE_OBJECT);
        registerMethod(runner, "arrLast", DECISION_DELEGATE, "arrLast", SINGLE_OBJECT);
        registerMethod(runner, "arrDistinct", DECISION_DELEGATE, "arrDistinct", SINGLE_OBJECT);
        registerMethod(runner, "arrSort", DECISION_DELEGATE, "arrSort", OBJECT_STRING);
        registerMethod(runner, "arrContains", DECISION_DELEGATE, "arrContains", TWO_OBJECTS);
        registerMethod(runner, "arrMax", DECISION_DELEGATE, "arrMax", SINGLE_OBJECT);
        registerMethod(runner, "arrMin", DECISION_DELEGATE, "arrMin", SINGLE_OBJECT);
        registerMethod(runner, "arrAdd", DECISION_DELEGATE, "arrAdd", TWO_OBJECTS);
        registerMethod(runner, "arrRemove", DECISION_DELEGATE, "arrRemove", TWO_OBJECTS);
        registerMethod(runner, "arrSortBy", DECISION_DELEGATE, "arrSortBy", OBJECT_STRING_STRING);
        registerMethod(runner, "arrPluck", DECISION_DELEGATE, "arrPluck", OBJECT_STRING);
        registerMethod(runner, "arrIsEmpty", DECISION_DELEGATE, "arrIsEmpty", SINGLE_OBJECT);

        registerMethod(runner, "jsonParse", DECISION_DELEGATE, "jsonParse", SINGLE_STRING);
        registerMethod(runner, "jsonGet", DECISION_DELEGATE, "jsonGet", OBJECT_STRING);
        registerMethod(runner, "jsonList", DECISION_DELEGATE, "jsonList", OBJECT_STRING);
        registerMethod(runner, "jsonExists", DECISION_DELEGATE, "jsonExists", OBJECT_STRING);
        registerMethod(runner, "jsonCount", DECISION_DELEGATE, "jsonCount", OBJECT_STRING);
        registerMethod(runner, "jsonSum", DECISION_DELEGATE, "jsonSum", OBJECT_STRING);
        registerMethod(runner, "jsonAvg", DECISION_DELEGATE, "jsonAvg", OBJECT_STRING);
        registerMethod(runner, "jsonMin", DECISION_DELEGATE, "jsonMin", OBJECT_STRING);
        registerMethod(runner, "jsonMax", DECISION_DELEGATE, "jsonMax", OBJECT_STRING);
        registerMethod(runner, "objGet", DECISION_DELEGATE, "objGet", OBJECT_STRING);
        registerMethod(runner, "objGetOrDefault", DECISION_DELEGATE, "objGetOrDefault", OBJECT_STRING_OBJECT);
        registerMethod(runner, "objHas", DECISION_DELEGATE, "objHas", OBJECT_STRING);
        registerMethod(runner, "objSize", DECISION_DELEGATE, "objSize", SINGLE_OBJECT);
        registerMethod(runner, "objKeys", DECISION_DELEGATE, "objKeys", SINGLE_OBJECT);
        registerMethod(runner, "objValues", DECISION_DELEGATE, "objValues", SINGLE_OBJECT);
        registerMethod(runner, "toJson", DECISION_DELEGATE, "toJson", SINGLE_OBJECT);
        registerMethod(runner, "mapPut", DECISION_DELEGATE, "mapPut", OBJECT_STRING_OBJECT);
        registerMethod(runner, "mapRemove", DECISION_DELEGATE, "mapRemove", OBJECT_STRING);
        registerMethod(runner, "mapHasKey", DECISION_DELEGATE, "mapHasKey", OBJECT_STRING);
        registerMethod(runner, "mapGet", DECISION_DELEGATE, "mapGet", OBJECT_STRING);
        registerMethod(runner, "mapSize", DECISION_DELEGATE, "mapSize", SINGLE_OBJECT);
        registerMethod(runner, "mapKeys", DECISION_DELEGATE, "mapKeys", SINGLE_OBJECT);
        registerMethod(runner, "mapValues", DECISION_DELEGATE, "mapValues", SINGLE_OBJECT);
        registerMethod(runner, "newMap", DECISION_DELEGATE, "newMap", NO_ARGS);
        registerMethod(runner, "newList", DECISION_DELEGATE, "newList", NO_ARGS);
        registerMethod(runner, "newLike", DECISION_DELEGATE, "newLike", SINGLE_OBJECT);
        registerMethod(runner, "toStringValue", DECISION_DELEGATE, "toStringValue", SINGLE_OBJECT);
        registerMethod(runner, "toNumberValue", DECISION_DELEGATE, "toNumberValue", SINGLE_OBJECT);
        registerMethod(runner, "toBooleanValue", DECISION_DELEGATE, "toBooleanValue", SINGLE_OBJECT);
        registerMethod(runner, "toListValue", DECISION_DELEGATE, "toListValue", SINGLE_OBJECT);
        registerMethod(runner, "toMapValue", DECISION_DELEGATE, "toMapValue", SINGLE_OBJECT);

        registerMethod(runner, "currentDate", DECISION_DELEGATE, "currentDate", NO_ARGS);
        registerMethod(runner, "currentDateTime", DECISION_DELEGATE, "currentDateTime", NO_ARGS);
        registerMethod(runner, "dateFormat", DECISION_DELEGATE, "dateFormat", OBJECT_STRING);
        registerMethod(runner, "dateConvert", DECISION_DELEGATE, "dateConvert", STRING_STRING_STRING);
        registerMethod(runner, "dateAdd", DECISION_DELEGATE, "dateAdd", OBJECT_DOUBLE_STRING);
        registerMethod(runner, "dateSub", DECISION_DELEGATE, "dateSub", OBJECT_DOUBLE_STRING);
        registerMethod(runner, "dateAddParts", DECISION_DELEGATE, "dateAddParts", OBJECT_SIX_DOUBLES);
        registerMethod(runner, "dateSubParts", DECISION_DELEGATE, "dateSubParts", OBJECT_SIX_DOUBLES);
        registerMethod(runner, "dateDiff", DECISION_DELEGATE, "dateDiff", OBJECT_OBJECT_STRING);
        registerMethod(runner, "dateAddYears", DECISION_DELEGATE, "dateAddYears", OBJECT_DOUBLE);
        registerMethod(runner, "dateAddMonths", DECISION_DELEGATE, "dateAddMonths", OBJECT_DOUBLE);
        registerMethod(runner, "dateAddDays", DECISION_DELEGATE, "dateAddDays", OBJECT_DOUBLE);
        registerMethod(runner, "dateAddHours", DECISION_DELEGATE, "dateAddHours", OBJECT_DOUBLE);
        registerMethod(runner, "dateAddMinutes", DECISION_DELEGATE, "dateAddMinutes", OBJECT_DOUBLE);
        registerMethod(runner, "dateAddSeconds", DECISION_DELEGATE, "dateAddSeconds", OBJECT_DOUBLE);
        registerMethod(runner, "dateSubYears", DECISION_DELEGATE, "dateSubYears", OBJECT_DOUBLE);
        registerMethod(runner, "dateSubMonths", DECISION_DELEGATE, "dateSubMonths", OBJECT_DOUBLE);
        registerMethod(runner, "dateSubDays", DECISION_DELEGATE, "dateSubDays", OBJECT_DOUBLE);
        registerMethod(runner, "dateSubHours", DECISION_DELEGATE, "dateSubHours", OBJECT_DOUBLE);
        registerMethod(runner, "dateSubMinutes", DECISION_DELEGATE, "dateSubMinutes", OBJECT_DOUBLE);
        registerMethod(runner, "dateSubSeconds", DECISION_DELEGATE, "dateSubSeconds", OBJECT_DOUBLE);
        registerMethod(runner, "dateYear", DECISION_DELEGATE, "dateYear", SINGLE_OBJECT);
        registerMethod(runner, "dateMonth", DECISION_DELEGATE, "dateMonth", SINGLE_OBJECT);
        registerMethod(runner, "dateWeekday", DECISION_DELEGATE, "dateWeekday", SINGLE_OBJECT);
        registerMethod(runner, "dateDay", DECISION_DELEGATE, "dateDay", SINGLE_OBJECT);
        registerMethod(runner, "dateHour", DECISION_DELEGATE, "dateHour", SINGLE_OBJECT);
        registerMethod(runner, "dateMinute", DECISION_DELEGATE, "dateMinute", SINGLE_OBJECT);
        registerMethod(runner, "dateSecond", DECISION_DELEGATE, "dateSecond", SINGLE_OBJECT);
        registerMethod(runner, "dateDiffMillis", DECISION_DELEGATE, "dateDiffMillis", TWO_OBJECTS);
        registerMethod(runner, "dateDiffSeconds", DECISION_DELEGATE, "dateDiffSeconds", TWO_OBJECTS);
        registerMethod(runner, "dateDiffMinutes", DECISION_DELEGATE, "dateDiffMinutes", TWO_OBJECTS);
        registerMethod(runner, "dateDiffHours", DECISION_DELEGATE, "dateDiffHours", TWO_OBJECTS);
        registerMethod(runner, "dateDiffDays", DECISION_DELEGATE, "dateDiffDays", TWO_OBJECTS);
        registerMethod(runner, "dateDiffWeeks", DECISION_DELEGATE, "dateDiffWeeks", TWO_OBJECTS);
        registerMethod(runner, "dateDaysInMonths", DECISION_DELEGATE, "dateDaysInMonths", SINGLE_OBJECT);
        registerMethod(runner, "dateDaysOutsideMonths", DECISION_DELEGATE, "dateDaysOutsideMonths", SINGLE_OBJECT);
        registerMethod(runner, "dateDaysInSpecifiedMonths", DECISION_DELEGATE, "dateDaysInSpecifiedMonths", OBJECT_OBJECT_STRING);
        registerMethod(runner, "dateDaysOutsideSpecifiedMonths", DECISION_DELEGATE, "dateDaysOutsideSpecifiedMonths", OBJECT_OBJECT_STRING);
        registerMethod(runner, "dateToMillis", DECISION_DELEGATE, "dateToMillis", SINGLE_OBJECT);
        registerMethod(runner, "millisToDate", DECISION_DELEGATE, "millisToDate", DOUBLE_STRING);
        registerMethod(runner, "idCardBirthDate", DECISION_DELEGATE, "idCardBirthDate", SINGLE_STRING);
        registerMethod(runner, "idCardAge", DECISION_DELEGATE, "idCardAge", STRING_OBJECT_STRING);

        registerMethod(runner, "scoreByOdds", DECISION_DELEGATE, "scoreByOdds", DOUBLE_DOUBLE_DOUBLE_STRING);
        registerMethod(runner, "scoreByProbability", DECISION_DELEGATE, "scoreByProbability", DOUBLE_DOUBLE_DOUBLE_STRING);
        registerMethod(runner, "scoreByOddsPdo", DECISION_DELEGATE, "scoreByOddsPdo", DOUBLE_DOUBLE_DOUBLE_DOUBLE_STRING);
        registerMethod(runner, "scoreByBadRatePdo", DECISION_DELEGATE, "scoreByBadRatePdo", DOUBLE_DOUBLE_DOUBLE_DOUBLE_STRING);
        registerMethod(runner, "setRuntimeValue", RUNTIME_CONTEXT_DELEGATE, "setRuntimeValue", STRING_OBJECT);
        registerMethod(runner, "currentRule", RUNTIME_CONTEXT_DELEGATE, "currentRule", NO_ARGS);
        registerMethod(runner, "currentRuleName", RUNTIME_CONTEXT_DELEGATE, "currentRuleName", NO_ARGS);
        registerMethod(runner, "currentMatchedConditions", RUNTIME_CONTEXT_DELEGATE, "currentMatchedConditions", NO_ARGS);
        registerMethod(runner, "sourceStatus", RUNTIME_CONTEXT_DELEGATE, "sourceStatus", FOUR_STRINGS);
        registerMethod(runner, "sourceStatusValue", RUNTIME_CONTEXT_DELEGATE, "sourceStatusValue", THREE_STRINGS);
        registerMethod(runner, "externalApiValue", RUNTIME_CONTEXT_DELEGATE, "externalApiValue", OBJECT_STRING);
        registerMethod(runner, "writeRuleList", RUNTIME_CONTEXT_DELEGATE, "writeRuleList", new Class<?>[]{String.class, Object.class});
        registerMethod(runner, "recordRuleSetItem", RUNTIME_CONTEXT_DELEGATE, "recordRuleSetItem", TWO_STRINGS_OBJECT);
        registerMethod(runner, "recordRuleSetSummary", RUNTIME_CONTEXT_DELEGATE, "recordRuleSetSummary", SINGLE_OBJECT);
    }

    private static void registerMethod(Express4Runner runner, String code, Object delegate, String methodName, Class<?>[] types) {
        try {
            Method actual = delegate.getClass().getMethod(methodName, types);
            Method managed = actual;
            // 平台入口允许额外调用形式；管理函数的 ID 引用仍精确绑定其声明方法。
            if (("max".equals(code) || "min".equals(code)) && delegate == DELEGATE) {
                managed = delegate.getClass().getMethod(code, Object.class);
            } else if (("randomInt".equals(code) || "randomDecimal".equals(code)) && delegate == RANDOM_DELEGATE) {
                managed = delegate.getClass().getMethod(code + "ForManagement", double.class, double.class, boolean.class, boolean.class);
            }
            MANAGEMENT_METHODS.putIfAbsent(code, managed);
            if (runner != null) runner.addFunction(code, new com.alibaba.qlexpress4.runtime.function.QMethodFunction(delegate, actual));
        } catch (NoSuchMethodException error) { throw new IllegalStateException("平台函数方法不存在: " + code, error); }
    }

    public static Method managementMethod(String code) {
        if (!managementCatalogReady) {
            synchronized (AggregateBuiltinFunctionRegistry.class) {
                if (!managementCatalogReady) { registerConfiguredMethods(null, true); managementCatalogReady = true; }
            }
        }
        return code == null ? null : MANAGEMENT_METHODS.get(code);
    }

    public static boolean matchesManagementMethod(String code, String className, String methodName, String paramsJson) {
        Method expected = managementMethod(code);
        if (expected == null || !expected.getDeclaringClass().getName().equals(className)) return false;
        return expected.equals(FunctionParameterContract.resolveMethod(expected.getDeclaringClass(), methodName, code, paramsJson));
    }
}
