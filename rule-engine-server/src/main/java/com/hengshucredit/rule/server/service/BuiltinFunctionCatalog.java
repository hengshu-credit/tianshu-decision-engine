package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.hengshucredit.rule.model.entity.RuleFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BuiltinFunctionCatalog {

    private static final String AGGREGATE_CLASS = "com.hengshucredit.rule.core.function.AggregateBuiltinFunctions";
    private static final String DECISION_CLASS = "com.hengshucredit.rule.core.function.DecisionBuiltinFunctions";
    private static final String DIGEST_CLASS = "com.hengshucredit.rule.core.function.DigestBuiltinFunctions";
    private static final String RUNTIME_CONTEXT_CLASS = "com.hengshucredit.rule.core.function.RuntimeContextBuiltinFunctions";
    private static final String RANDOM_CLASS = "com.hengshucredit.rule.core.function.RandomBuiltinFunctions";

    private BuiltinFunctionCatalog() {
    }

    static List<RuleFunction> definitions() {
        List<RuleFunction> list = new ArrayList<>();

        list.add(fn("sum", "序列求和", "对列表、数组或 JSONPath 结果中的有效数字求和", p("data", "OBJECT", "序列", Arrays.asList(1, 2, 3)), "NUMBER", AGGREGATE_CLASS, "sum"));
        list.add(fn("count", "序列计数", "返回列表、数组或 JSONPath 结果的元素数量", p("data", "OBJECT", "序列", Arrays.asList("A", "B", "C")), "NUMBER", AGGREGATE_CLASS, "count"));
        list.add(fn("max", "序列最大值", "返回序列中有效数字的最大值", p("data", "OBJECT", "序列", Arrays.asList(3, 9, 2)), "NUMBER", AGGREGATE_CLASS, "max"));
        list.add(fn("min", "序列最小值", "返回序列中有效数字的最小值", p("data", "OBJECT", "序列", Arrays.asList(3, 9, 2)), "NUMBER", AGGREGATE_CLASS, "min"));
        list.add(fn("avg", "序列平均值", "返回序列中有效数字的算术平均值", p("data", "OBJECT", "序列", Arrays.asList(3, 9, 6)), "NUMBER", AGGREGATE_CLASS, "avg"));
        list.add(fn("isNull", "是否为空指针", "判断值是否为 null", p("value", "OBJECT", "值", null), "BOOLEAN", AGGREGATE_CLASS, "isNull"));
        list.add(fn("isNotNull", "是否非空指针", "判断值是否不是 null", p("value", "OBJECT", "值", "A"), "BOOLEAN", AGGREGATE_CLASS, "isNotNull"));
        list.add(fn("isBlank", "是否为空", "判断 null、空字符串、空集合、空对象或空数组", p("value", "OBJECT", "值", "  "), "BOOLEAN", AGGREGATE_CLASS, "isBlank"));
        list.add(fn("isNotBlank", "是否非空", "判断值是否有实际内容", p("value", "OBJECT", "值", "A"), "BOOLEAN", AGGREGATE_CLASS, "isNotBlank"));
        list.add(fn("containsValue", "是否包含值", "判断字符串、集合或对象 key 是否包含指定值", params(
                p("target", "OBJECT", "目标", Arrays.asList("A", "B")),
                p("value", "OBJECT", "值", "A")), "BOOLEAN", AGGREGATE_CLASS, "containsValue"));
        list.add(fn("containsAnyValue", "是否包含任一值", "判断目标是否包含候选集合中的任一值", params(
                p("target", "OBJECT", "目标", Arrays.asList("A", "B")),
                p("values", "OBJECT", "候选值", Arrays.asList("C", "A"))), "BOOLEAN", AGGREGATE_CLASS, "containsAnyValue"));
        list.add(fn("containsAllValues", "是否包含全部值", "判断目标是否包含候选集合中的全部值", params(
                p("target", "OBJECT", "目标", Arrays.asList("A", "B", "C")),
                p("values", "OBJECT", "候选值", Arrays.asList("A", "C"))), "BOOLEAN", AGGREGATE_CLASS, "containsAllValues"));
        list.add(fn("startsWithValue", "是否指定前缀", "判断文本是否以指定前缀开头", params(
                p("target", "OBJECT", "目标", "ABCD"),
                p("prefix", "OBJECT", "前缀", "AB")), "BOOLEAN", AGGREGATE_CLASS, "startsWithValue"));
        list.add(fn("endsWithValue", "是否指定后缀", "判断文本是否以指定后缀结尾", params(
                p("target", "OBJECT", "目标", "ABCD"),
                p("suffix", "OBJECT", "后缀", "CD")), "BOOLEAN", AGGREGATE_CLASS, "endsWithValue"));
        list.add(fn("hasKey", "对象是否有 key", "判断 Map/对象是否包含指定 key", params(
                p("target", "OBJECT", "对象", mapOf("riskLevel", "HIGH")),
                p("key", "OBJECT", "key", "riskLevel")), "BOOLEAN", AGGREGATE_CLASS, "hasKey"));
        list.add(fn("nvl", "空值兜底", "value 为 null 时返回 fallback", params(
                p("value", "OBJECT", "值", null),
                p("fallback", "OBJECT", "兜底值", "UNKNOWN")), "OBJECT", AGGREGATE_CLASS, "nvl"));
        list.add(fn("roundScale", "数值舍入", "按指定小数位和舍入模式处理数值", params(
                p("value", "OBJECT", "数值", 12.3456),
                p("decimalPlaces", "OBJECT", "小数位", 2),
                p("roundingMode", "OBJECT", "舍入模式", "HALF_UP")), "NUMBER", AGGREGATE_CLASS, "roundScale"));

        list.add(fn("numAdd", "数值相加", "两个数值相加并返回精确小数", params(p("left", "NUMBER", "左值", 12.3), p("right", "NUMBER", "右值", 4.5)), "NUMBER", DECISION_CLASS, "numAdd"));
        list.add(fn("numSub", "数值相减", "两个数值相减并返回精确小数", params(p("left", "NUMBER", "左值", 12.3), p("right", "NUMBER", "右值", 4.5)), "NUMBER", DECISION_CLASS, "numSub"));
        list.add(fn("numMul", "数值相乘", "两个数值相乘并返回精确小数", params(p("left", "NUMBER", "左值", 12.3), p("right", "NUMBER", "右值", 4.5)), "NUMBER", DECISION_CLASS, "numMul"));
        list.add(fn("numDiv", "数值相除", "两个数值相除，按 scale 四舍五入；除数为 0 返回 null", params(p("left", "NUMBER", "被除数", 10), p("right", "NUMBER", "除数", 3), p("scale", "NUMBER", "小数位", 2)), "NUMBER", DECISION_CLASS, "numDiv"));
        list.add(fn("numRound", "数值四舍五入", "按指定小数位四舍五入", params(p("value", "NUMBER", "数值", 12.3456), p("scale", "NUMBER", "小数位", 2)), "NUMBER", DECISION_CLASS, "numRound"));
        list.add(fn("numAbs", "数值绝对值", "返回数值绝对值", p("value", "NUMBER", "数值", -12.3), "NUMBER", DECISION_CLASS, "numAbs"));
        list.add(fn("numPow", "数值幂运算", "返回 value 的 exponent 次幂", params(p("value", "NUMBER", "底数", 2), p("exponent", "NUMBER", "指数", 3)), "NUMBER", DECISION_CLASS, "numPow"));
        list.add(fn("numBetween", "数值区间判断", "判断 value 是否在 [min, max] 闭区间内", params(p("value", "NUMBER", "数值", 85), p("min", "NUMBER", "下限", 60), p("max", "NUMBER", "上限", 100)), "BOOLEAN", DECISION_CLASS, "numBetween"));
        list.add(fn("randomInt", "随机整数",
                "生成随机整数；支持 randomInt()、randomInt(lower, upper) 和 randomInt(lower, upper, includeLower, includeUpper)，默认闭区间；仅用于普通业务抽样，不用于安全随机",
                params(
                        p("lower", "NUMBER", "下界", 0),
                        p("upper", "NUMBER", "上界", 1),
                        p("includeLower", "BOOLEAN", "包含下界", true),
                        p("includeUpper", "BOOLEAN", "包含上界", true)),
                "NUMBER", RANDOM_CLASS, "randomIntForManagement"));
        list.add(fn("randomDecimal", "随机小数",
                "生成随机小数；支持 randomDecimal()、randomDecimal(lower, upper) 和 randomDecimal(lower, upper, includeLower, includeUpper)，默认闭区间；仅用于普通业务抽样，不用于安全随机",
                params(
                        p("lower", "NUMBER", "下界", 0),
                        p("upper", "NUMBER", "上界", 1),
                        p("includeLower", "BOOLEAN", "包含下界", true),
                        p("includeUpper", "BOOLEAN", "包含上界", true)),
                "NUMBER", RANDOM_CLASS, "randomDecimalForManagement"));

        list.add(fn("strLength", "字符串长度", "返回字符串长度，null 返回 0", p("text", "STRING", "文本", "ABC123"), "NUMBER", DECISION_CLASS, "strLength"));
        list.add(fn("strTrim", "字符串去空白", "去除字符串首尾空白", p("text", "STRING", "文本", "  ABC123  "), "STRING", DECISION_CLASS, "strTrim"));
        list.add(fn("strContains", "字符串包含", "判断文本是否包含关键字", params(p("text", "STRING", "文本", "ABC123"), p("keyword", "STRING", "关键字", "BC")), "BOOLEAN", DECISION_CLASS, "strContains"));
        list.add(fn("strReplace", "字符串正则替换", "使用正则表达式替换文本", params(p("text", "STRING", "文本", "A-123"), p("regex", "STRING", "正则", "[^0-9]"), p("replacement", "STRING", "替换值", "")), "STRING", DECISION_CLASS, "strReplace"));
        list.add(fn("strRegexExtract", "字符串正则提取", "按正则分组提取文本，未匹配返回 null", params(p("text", "STRING", "文本", "phone=13800138000"), p("regex", "STRING", "正则", "phone=([0-9]+)"), p("groupIndex", "NUMBER", "分组序号", 1)), "STRING", DECISION_CLASS, "strRegexExtract"));
        list.add(fn("strSplit", "字符串切分", "按字面量分隔符切分文本", params(p("text", "STRING", "文本", "A,B,C"), p("delimiter", "STRING", "分隔符", ",")), "LIST", DECISION_CLASS, "strSplit"));
        list.add(fn("strJoin", "序列拼接字符串", "将序列元素按分隔符拼接", params(p("values", "LIST", "序列", Arrays.asList("A", "B", "C")), p("delimiter", "STRING", "分隔符", "|")), "STRING", DECISION_CLASS, "strJoin"));
        list.add(fn("md5", "MD5 字段摘要", "按 UTF-8 计算 MD5 并返回小写十六进制；仅用于兼容旧系统，不用于密码或安全签名",
                p("text", "STRING", "字段文本", "abc"), "STRING", DIGEST_CLASS, "md5"));
        list.add(fn("sha1", "SHA-1 字段摘要", "按 UTF-8 计算 SHA-1 并返回小写十六进制；仅用于兼容旧系统，不用于密码或安全签名",
                p("text", "STRING", "字段文本", "abc"), "STRING", DIGEST_CLASS, "sha1"));
        list.add(fn("sha256", "SHA-256 字段摘要", "按 UTF-8 计算 SHA-256 并返回小写十六进制；低熵敏感字段建议使用 HMAC-SHA256",
                p("text", "STRING", "字段文本", "abc"), "STRING", DIGEST_CLASS, "sha256"));
        list.add(fn("hmacSha256", "HMAC-SHA256 字段摘要", "使用密钥按 UTF-8 计算 HMAC-SHA256；空密钥返回 null，密钥应从运行时安全变量传入，不要写死在规则中",
                params(p("text", "STRING", "字段文本", "abc"), p("key", "STRING", "密钥", "secret-key")),
                "STRING", DIGEST_CLASS, "hmacSha256"));

        list.add(fn("arrSize", "数组长度", "返回列表、数组或对象值集合的元素数量", p("values", "LIST", "数组", Arrays.asList("A", "B", "C")), "NUMBER", DECISION_CLASS, "arrSize"));
        list.add(fn("arrGet", "数组按下标取值", "按下标取值，支持负数倒序下标", params(p("values", "LIST", "数组", Arrays.asList("A", "B", "C")), p("index", "NUMBER", "下标", 1)), "OBJECT", DECISION_CLASS, "arrGet"));
        list.add(fn("arrFirst", "数组首元素", "返回序列第一个元素", p("values", "LIST", "数组", Arrays.asList("A", "B", "C")), "OBJECT", DECISION_CLASS, "arrFirst"));
        list.add(fn("arrLast", "数组末元素", "返回序列最后一个元素", p("values", "LIST", "数组", Arrays.asList("A", "B", "C")), "OBJECT", DECISION_CLASS, "arrLast"));
        list.add(fn("arrDistinct", "数组去重", "按数值和字符串等值语义去重", p("values", "LIST", "数组", Arrays.asList("A", "B", "A", 1, 1.0)), "LIST", DECISION_CLASS, "arrDistinct"));
        list.add(fn("arrSort", "数组排序", "对数值或文本序列排序，direction 支持 ASC/DESC", params(p("values", "LIST", "数组", Arrays.asList(3, 1, 2)), p("direction", "STRING", "方向", "ASC")), "LIST", DECISION_CLASS, "arrSort"));
        list.add(fn("arrContains", "数组包含", "判断序列是否包含指定值", params(p("values", "LIST", "数组", Arrays.asList("A", "B")), p("target", "OBJECT", "目标值", "A")), "BOOLEAN", DECISION_CLASS, "arrContains"));

        list.add(fn("jsonParse", "JSON 文本解析", "将 JSON 字符串解析为对象或数组，失败返回 null", p("text", "STRING", "JSON文本", JSON.toJSONString(sampleJson())), "OBJECT", DECISION_CLASS, "jsonParse"));
        list.add(fn("jsonGet", "JSONPath 取值", "使用 JSONPath 从对象或 JSON 文本中取值，支持筛选表达式", params(p("json", "OBJECT", "JSON", sampleJson()), p("path", "STRING", "JSONPath", "$.customer.age")), "OBJECT", DECISION_CLASS, "jsonGet"));
        list.add(fn("jsonList", "JSONPath 取列表", "使用 JSONPath 取列表；标量结果会包装为单元素列表", params(p("json", "OBJECT", "JSON", sampleJson()), p("path", "STRING", "JSONPath", "$.orders[?(@.status='SUCCESS')].amount")), "LIST", DECISION_CLASS, "jsonList"));
        list.add(fn("jsonExists", "JSONPath 是否存在", "判断 JSONPath 是否存在", params(p("json", "OBJECT", "JSON", sampleJson()), p("path", "STRING", "JSONPath", "$.customer.age")), "BOOLEAN", DECISION_CLASS, "jsonExists"));
        list.add(fn("jsonCount", "JSONPath 结果计数", "统计 JSONPath 结果数量", params(p("json", "OBJECT", "JSON", sampleJson()), p("path", "STRING", "JSONPath", "$.orders[*]")), "NUMBER", DECISION_CLASS, "jsonCount"));
        list.add(fn("jsonSum", "JSONPath 求和", "对 JSONPath 结果中的有效数字求和", params(p("json", "OBJECT", "JSON", sampleJson()), p("path", "STRING", "JSONPath", "$.orders[?(@.status='SUCCESS')].amount")), "NUMBER", DECISION_CLASS, "jsonSum"));
        list.add(fn("jsonAvg", "JSONPath 平均值", "对 JSONPath 结果中的有效数字求平均值", params(p("json", "OBJECT", "JSON", sampleJson()), p("path", "STRING", "JSONPath", "$.orders[*].amount")), "NUMBER", DECISION_CLASS, "jsonAvg"));
        list.add(fn("jsonMin", "JSONPath 最小值", "对 JSONPath 结果中的有效数字取最小值", params(p("json", "OBJECT", "JSON", sampleJson()), p("path", "STRING", "JSONPath", "$.orders[*].amount")), "NUMBER", DECISION_CLASS, "jsonMin"));
        list.add(fn("jsonMax", "JSONPath 最大值", "对 JSONPath 结果中的有效数字取最大值", params(p("json", "OBJECT", "JSON", sampleJson()), p("path", "STRING", "JSONPath", "$.orders[*].amount")), "NUMBER", DECISION_CLASS, "jsonMax"));
        list.add(fn("objGet", "对象路径取值", "从 Map/对象/JSON 文本中按路径取值，路径可省略 $. 前缀", params(p("object", "OBJECT", "对象", sampleJson()), p("path", "STRING", "路径", "customer.age")), "OBJECT", DECISION_CLASS, "objGet"));
        list.add(fn("objGetOrDefault", "对象路径取值带默认值", "路径无值时返回 fallback", params(p("object", "OBJECT", "对象", sampleJson()), p("path", "STRING", "路径", "customer.level"), p("fallback", "OBJECT", "默认值", "UNKNOWN")), "OBJECT", DECISION_CLASS, "objGetOrDefault"));
        list.add(fn("objHas", "对象路径是否存在", "判断对象路径是否存在", params(p("object", "OBJECT", "对象", sampleJson()), p("path", "STRING", "路径", "customer.age")), "BOOLEAN", DECISION_CLASS, "objHas"));
        list.add(fn("objSize", "对象大小", "返回对象 key 数量、数组长度、字符串长度或标量 1", p("object", "OBJECT", "对象", sampleJson()), "NUMBER", DECISION_CLASS, "objSize"));
        list.add(fn("objKeys", "对象 key 列表", "返回 Map/对象的 key 列表", p("object", "OBJECT", "对象", sampleJson()), "LIST", DECISION_CLASS, "objKeys"));
        list.add(fn("objValues", "对象 value 列表", "返回 Map/对象的 value 列表", p("object", "OBJECT", "对象", sampleJson()), "LIST", DECISION_CLASS, "objValues"));
        list.add(fn("toJson", "对象转 JSON", "将对象、数组或标量序列化为 JSON 字符串", p("value", "OBJECT", "值", sampleJson()), "STRING", DECISION_CLASS, "toJson"));

        list.add(fn("dateFormat", "日期格式化", "将日期、时间戳或常见日期字符串格式化为指定样式", params(
                p("input_date", "OBJECT", "日期", "2026-07-09 10:30:00"),
                p("format_pattern", "STRING", "输出格式", "yyyy-MM-dd")), "STRING", DECISION_CLASS, "dateFormat"));
        list.add(fn("dateConvert", "日期格式转换", "按输入格式解析日期文本，并转换为新的输出格式", params(
                p("input_date", "STRING", "日期文本", "20260709"),
                p("from_pattern", "STRING", "输入格式", "yyyyMMdd"),
                p("to_pattern", "STRING", "输出格式", "yyyy-MM-dd")), "STRING", DECISION_CLASS, "dateConvert"));
        list.add(fn("dateAdd", "日期加法", "对日期按年、季、月、周、日、小时、分钟、秒或毫秒增加指定数量", params(
                p("input_date", "OBJECT", "日期", "2026-07-09 10:30:00"),
                p("amount", "NUMBER", "数量", 3),
                p("unit", "STRING", "单位", "DAY")), "STRING", DECISION_CLASS, "dateAdd"));
        list.add(fn("dateSub", "日期减法", "对日期按指定单位减少指定数量", params(
                p("input_date", "OBJECT", "日期", "2026-07-09 10:30:00"),
                p("amount", "NUMBER", "数量", 1),
                p("unit", "STRING", "单位", "MONTH")), "STRING", DECISION_CLASS, "dateSub"));
        list.add(fn("dateDiff", "日期差值", "计算两个日期之间的差值，单位支持 YEAR/QUARTER/MONTH/WEEK/DAY/HOUR/MINUTE/SECOND/MILLISECOND", params(
                p("start_date", "OBJECT", "开始日期", "2026-07-01 00:00:00"),
                p("end_date", "OBJECT", "结束日期", "2026-07-09 12:30:00"),
                p("unit", "STRING", "单位", "DAY")), "NUMBER", DECISION_CLASS, "dateDiff"));
        list.add(fn("dateToMillis", "日期转毫秒时间戳", "将日期、时间戳或常见日期字符串转换为毫秒时间戳", p("input_date", "OBJECT", "日期", "2026-07-09 10:30:00"), "NUMBER", DECISION_CLASS, "dateToMillis"));
        list.add(fn("millisToDate", "毫秒时间戳转日期", "将毫秒时间戳格式化为日期字符串", params(
                p("timestamp", "NUMBER", "毫秒时间戳", 1783564200000L),
                p("format_pattern", "STRING", "输出格式", "yyyy-MM-dd HH:mm:ss")), "STRING", DECISION_CLASS, "millisToDate"));
        list.add(fn("idCardBirthDate", "身份证提取出生日期", "提取身份证出生日期并返回 yyyy-MM-dd；无法识别时返回 null",
                p("idCard", "STRING", "身份证号", "110105199001022317"), "STRING", DECISION_CLASS, "idCardBirthDate"));
        list.add(fn("idCardAge", "身份证计算年龄", "按身份证出生日期计算年龄；YEAR 按年份差，FULL/DAY 按周岁计算", params(
                p("idCard", "STRING", "身份证号", "110105199001022317"),
                p("currentDate", "OBJECT", "计算日期", "2025-01-02 12:30:00"),
                p("calcMode", "STRING", "计算方式", "FULL")), "NUMBER", DECISION_CLASS, "idCardAge"));

        list.add(fn("scoreByOdds", "odds 转评分", "按 score = A +/- B * ln(odds) 将 odds 倍率转换为评分，direction 支持 ASC/DESC", params(
                p("odds", "NUMBER", "odds 倍率", 20),
                p("a", "NUMBER", "A 参数", 600),
                p("b", "NUMBER", "B 参数", 28.8539),
                p("direction", "STRING", "方向", "ASC")), "NUMBER", DECISION_CLASS, "scoreByOdds"));
        list.add(fn("scoreByProbability", "概率转评分", "按 A +/- B * ln(p/(1-p)) 将概率转换为评分；默认越大越好使用减号", params(
                p("p", "NUMBER", "概率 p", 0.05),
                p("a", "NUMBER", "A 参数", 600),
                p("b", "NUMBER", "B 参数", 20),
                p("direction", "STRING", "方向 HIGH_GOOD/LOW_GOOD", "HIGH_GOOD")), "NUMBER", DECISION_CLASS, "scoreByProbability"));
        list.add(fn("scoreByOddsPdo", "odds 按 PDO 转评分", "通过基础分、基础 odds、PDO 和方向将 odds 倍率转换为评分", params(
                p("odds", "NUMBER", "odds 倍率", 40),
                p("base_score", "NUMBER", "基础分", 600),
                p("base_odds", "NUMBER", "基础 odds", 20),
                p("pdo", "NUMBER", "PDO", 20),
                p("direction", "STRING", "方向", "ASC")), "NUMBER", DECISION_CLASS, "scoreByOddsPdo"));
        list.add(fn("scoreByBadRatePdo", "坏账率按 PDO 转评分", "先将 bad_rate 转为 good/bad odds，再按基础分、基础 odds、PDO 和方向转换为评分", params(
                p("bad_rate", "NUMBER", "坏账率", 0.05),
                p("base_score", "NUMBER", "基础分", 600),
                p("base_odds", "NUMBER", "基础 odds", 20),
                p("pdo", "NUMBER", "PDO", 20),
                p("direction", "STRING", "方向", "ASC")), "NUMBER", DECISION_CLASS, "scoreByBadRatePdo"));

        addExtendedDefinitions(list);
        return list;
    }

    private static void addExtendedDefinitions(List<RuleFunction> list) {
        add(list, "numMax", "两数最大值", "NUMBER", DECISION_CLASS, p("left", "NUMBER", "左值", 9), p("right", "NUMBER", "右值", 2));
        add(list, "numMin", "两数最小值", "NUMBER", DECISION_CLASS, p("left", "NUMBER", "左值", 9), p("right", "NUMBER", "右值", 2));
        add(list, "numSin", "正弦", "NUMBER", DECISION_CLASS, p("value", "NUMBER", "弧度", 0));
        add(list, "numCos", "余弦", "NUMBER", DECISION_CLASS, p("value", "NUMBER", "弧度", 0));
        add(list, "numTan", "正切", "NUMBER", DECISION_CLASS, p("value", "NUMBER", "弧度", 0));
        add(list, "numCot", "余切", "NUMBER", DECISION_CLASS, p("value", "NUMBER", "弧度", 1));
        add(list, "numLn", "自然对数", "NUMBER", DECISION_CLASS, p("value", "NUMBER", "正数", Math.E));
        add(list, "numLog10", "常用对数", "NUMBER", DECISION_CLASS, p("value", "NUMBER", "正数", 100));
        add(list, "numCeil", "向上取整", "NUMBER", DECISION_CLASS, p("value", "NUMBER", "数值", 1.2));
        add(list, "numFloor", "向下取整", "NUMBER", DECISION_CLASS, p("value", "NUMBER", "数值", 1.9));
        add(list, "numRoundInteger", "四舍五入取整", "NUMBER", DECISION_CLASS, p("value", "NUMBER", "数值", 1.5));

        add(list, "strSubstring", "截取字符串", "STRING", DECISION_CLASS, p("text", "STRING", "文本", "ABCDE"), p("start", "NUMBER", "开始下标", 1), p("end", "NUMBER", "结束下标", 4));
        add(list, "strSubstringFrom", "从下标截取", "STRING", DECISION_CLASS, p("text", "STRING", "文本", "ABCDE"), p("start", "NUMBER", "开始下标", 2));
        add(list, "strSubstringTo", "截取到下标", "STRING", DECISION_CLASS, p("text", "STRING", "文本", "ABCDE"), p("end", "NUMBER", "结束下标", 3));
        add(list, "strLower", "转小写", "STRING", DECISION_CLASS, p("text", "STRING", "文本", "AbC"));
        add(list, "strUpper", "转大写", "STRING", DECISION_CLASS, p("text", "STRING", "文本", "AbC"));
        add(list, "strCharAt", "按下标取字符", "STRING", DECISION_CLASS, p("text", "STRING", "文本", "ABC"), p("index", "NUMBER", "下标", 1));
        add(list, "strIndexOf", "首次出现位置", "NUMBER", DECISION_CLASS, p("text", "STRING", "文本", "ABCA"), p("target", "STRING", "目标", "B"));
        add(list, "strLastIndexOf", "末次出现位置", "NUMBER", DECISION_CLASS, p("text", "STRING", "文本", "ABCA"), p("target", "STRING", "目标", "A"));
        add(list, "strReplaceLiteral", "按字面量替换", "STRING", DECISION_CLASS, p("text", "STRING", "文本", "A-B"), p("target", "STRING", "目标", "-"), p("replacement", "STRING", "替换值", "."));

        add(list, "arrMax", "数组最大值", "OBJECT", DECISION_CLASS, p("values", "LIST", "数组", Arrays.asList(3, 1, 2)));
        add(list, "arrMin", "数组最小值", "OBJECT", DECISION_CLASS, p("values", "LIST", "数组", Arrays.asList(3, 1, 2)));
        add(list, "arrAdd", "数组追加元素", "LIST", DECISION_CLASS, p("values", "LIST", "数组", Arrays.asList(1, 2)), p("value", "OBJECT", "元素", 3));
        add(list, "arrRemove", "数组移除元素", "LIST", DECISION_CLASS, p("values", "LIST", "数组", Arrays.asList(1, 2, 3)), p("value", "OBJECT", "元素", 2));
        add(list, "arrSortBy", "数组按字段排序", "LIST", DECISION_CLASS, p("values", "LIST", "对象数组", sampleRows()), p("path", "STRING", "字段路径", "score"), p("direction", "STRING", "方向", "ASC"));
        add(list, "arrPluck", "提取数组字段", "LIST", DECISION_CLASS, p("values", "LIST", "对象数组", sampleRows()), p("path", "STRING", "字段路径", "score"));
        add(list, "arrIsEmpty", "数组是否为空", "BOOLEAN", DECISION_CLASS, p("values", "LIST", "数组", Arrays.asList()));

        add(list, "mapPut", "字典设置键值", "MAP", DECISION_CLASS, p("source", "MAP", "字典", mapOf("a", 1)), p("key", "STRING", "键", "b"), p("value", "OBJECT", "值", 2));
        add(list, "mapRemove", "字典移除键", "MAP", DECISION_CLASS, p("source", "MAP", "字典", mapOf("a", 1)), p("key", "STRING", "键", "a"));
        add(list, "mapHasKey", "字典包含键", "BOOLEAN", DECISION_CLASS, p("source", "MAP", "字典", mapOf("a", 1)), p("key", "STRING", "键", "a"));
        add(list, "mapGet", "字典按键取值", "OBJECT", DECISION_CLASS, p("source", "MAP", "字典", mapOf("a", 1)), p("key", "STRING", "键", "a"));
        add(list, "mapSize", "字典键数量", "NUMBER", DECISION_CLASS, p("source", "MAP", "字典", mapOf("a", 1)));
        add(list, "mapKeys", "字典键列表", "LIST", DECISION_CLASS, p("source", "MAP", "字典", mapOf("a", 1)));
        add(list, "mapValues", "字典值列表", "LIST", DECISION_CLASS, p("source", "MAP", "字典", mapOf("a", 1)));
        add(list, "newMap", "新建空字典", "MAP", DECISION_CLASS);
        add(list, "newList", "新建空数组", "LIST", DECISION_CLASS);
        add(list, "newLike", "新建同结构容器", "OBJECT", DECISION_CLASS, p("source", "OBJECT", "源容器", mapOf("a", 1)));
        add(list, "toStringValue", "转换为字符串", "STRING", DECISION_CLASS, p("value", "OBJECT", "值", 12.5));
        add(list, "toNumberValue", "转换为数值", "NUMBER", DECISION_CLASS, p("value", "OBJECT", "值", "12.5"));
        add(list, "toBooleanValue", "转换为布尔值", "BOOLEAN", DECISION_CLASS, p("value", "OBJECT", "值", "true"));
        add(list, "toListValue", "转换为数组", "LIST", DECISION_CLASS, p("value", "OBJECT", "值", "[1,2]"));
        add(list, "toMapValue", "转换为字典", "MAP", DECISION_CLASS, p("value", "OBJECT", "值", "{\"a\":1}"));

        add(list, "currentDate", "获取当前日期", "STRING", DECISION_CLASS);
        add(list, "currentDateTime", "获取当前日期时间", "STRING", DECISION_CLASS);
        add(list, "dateAddParts", "按年月日时分秒增加日期", "STRING", DECISION_CLASS,
                p("date", "OBJECT", "日期", "2026-01-31 10:20:30"), p("years", "NUMBER", "年", 1),
                p("months", "NUMBER", "月", 1), p("days", "NUMBER", "日", 3),
                p("hours", "NUMBER", "小时", 4), p("minutes", "NUMBER", "分钟", 5), p("seconds", "NUMBER", "秒", 6));
        add(list, "dateSubParts", "按年月日时分秒减少日期", "STRING", DECISION_CLASS,
                p("date", "OBJECT", "日期", "2026-01-31 10:20:30"), p("years", "NUMBER", "年", 1),
                p("months", "NUMBER", "月", 1), p("days", "NUMBER", "日", 3),
                p("hours", "NUMBER", "小时", 4), p("minutes", "NUMBER", "分钟", 5), p("seconds", "NUMBER", "秒", 6));
        addDateShift(list, "dateAddYears", "日期增加年");
        addDateShift(list, "dateAddMonths", "日期增加月");
        addDateShift(list, "dateAddDays", "日期增加日");
        addDateShift(list, "dateAddHours", "日期增加小时");
        addDateShift(list, "dateAddMinutes", "日期增加分钟");
        addDateShift(list, "dateAddSeconds", "日期增加秒");
        addDateShift(list, "dateSubYears", "日期减少年");
        addDateShift(list, "dateSubMonths", "日期减少月");
        addDateShift(list, "dateSubDays", "日期减少日");
        addDateShift(list, "dateSubHours", "日期减少小时");
        addDateShift(list, "dateSubMinutes", "日期减少分钟");
        addDateShift(list, "dateSubSeconds", "日期减少秒");
        addDatePart(list, "dateYear", "获取年份");
        addDatePart(list, "dateMonth", "获取月份");
        addDatePart(list, "dateWeekday", "获取星期");
        addDatePart(list, "dateDay", "获取日");
        addDatePart(list, "dateHour", "获取小时");
        addDatePart(list, "dateMinute", "获取分钟");
        addDatePart(list, "dateSecond", "获取秒");
        addDateDiff(list, "dateDiffMillis", "日期相差毫秒");
        addDateDiff(list, "dateDiffSeconds", "日期相差秒");
        addDateDiff(list, "dateDiffMinutes", "日期相差分钟");
        addDateDiff(list, "dateDiffHours", "日期相差小时");
        addDateDiff(list, "dateDiffDays", "日期相差天");
        addDateDiff(list, "dateDiffWeeks", "日期相差周");
        add(list, "dateDaysInMonths", "日期所在月的全部日期", "LIST", DECISION_CLASS, p("date", "OBJECT", "日期", "2026-07-14"));
        add(list, "dateDaysOutsideMonths", "日期所在年其他月份的日期", "LIST", DECISION_CLASS, p("date", "OBJECT", "日期", "2026-07-14"));
        add(list, "dateDaysInSpecifiedMonths", "时间段内指定月份日期", "LIST", DECISION_CLASS,
                p("start", "OBJECT", "开始日期", "2026-01-01"), p("end", "OBJECT", "结束日期", "2026-03-31"), p("months", "STRING", "月份", "1,2"));
        add(list, "dateDaysOutsideSpecifiedMonths", "时间段内非指定月份日期", "LIST", DECISION_CLASS,
                p("start", "OBJECT", "开始日期", "2026-01-01"), p("end", "OBJECT", "结束日期", "2026-03-31"), p("months", "STRING", "月份", "1,2"));

        add(list, "currentRule", "获取当前规则", "MAP", RUNTIME_CONTEXT_CLASS);
        add(list, "currentRuleName", "获取当前规则名称", "STRING", RUNTIME_CONTEXT_CLASS);
        add(list, "currentMatchedConditions", "获取当前命中条件", "LIST", RUNTIME_CONTEXT_CLASS);
        list.add(beanFn("isInLists", "是否在一个或多个名单内", "按单个值查询一个或多个名单库并返回布尔值",
                params(p("value", "OBJECT", "查询值", "13800138000"), p("listIds", "OBJECT", "名单ID", Arrays.asList(1, 2))), "BOOLEAN"));
        list.add(beanFn("isInListsNumber", "是否在名单内（数字）", "按单个值查询一个或多个名单库并返回0或1",
                params(p("value", "OBJECT", "查询值", "13800138000"), p("listIds", "OBJECT", "名单ID", Arrays.asList(1, 2))), "NUMBER"));
        String matrixParams = params(p("values", "OBJECT", "查询值", Arrays.asList("13800138000", "110101199001010011")),
                p("listIds", "OBJECT", "名单ID", Arrays.asList(1, 2)),
                p("combinationMode", "STRING", "组合模式", "ANY_FIELD_ANY_LIST"),
                p("matchMode", "STRING", "匹配方式", "IN_LIST"),
                p("itemTypes", "OBJECT", "内容类型", Arrays.asList("MOBILE", "ID_CARD")));
        list.add(beanFn("listMatch", "多字段多名单匹配", "按字段与名单组合模式返回布尔值", matrixParams, "BOOLEAN"));
        list.add(beanFn("listMatchNumber", "多字段多名单匹配（数字）", "按字段与名单组合模式返回0或1", matrixParams, "NUMBER"));
    }

    private static void addDateShift(List<RuleFunction> list, String code, String name) {
        add(list, code, name, "STRING", DECISION_CLASS, p("date", "OBJECT", "日期", "2026-07-14 10:20:30"), p("amount", "NUMBER", "数量", 1));
    }

    private static void addDatePart(List<RuleFunction> list, String code, String name) {
        add(list, code, name, "NUMBER", DECISION_CLASS, p("date", "OBJECT", "日期", "2026-07-14 15:16:17"));
    }

    private static void addDateDiff(List<RuleFunction> list, String code, String name) {
        add(list, code, name, "NUMBER", DECISION_CLASS, p("start", "OBJECT", "开始日期", "2026-07-01 00:00:00"), p("end", "OBJECT", "结束日期", "2026-07-03 00:00:00"));
    }

    private static void add(List<RuleFunction> list, String code, String name, String returnType,
                            String implClass, JSONObject... arguments) {
        list.add(fn(code, name, name, params(arguments), returnType, implClass, code));
    }

    private static RuleFunction fn(String code, String name, String description, JSONObject param, String returnType,
                                   String implClass, String implMethod) {
        return fn(code, name, description, params(param), returnType, implClass, implMethod);
    }

    private static RuleFunction fn(String code, String name, String description, String paramsJson, String returnType,
                                   String implClass, String implMethod) {
        RuleFunction function = new RuleFunction();
        function.setProjectId(0L);
        function.setScope(RuleFunctionService.SCOPE_GLOBAL);
        function.setFuncCode(code);
        function.setFuncName(name);
        function.setDescription(description);
        function.setParamsJson(paramsJson);
        function.setReturnType(returnType);
        function.setImplType("JAVA");
        function.setImplClass(implClass);
        function.setImplMethod(implMethod);
        function.setStatus(1);
        return function;
    }

    private static RuleFunction beanFn(String code, String name, String description, String paramsJson, String returnType) {
        RuleFunction function = new RuleFunction();
        function.setProjectId(0L);
        function.setScope(RuleFunctionService.SCOPE_GLOBAL);
        function.setFuncCode(code);
        function.setFuncName(name);
        function.setDescription(description);
        function.setParamsJson(paramsJson);
        function.setReturnType(returnType);
        function.setImplType("BEAN");
        function.setImplBeanName("ruleListFunctions");
        function.setImplMethod(code);
        function.setStatus(1);
        return function;
    }

    private static String params(JSONObject... params) {
        JSONArray array = new JSONArray();
        array.addAll(Arrays.asList(params));
        return JSON.toJSONString(array, SerializerFeature.WriteMapNullValue);
    }

    private static JSONObject p(String name, String type, String label, Object example) {
        JSONObject object = new JSONObject();
        object.put("name", name);
        object.put("type", type);
        object.put("label", label);
        object.put("example", example);
        return object;
    }

    private static Map<String, Object> sampleJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("age", 35);
        customer.put("city", "上海");
        root.put("customer", customer);
        root.put("orders", Arrays.asList(
                order("SUCCESS", 12.5, "A"),
                order("FAIL", 3.0, "B"),
                order("SUCCESS", 7.5, "C")
        ));
        return root;
    }

    private static List<Map<String, Object>> sampleRows() {
        return Arrays.asList(mapOf("score", 20), mapOf("score", 10));
    }

    private static Map<String, Object> order(String status, double amount, String type) {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("status", status);
        order.put("amount", amount);
        order.put("type", type);
        return order;
    }

    private static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }
}
