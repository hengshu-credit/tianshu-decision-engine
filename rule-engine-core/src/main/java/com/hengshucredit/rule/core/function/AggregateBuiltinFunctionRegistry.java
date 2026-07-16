package com.hengshucredit.rule.core.function;

import com.alibaba.qlexpress4.Express4Runner;

/**
 * 将内置聚合函数注册到 {@link Express4Runner}，使用 addOrReplace 语义以便重复调用。
 */
public final class AggregateBuiltinFunctionRegistry {

    private static final AggregateBuiltinFunctions DELEGATE = new AggregateBuiltinFunctions();
    private static final DecisionBuiltinFunctions DECISION_DELEGATE = new DecisionBuiltinFunctions();
    private static final DigestBuiltinFunctions DIGEST_DELEGATE = new DigestBuiltinFunctions();
    private static final RuntimeContextBuiltinFunctions RUNTIME_CONTEXT_DELEGATE = new RuntimeContextBuiltinFunctions();
    private static final RandomBuiltinFunctions RANDOM_DELEGATE = new RandomBuiltinFunctions();
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
    private static final Class<?>[] OBJECT_STRING = {Object.class, String.class};
    private static final Class<?>[] OBJECT_STRING_OBJECT = {Object.class, String.class, Object.class};
    private static final Class<?>[] OBJECT_STRING_STRING = {Object.class, String.class, String.class};
    private static final Class<?>[] OBJECT_DOUBLE = {Object.class, double.class};
    private static final Class<?>[] OBJECT_SIX_DOUBLES = {Object.class, double.class, double.class, double.class,
            double.class, double.class, double.class};
    private static final Class<?>[] OBJECT_DOUBLE_STRING = {Object.class, double.class, String.class};
    private static final Class<?>[] OBJECT_OBJECT_STRING = {Object.class, Object.class, String.class};
    private static final Class<?>[] STRING_OBJECT = {String.class, Object.class};
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
        runner.addFunctionOfServiceMethod("hasMapValue", DELEGATE, "hasMapValue", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("regexMatchValue", DELEGATE, "regexMatchValue", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("containsElementValue", DELEGATE, "containsElementValue", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("elementStartsWithValue", DELEGATE, "elementStartsWithValue", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("elementEndsWithValue", DELEGATE, "elementEndsWithValue", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("sizeOfValue", DELEGATE, "sizeOfValue", SINGLE_OBJECT);
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
        runner.addFunctionOfServiceMethod("numMax", DECISION_DELEGATE, "numMax", DOUBLE_DOUBLE);
        runner.addFunctionOfServiceMethod("numMin", DECISION_DELEGATE, "numMin", DOUBLE_DOUBLE);
        runner.addFunctionOfServiceMethod("numSin", DECISION_DELEGATE, "numSin", new Class<?>[]{double.class});
        runner.addFunctionOfServiceMethod("numCos", DECISION_DELEGATE, "numCos", new Class<?>[]{double.class});
        runner.addFunctionOfServiceMethod("numTan", DECISION_DELEGATE, "numTan", new Class<?>[]{double.class});
        runner.addFunctionOfServiceMethod("numCot", DECISION_DELEGATE, "numCot", new Class<?>[]{double.class});
        runner.addFunctionOfServiceMethod("numLn", DECISION_DELEGATE, "numLn", new Class<?>[]{double.class});
        runner.addFunctionOfServiceMethod("numLog10", DECISION_DELEGATE, "numLog10", new Class<?>[]{double.class});
        runner.addFunctionOfServiceMethod("numCeil", DECISION_DELEGATE, "numCeil", new Class<?>[]{double.class});
        runner.addFunctionOfServiceMethod("numFloor", DECISION_DELEGATE, "numFloor", new Class<?>[]{double.class});
        runner.addFunctionOfServiceMethod("numRoundInteger", DECISION_DELEGATE, "numRoundInteger", new Class<?>[]{double.class});
        runner.addFunctionOfServiceMethod("randomInt", RANDOM_DELEGATE, "randomInt", OBJECT_VARARGS);
        runner.addFunctionOfServiceMethod("randomDecimal", RANDOM_DELEGATE, "randomDecimal", OBJECT_VARARGS);

        runner.addFunctionOfServiceMethod("strLength", DECISION_DELEGATE, "strLength", SINGLE_STRING);
        runner.addFunctionOfServiceMethod("strTrim", DECISION_DELEGATE, "strTrim", SINGLE_STRING);
        runner.addFunctionOfServiceMethod("strContains", DECISION_DELEGATE, "strContains", TWO_STRINGS);
        runner.addFunctionOfServiceMethod("strReplace", DECISION_DELEGATE, "strReplace", THREE_STRINGS);
        runner.addFunctionOfServiceMethod("strRegexExtract", DECISION_DELEGATE, "strRegexExtract", STRING_STRING_DOUBLE);
        runner.addFunctionOfServiceMethod("strSplit", DECISION_DELEGATE, "strSplit", TWO_STRINGS);
        runner.addFunctionOfServiceMethod("strJoin", DECISION_DELEGATE, "strJoin", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("strSubstring", DECISION_DELEGATE, "strSubstring", STRING_DOUBLE_DOUBLE);
        runner.addFunctionOfServiceMethod("strSubstringFrom", DECISION_DELEGATE, "strSubstringFrom", STRING_DOUBLE);
        runner.addFunctionOfServiceMethod("strSubstringTo", DECISION_DELEGATE, "strSubstringTo", STRING_DOUBLE);
        runner.addFunctionOfServiceMethod("strLower", DECISION_DELEGATE, "strLower", SINGLE_STRING);
        runner.addFunctionOfServiceMethod("strUpper", DECISION_DELEGATE, "strUpper", SINGLE_STRING);
        runner.addFunctionOfServiceMethod("strCharAt", DECISION_DELEGATE, "strCharAt", STRING_DOUBLE);
        runner.addFunctionOfServiceMethod("strIndexOf", DECISION_DELEGATE, "strIndexOf", TWO_STRINGS);
        runner.addFunctionOfServiceMethod("strLastIndexOf", DECISION_DELEGATE, "strLastIndexOf", TWO_STRINGS);
        runner.addFunctionOfServiceMethod("strReplaceLiteral", DECISION_DELEGATE, "strReplaceLiteral", THREE_STRINGS);
        runner.addFunctionOfServiceMethod("md5", DIGEST_DELEGATE, "md5", SINGLE_STRING);
        runner.addFunctionOfServiceMethod("sha1", DIGEST_DELEGATE, "sha1", SINGLE_STRING);
        runner.addFunctionOfServiceMethod("sha256", DIGEST_DELEGATE, "sha256", SINGLE_STRING);
        runner.addFunctionOfServiceMethod("hmacSha256", DIGEST_DELEGATE, "hmacSha256", TWO_STRINGS);

        runner.addFunctionOfServiceMethod("arrSize", DECISION_DELEGATE, "arrSize", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("arrGet", DECISION_DELEGATE, "arrGet", new Class<?>[]{Object.class, double.class});
        runner.addFunctionOfServiceMethod("arrFirst", DECISION_DELEGATE, "arrFirst", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("arrLast", DECISION_DELEGATE, "arrLast", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("arrDistinct", DECISION_DELEGATE, "arrDistinct", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("arrSort", DECISION_DELEGATE, "arrSort", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("arrContains", DECISION_DELEGATE, "arrContains", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("arrMax", DECISION_DELEGATE, "arrMax", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("arrMin", DECISION_DELEGATE, "arrMin", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("arrAdd", DECISION_DELEGATE, "arrAdd", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("arrRemove", DECISION_DELEGATE, "arrRemove", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("arrSortBy", DECISION_DELEGATE, "arrSortBy", OBJECT_STRING_STRING);
        runner.addFunctionOfServiceMethod("arrPluck", DECISION_DELEGATE, "arrPluck", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("arrIsEmpty", DECISION_DELEGATE, "arrIsEmpty", SINGLE_OBJECT);

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
        runner.addFunctionOfServiceMethod("mapPut", DECISION_DELEGATE, "mapPut", OBJECT_STRING_OBJECT);
        runner.addFunctionOfServiceMethod("mapRemove", DECISION_DELEGATE, "mapRemove", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("mapHasKey", DECISION_DELEGATE, "mapHasKey", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("mapGet", DECISION_DELEGATE, "mapGet", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("mapSize", DECISION_DELEGATE, "mapSize", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("mapKeys", DECISION_DELEGATE, "mapKeys", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("mapValues", DECISION_DELEGATE, "mapValues", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("newMap", DECISION_DELEGATE, "newMap", NO_ARGS);
        runner.addFunctionOfServiceMethod("newList", DECISION_DELEGATE, "newList", NO_ARGS);
        runner.addFunctionOfServiceMethod("newLike", DECISION_DELEGATE, "newLike", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("toStringValue", DECISION_DELEGATE, "toStringValue", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("toNumberValue", DECISION_DELEGATE, "toNumberValue", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("toBooleanValue", DECISION_DELEGATE, "toBooleanValue", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("toListValue", DECISION_DELEGATE, "toListValue", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("toMapValue", DECISION_DELEGATE, "toMapValue", SINGLE_OBJECT);

        runner.addFunctionOfServiceMethod("currentDate", DECISION_DELEGATE, "currentDate", NO_ARGS);
        runner.addFunctionOfServiceMethod("currentDateTime", DECISION_DELEGATE, "currentDateTime", NO_ARGS);
        runner.addFunctionOfServiceMethod("dateFormat", DECISION_DELEGATE, "dateFormat", OBJECT_STRING);
        runner.addFunctionOfServiceMethod("dateConvert", DECISION_DELEGATE, "dateConvert", STRING_STRING_STRING);
        runner.addFunctionOfServiceMethod("dateAdd", DECISION_DELEGATE, "dateAdd", OBJECT_DOUBLE_STRING);
        runner.addFunctionOfServiceMethod("dateSub", DECISION_DELEGATE, "dateSub", OBJECT_DOUBLE_STRING);
        runner.addFunctionOfServiceMethod("dateAddParts", DECISION_DELEGATE, "dateAddParts", OBJECT_SIX_DOUBLES);
        runner.addFunctionOfServiceMethod("dateSubParts", DECISION_DELEGATE, "dateSubParts", OBJECT_SIX_DOUBLES);
        runner.addFunctionOfServiceMethod("dateDiff", DECISION_DELEGATE, "dateDiff", OBJECT_OBJECT_STRING);
        runner.addFunctionOfServiceMethod("dateAddYears", DECISION_DELEGATE, "dateAddYears", OBJECT_DOUBLE);
        runner.addFunctionOfServiceMethod("dateAddMonths", DECISION_DELEGATE, "dateAddMonths", OBJECT_DOUBLE);
        runner.addFunctionOfServiceMethod("dateAddDays", DECISION_DELEGATE, "dateAddDays", OBJECT_DOUBLE);
        runner.addFunctionOfServiceMethod("dateAddHours", DECISION_DELEGATE, "dateAddHours", OBJECT_DOUBLE);
        runner.addFunctionOfServiceMethod("dateAddMinutes", DECISION_DELEGATE, "dateAddMinutes", OBJECT_DOUBLE);
        runner.addFunctionOfServiceMethod("dateAddSeconds", DECISION_DELEGATE, "dateAddSeconds", OBJECT_DOUBLE);
        runner.addFunctionOfServiceMethod("dateSubYears", DECISION_DELEGATE, "dateSubYears", OBJECT_DOUBLE);
        runner.addFunctionOfServiceMethod("dateSubMonths", DECISION_DELEGATE, "dateSubMonths", OBJECT_DOUBLE);
        runner.addFunctionOfServiceMethod("dateSubDays", DECISION_DELEGATE, "dateSubDays", OBJECT_DOUBLE);
        runner.addFunctionOfServiceMethod("dateSubHours", DECISION_DELEGATE, "dateSubHours", OBJECT_DOUBLE);
        runner.addFunctionOfServiceMethod("dateSubMinutes", DECISION_DELEGATE, "dateSubMinutes", OBJECT_DOUBLE);
        runner.addFunctionOfServiceMethod("dateSubSeconds", DECISION_DELEGATE, "dateSubSeconds", OBJECT_DOUBLE);
        runner.addFunctionOfServiceMethod("dateYear", DECISION_DELEGATE, "dateYear", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("dateMonth", DECISION_DELEGATE, "dateMonth", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("dateWeekday", DECISION_DELEGATE, "dateWeekday", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("dateDay", DECISION_DELEGATE, "dateDay", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("dateHour", DECISION_DELEGATE, "dateHour", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("dateMinute", DECISION_DELEGATE, "dateMinute", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("dateSecond", DECISION_DELEGATE, "dateSecond", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("dateDiffMillis", DECISION_DELEGATE, "dateDiffMillis", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("dateDiffSeconds", DECISION_DELEGATE, "dateDiffSeconds", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("dateDiffMinutes", DECISION_DELEGATE, "dateDiffMinutes", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("dateDiffHours", DECISION_DELEGATE, "dateDiffHours", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("dateDiffDays", DECISION_DELEGATE, "dateDiffDays", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("dateDiffWeeks", DECISION_DELEGATE, "dateDiffWeeks", TWO_OBJECTS);
        runner.addFunctionOfServiceMethod("dateDaysInMonths", DECISION_DELEGATE, "dateDaysInMonths", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("dateDaysOutsideMonths", DECISION_DELEGATE, "dateDaysOutsideMonths", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("dateDaysInSpecifiedMonths", DECISION_DELEGATE, "dateDaysInSpecifiedMonths", OBJECT_OBJECT_STRING);
        runner.addFunctionOfServiceMethod("dateDaysOutsideSpecifiedMonths", DECISION_DELEGATE, "dateDaysOutsideSpecifiedMonths", OBJECT_OBJECT_STRING);
        runner.addFunctionOfServiceMethod("dateToMillis", DECISION_DELEGATE, "dateToMillis", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("millisToDate", DECISION_DELEGATE, "millisToDate", DOUBLE_STRING);
        runner.addFunctionOfServiceMethod("idCardBirthDate", DECISION_DELEGATE, "idCardBirthDate", SINGLE_STRING);
        runner.addFunctionOfServiceMethod("idCardAge", DECISION_DELEGATE, "idCardAge", STRING_OBJECT_STRING);

        runner.addFunctionOfServiceMethod("scoreByOdds", DECISION_DELEGATE, "scoreByOdds", DOUBLE_DOUBLE_DOUBLE_STRING);
        runner.addFunctionOfServiceMethod("scoreByProbability", DECISION_DELEGATE, "scoreByProbability", DOUBLE_DOUBLE_DOUBLE_STRING);
        runner.addFunctionOfServiceMethod("scoreByOddsPdo", DECISION_DELEGATE, "scoreByOddsPdo", DOUBLE_DOUBLE_DOUBLE_DOUBLE_STRING);
        runner.addFunctionOfServiceMethod("scoreByBadRatePdo", DECISION_DELEGATE, "scoreByBadRatePdo", DOUBLE_DOUBLE_DOUBLE_DOUBLE_STRING);
        runner.addFunctionOfServiceMethod("setRuntimeValue", RUNTIME_CONTEXT_DELEGATE, "setRuntimeValue", STRING_OBJECT);
        runner.addFunctionOfServiceMethod("currentRule", RUNTIME_CONTEXT_DELEGATE, "currentRule", NO_ARGS);
        runner.addFunctionOfServiceMethod("currentRuleName", RUNTIME_CONTEXT_DELEGATE, "currentRuleName", NO_ARGS);
        runner.addFunctionOfServiceMethod("currentMatchedConditions", RUNTIME_CONTEXT_DELEGATE, "currentMatchedConditions", NO_ARGS);
    }
}
