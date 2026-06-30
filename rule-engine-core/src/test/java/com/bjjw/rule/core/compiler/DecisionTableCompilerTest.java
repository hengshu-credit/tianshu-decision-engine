package com.bjjw.rule.core.compiler;

import com.bjjw.rule.core.engine.QLExpressEngine;
import com.bjjw.rule.model.dto.RuleResult;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * DecisionTableCompiler 单元测试。
 * 覆盖：空模型、单一规则、hitPolicy、outputVarCodes、VarContext 解析等场景。
 */
public class DecisionTableCompilerTest {

    private DecisionTableCompiler compiler;
    private QLExpressEngine engine;

    @Before
    public void setUp() {
        compiler = new DecisionTableCompiler();
        engine = new QLExpressEngine();
    }

    private CompileResult compile(String json) {
        return compiler.compile(json);
    }

    private CompileResult compile(String json, VarContext ctx) {
        return compiler.compile(json, ctx);
    }

    /** 无 rules 字段时返回失败 */
    @Test
    public void test空模型_返回失败() {
        CompileResult r = compile("{}");
        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage().contains("缺少必要字段"));
    }

    /** 空 rules 数组不崩溃 */
    @Test
    public void test空规则数组_返回成功() {
        CompileResult r = compile("{\"rules\":[]}");
        assertTrue(r.isSuccess());
        assertNotNull(r.getCompiledScript());
    }

    /** 单一规则无条件无动作，返回输出变量声明 */
    @Test
    public void test单一规则无动作_返回脚本() {
        CompileResult r = compile("{\"rules\":[{\"conditions\":[],\"actions\":[]}]}");
        assertTrue(r.isSuccess());
        assertNotNull(r.getCompiledScript());
    }

    /** 带条件的规则生成 if 语句 */
    @Test
    public void test带条件规则_生成if语句() {
        CompileResult r = compile("{\"rules\":[{\"conditions\":[{\"varCode\":\"age\",\"operator\":\">=\",\"value\":\"18\"}],\"actions\":[]}]}");
        assertTrue(r.isSuccess());
        assertTrue(r.getCompiledScript().contains("if ("));
    }

    /** hitPolicy=FIRST 生成 if/else if 链 */
    @Test
    public void testHitPolicyFirst_生成elseIf链() {
        CompileResult r = compile("{\n" +
                "  \"hitPolicy\": \"FIRST\",\n" +
                "  \"rules\": [\n" +
                "    {\"conditions\": [{\"varCode\": \"score\", \"operator\": \">=\", \"value\": \"90\"}], \"actions\": []},\n" +
                "    {\"conditions\": [{\"varCode\": \"score\", \"operator\": \">=\", \"value\": \"60\"}], \"actions\": []}\n" +
                "  ]\n" +
                "}");
        assertTrue(r.isSuccess());
        String script = r.getCompiledScript();
        assertTrue(script.contains("if ("));
        assertTrue(script.contains(" else if ("));
    }

    /** hitPolicy=ALL 生成顺序 if 语句 */
    @Test
    public void testHitPolicyAll_生成顺序if() {
        CompileResult r = compile("{\n" +
                "  \"hitPolicy\": \"ALL\",\n" +
                "  \"rules\": [\n" +
                "    {\"conditions\": [{\"varCode\": \"a\", \"operator\": \">\", \"value\": \"0\"}], \"actions\": []},\n" +
                "    {\"conditions\": [{\"varCode\": \"b\", \"operator\": \">\", \"value\": \"0\"}], \"actions\": []}\n" +
                "  ]\n" +
                "}");
        assertTrue(r.isSuccess());
        String script = r.getCompiledScript();
        assertTrue(script.contains("if ("));
        assertFalse(script.contains(" else if ("));
    }

    /** 全局 action 列 + 行内 actions（仅有 value）生成输出赋值 */
    @Test
    public void test全局动作列_生成赋值() {
        CompileResult r = compile("{\n" +
                "  \"actions\": [{\"varCode\": \"result\", \"varType\": \"STRING\"}],\n" +
                "  \"rules\": [\n" +
                "    {\"conditions\": [], \"actions\": [{\"value\": \"PASS\"}]}\n" +
                "  ]\n" +
                "}");
        assertTrue(r.isSuccess());
        // 行内 actions 无 varCode 时从全局 actions 列取 varCode
        assertTrue(r.getCompiledScript().contains("result = \"PASS\""));
    }

    /** 行内 action varCode 生成输出赋值 */
    @Test
    public void test行内动作_生成赋值() {
        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditions\": [],\n" +
                "    \"actions\": [{\"varCode\": \"taxAmount\", \"varType\": \"DOUBLE\", \"value\": \"1000.0\"}]\n" +
                "  }]\n" +
                "}");
        assertTrue(r.isSuccess());
        assertTrue(r.getCompiledScript().contains("taxAmount = 1000.0"));
    }

    /** action varType=STRING 值带引号 */
    @Test
    public void test字符串动作_值带引号() {
        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditions\": [],\n" +
                "    \"actions\": [{\"varCode\": \"level\", \"varType\": \"STRING\", \"value\": \"HIGH\"}]\n" +
                "  }]\n" +
                "}");
        assertTrue(r.isSuccess());
        assertTrue(r.getCompiledScript().contains("level = \"HIGH\""));
    }

    /** 多条规则多输出变量生成完整的结果 Map */
    @Test
    public void test多规则多输出变量_生成结果Map() {
        CompileResult r = compile("{\n" +
                "  \"rules\": [\n" +
                "    {\"conditions\": [], " +
                "     \"actions\": [{\"varCode\": \"rate\", \"varType\": \"DOUBLE\", \"value\": \"0.05\"}, {\"varCode\": \"fee\", \"varType\": \"DOUBLE\", \"value\": \"10\"}]},\n" +
                "    {\"conditions\": [], " +
                "     \"actions\": [{\"varCode\": \"rate\", \"varType\": \"DOUBLE\", \"value\": \"0.08\"}, {\"varCode\": \"fee\", \"varType\": \"DOUBLE\", \"value\": \"20\"}]}\n" +
                "  ]\n" +
                "}");
        assertTrue(r.isSuccess());
        String script = r.getCompiledScript();
        // hitPolicy 默认 FIRST：先 null 初始化（RuleScriptResultCollector），
        // 第一条规则匹配后块内赋值，返回 _result（QLExpress 返回最后表达式值）
        assertTrue("script: " + script, script.contains("rate = null"));
        assertTrue("script: " + script, script.contains("fee = null"));
        assertTrue("script: " + script, script.contains("rate = 0.05"));
        assertTrue("script: " + script, script.contains("fee = 10"));
        // 返回 _result 而非 return 关键字
        assertTrue("script: " + script, script.contains("_result"));
    }

    /** VarContext 解析 varId -> scriptName（需用 conditionRoot 格式，新格式才走 VarContext） */
    @Test
    public void testVarContext解析输入变量名() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(100L, "age");
        VarContext ctx = new VarContext(varIdMap);

        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditionRoot\": {\"type\":\"leaf\",\"_varId\":100,\"varCode\":\"ageInput\",\"operator\":\">=\",\"value\":\"18\"},\n" +
                "    \"actions\": []\n" +
                "  }]\n" +
                "}", ctx);
        assertTrue(r.isSuccess());
        String script = r.getCompiledScript();
        // conditionRoot 格式通过 compileLeaf 走 VarContext 解析
        assertTrue(script.contains("age"));
        assertFalse(script.contains("ageInput"));
    }

    /** VarContext 解析 outputVarCode */
    @Test
    public void testVarContext解析输出变量名() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(200L, "result");
        VarContext ctx = new VarContext(varIdMap);

        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditions\": [],\n" +
                "    \"actions\": [{\"_varId\": 200, \"varCode\": \"resultCode\", \"varType\": \"STRING\", \"value\": \"OK\"}]\n" +
                "  }]\n" +
                "}", ctx);
        assertTrue(r.isSuccess());
        assertTrue(r.getCompiledScript().contains("result"));
        assertFalse(r.getCompiledScript().contains("resultCode"));
    }

    /** VarContext 优先按 refType:id 精确解析，避免变量表和数据对象字段 ID 冲突 */
    @Test
    public void testVarContext按引用类型解析数据对象字段() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(32L, "plainVariable");
        Map<String, String> refIdMap = new LinkedHashMap<>();
        refIdMap.put("DATA_OBJECT:32", "request.params.taxpayerType");
        VarContext ctx = new VarContext(varIdMap, new LinkedHashMap<>(), refIdMap);

        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditionRoot\": {\"type\":\"leaf\",\"_varId\":32,\"_refType\":\"DATA_OBJECT\",\"varCode\":\"request.params.taxpayerType\",\"operator\":\"==\",\"value\":\"一般纳税人\"},\n" +
                "    \"actions\": []\n" +
                "  }]\n" +
                "}", ctx);

        assertTrue(r.isSuccess());
        String script = r.getCompiledScript();
        assertTrue(script.contains("request.params.taxpayerType"));
        assertFalse(script.contains("plainVariable"));
    }

    /** 规则内 conditionRoot 树结构 */
    @Test
    public void testConditionRoot树结构_正确编译() {
        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditionRoot\": {\"type\":\"group\",\"op\":\"AND\",\"children\":[\n" +
                "      {\"type\":\"leaf\",\"varCode\":\"income\",\"operator\":\">=\",\"value\":\"10000\"},\n" +
                "      {\"type\":\"leaf\",\"varCode\":\"score\",\"operator\":\">=\",\"value\":\"600\"}\n" +
                "    ]},\n" +
                "    \"actions\": []\n" +
                "  }]\n" +
                "}");
        assertTrue(r.isSuccess());
        String script = r.getCompiledScript();
        assertTrue(script.contains("income"));
        assertTrue(script.contains("score"));
        assertTrue(script.contains(" && "));
    }

    /** 操作符 == 字符串值加引号 */
    @Test
    public void test字符串比较_加引号() {
        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditions\": [{\"varCode\": \"status\", \"operator\": \"==\", \"value\": \"ACTIVE\"}],\n" +
                "    \"actions\": []\n" +
                "  }]\n" +
                "}");
        assertTrue(r.isSuccess());
        assertTrue(r.getCompiledScript().contains("\"ACTIVE\""));
    }

    /** 操作符为 * 时跳过（恒真条件，仅在 conditionRoot 格式生效） */
    @Test
    public void test通配符操作符_生成true() {
        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditionRoot\": {\"type\":\"leaf\",\"varCode\":\"x\",\"operator\":\"*\",\"value\":\"anything\"},\n" +
                "    \"actions\": []\n" +
                "  }]\n" +
                "}");
        assertTrue(r.isSuccess());
        assertTrue(r.getCompiledScript().contains("true"));
    }

    /** 无效 JSON 返回失败 */
    @Test
    public void test无效JSON_返回失败() {
        CompileResult r = compile("not a json");
        assertFalse(r.isSuccess());
        assertNotNull(r.getErrorMessage());
    }

    /** collectOutputVarCodes 静态方法测试 */
    @Test
    public void testCollectOutputVarCodes_多规则汇总() {
        com.alibaba.fastjson.JSONArray rules = new com.alibaba.fastjson.JSONArray();
        com.alibaba.fastjson.JSONArray actions = new com.alibaba.fastjson.JSONArray();
        actions.add(com.alibaba.fastjson.JSON.parseObject("{\"varCode\":\"tax\",\"varType\":\"DOUBLE\"}"));

        // rule1: 全局 actions 列引用，无行内 values 或 actions → 不贡献输出变量
        com.alibaba.fastjson.JSONObject rule1 = new com.alibaba.fastjson.JSONObject();
        rule1.put("values", new com.alibaba.fastjson.JSONArray());
        rules.add(rule1);

        // rule2: 行内 actions 有 varCode → 贡献 fee
        com.alibaba.fastjson.JSONObject rule2 = new com.alibaba.fastjson.JSONObject();
        rule2.put("actions", com.alibaba.fastjson.JSON.parseArray("[{\"varCode\":\"fee\",\"varType\":\"DOUBLE\"}]"));
        rules.add(rule2);

        LinkedHashSet<String> result = DecisionTableCompiler.collectOutputVarCodes(rules, actions, null);
        // rule1 的 values 是空数组（isEmpty=true），不贡献变量；rule2 的 actions 贡献 fee
        assertEquals(1, result.size());
        assertTrue(result.contains("fee"));
    }

    /** 空 modelJson 返回失败 */
    @Test
    public void testNull模型_返回失败() {
        CompileResult r = compile(null);
        assertFalse(r.isSuccess());
    }

    /** 空字符串模型返回失败 */
    @Test
    public void test空字符串模型_返回失败() {
        CompileResult r = compile("");
        assertFalse(r.isSuccess());
    }

    /** scriptType 为 QLEXPRESS */
    @Test
    public void test成功时scriptType为QLEXPRESS() {
        CompileResult r = compile("{\"rules\":[{\"conditions\":[],\"actions\":[]}]}");
        assertTrue(r.isSuccess());
        assertEquals("QLEXPRESS", r.getCompiledType());
    }

    // ========== conditionRoot leaf 类型（buildRulePredicate 支持）==========

    /** conditionRoot 为单个 leaf 节点，生成简单条件 */
    @Test
    public void testConditionRoot单个叶节点_生成条件() {
        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditionRoot\": {\"type\":\"leaf\",\"varCode\":\"age\",\"operator\":\">=\",\"value\":\"18\"},\n" +
                "    \"actions\": []\n" +
                "  }]\n" +
                "}");
        assertTrue(r.isSuccess());
        String script = r.getCompiledScript();
        assertTrue(script.contains("age"));
        assertTrue(script.contains("if ("));
    }

    /** conditionRoot leaf 含 _varId，通过 VarContext 解析为 scriptName */
    @Test
    public void testConditionRoot叶节点带varId_通过VarContext解析() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(500L, "customerAge");
        VarContext ctx = new VarContext(varIdMap);

        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditionRoot\": {\"type\":\"leaf\",\"_varId\":500,\"varCode\":\"ageTmp\",\"operator\":\">=\",\"value\":\"18\"},\n" +
                "    \"actions\": []\n" +
                "  }]\n" +
                "}", ctx);
        assertTrue(r.isSuccess());
        String script = r.getCompiledScript();
        // 应使用 VarContext 解析后的 customerAge，而非原始 varCode
        assertTrue(script.contains("customerAge"));
        assertFalse(script.contains("ageTmp"));
    }

    /** conditionRoot leaf 的 operator=* 生成 true（恒真跳过） */
    @Test
    public void testConditionRoot叶节点操作符通配_生成true() {
        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditionRoot\": {\"type\":\"leaf\",\"varCode\":\"x\",\"operator\":\"*\",\"value\":\"any\"},\n" +
                "    \"actions\": []\n" +
                "  }]\n" +
                "}");
        assertTrue(r.isSuccess());
        assertTrue(r.getCompiledScript().contains("true"));
    }

    /** conditionRoot leaf valueKind=VAR（变量比较），右侧也是变量 */
    @Test
    public void testConditionRoot叶节点变量比较_valueKind为VAR() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(600L, "maxAge");
        varIdMap.put(601L, "customerAge");
        VarContext ctx = new VarContext(varIdMap);

        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditionRoot\": {\"type\":\"leaf\",\"_varId\":601,\"varCode\":\"ageIn\",\"operator\":\">=\",\"valueKind\":\"VAR\",\"value\":\"ageThreshold\"},\n" +
                "    \"actions\": []\n" +
                "  }]\n" +
                "}", ctx);
        assertTrue(r.isSuccess());
        String script = r.getCompiledScript();
        // 左侧用 VarContext 解析为 customerAge，右侧保持原始 value
        assertTrue(script.contains("customerAge"));
        assertTrue(script.contains("ageThreshold"));
    }

    /** conditionRoot 嵌套 group（OR）生成正确括号与 OR */
    @Test
    public void testConditionRoot嵌套OR组_生成正确表达式() {
        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditionRoot\": {\"type\":\"group\",\"op\":\"OR\",\"children\":[\n" +
                "      {\"type\":\"leaf\",\"varCode\":\"score\",\"operator\":\">=\",\"value\":\"90\"},\n" +
                "      {\"type\":\"leaf\",\"varCode\":\"level\",\"operator\":\"==\",\"value\":\"VIP\"}\n" +
                "    ]},\n" +
                "    \"actions\": []\n" +
                "  }]\n" +
                "}");
        assertTrue(r.isSuccess());
        String script = r.getCompiledScript();
        assertTrue(script.contains(" || "));
        assertFalse(script.contains(" && "));
    }

    /** conditionRoot 空 children 生成 true */
    @Test
    public void testConditionRoot空组_生成true() {
        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditionRoot\": {\"type\":\"group\",\"op\":\"AND\",\"children\":[]},\n" +
                "    \"actions\": []\n" +
                "  }]\n" +
                "}");
        assertTrue(r.isSuccess());
        assertTrue(r.getCompiledScript().contains("true"));
    }

    /** conditionRoot 为 null 时回退到旧版 conditions 格式 */
    @Test
    public void testConditionRoot为null_回退旧版条件() {
        CompileResult r = compile("{\n" +
                "  \"conditions\": [{\"varCode\":\"amount\",\"varType\":\"NUMBER\"}],\n" +
                "  \"rules\": [{\n" +
                "    \"conditionRoot\": null,\n" +
                "    \"conditions\": [{\"operator\":\">\",\"value\":\"10000\"}],\n" +
                "    \"actions\": []\n" +
                "  }]\n" +
                "}");
        assertTrue(r.isSuccess());
        assertTrue(r.getCompiledScript().contains("amount"));
        assertTrue(r.getCompiledScript().contains("10000"));
    }

    // ========== 动作列 _varId 持久化（outputVarCodes + appendRuleAssignments）==========

    /** 动作带 _varId，通过 VarContext 解析输出变量名 */
    @Test
    public void test动作带varId_通过VarContext解析输出() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(700L, "taxAmount");
        VarContext ctx = new VarContext(varIdMap);

        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditions\": [],\n" +
                "    \"actions\": [{\"_varId\":700,\"varCode\":\"taxTmp\",\"varType\":\"DOUBLE\",\"value\":\"1500.0\"}]\n" +
                "  }]\n" +
                "}", ctx);
        assertTrue(r.isSuccess());
        String script = r.getCompiledScript();
        assertTrue(script.contains("taxAmount = 1500.0"));
        assertFalse(script.contains("taxTmp"));
    }

    /** 多条规则内各自带 _varId 的动作，输出变量汇总正确 */
    @Test
    public void test多规则动作varId_输出变量汇总正确() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(800L, "rate");
        varIdMap.put(801L, "discount");
        VarContext ctx = new VarContext(varIdMap);

        CompileResult r = compile("{\n" +
                "  \"rules\": [\n" +
                "    {\"conditions\": [], \"actions\": [{\"_varId\":800,\"varCode\":\"r1\",\"varType\":\"DOUBLE\",\"value\":\"0.05\"}]},\n" +
                "    {\"conditions\": [], \"actions\": [{\"_varId\":801,\"varCode\":\"r2\",\"varType\":\"DOUBLE\",\"value\":\"0.1\"}]}\n" +
                "  ]\n" +
                "}", ctx);
        assertTrue(r.isSuccess());
        String script = r.getCompiledScript();
        assertTrue(script.contains("rate = 0.05"));
        assertTrue(script.contains("discount = 0.1"));
    }

    /** 全局 actions 列含 _varId，行内 actions 无 varCode 时回退到全局定义 */
    @Test
    public void test全局动作列varId_行内回退全局定义() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(900L, "finalScore");
        VarContext ctx = new VarContext(varIdMap);

        CompileResult r = compile("{\n" +
                "  \"actions\": [{\"_varId\":900,\"varCode\":\"fsTmp\",\"varType\":\"NUMBER\"}],\n" +
                "  \"rules\": [\n" +
                "    {\"conditions\": [], \"actions\": [{\"value\":\"100\"}]}\n" +
                "  ]\n" +
                "}", ctx);
        assertTrue(r.isSuccess());
        String script = r.getCompiledScript();
        assertTrue(script.contains("finalScore = 100"));
        assertFalse(script.contains("fsTmp"));
    }

    /** 动作 varCode 为空时跳过（不生成赋值语句） */
    @Test
    public void test动作varCode为空_跳过赋值() {
        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditions\": [],\n" +
                "    \"actions\": [{\"varCode\":\"\",\"varType\":\"STRING\",\"value\":\"test\"}]\n" +
                "  }]\n" +
                "}");
        assertTrue(r.isSuccess());
        // 空 varCode 的动作不生成任何赋值
        assertFalse(r.getCompiledScript().contains("= \"test\""));
    }

    /**
     * 动作 value 缺失时跳过规则体内的赋值。
     * 注意：outputVarCodes 会为 result 生成顶层 `result = null`（初始化），
     * 但规则 if 块内不应出现 `result = "xxx"` 或 `result = xxx`。
     */
    @Test
    public void test动作value为空_跳过规则内赋值() {
        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditions\": [],\n" +
                "    \"actions\": [{\"varCode\":\"result\",\"varType\":\"STRING\"}]\n" +
                "  }]\n" +
                "}");
        assertTrue(r.isSuccess());
        String script = r.getCompiledScript();
        // output 初始化生成 result = null 是正常的（输出预初始化）
        // 但 if 块内不应出现 result = ... 赋值（value 缺失应跳过）
        // if 块内是 { 后到 } 之间的内容
        int ifBlockStart = script.indexOf("{");
        int ifBlockEnd = script.indexOf("}");
        if (ifBlockStart >= 0 && ifBlockEnd >= 0) {
            String ruleBody = script.substring(ifBlockStart, ifBlockEnd);
            assertFalse("规则体内不应生成赋值（value 缺失）", ruleBody.contains("result ="));
        }
    }

    /** 字符串类型动作值含引号时正确转义 */
    @Test
    public void test字符串动作值含引号_正确转义() {
        CompileResult r = compile("{\n" +
                "  \"rules\": [{\n" +
                "    \"conditions\": [],\n" +
                "    \"actions\": [{\"varCode\":\"msg\",\"varType\":\"STRING\",\"value\":\"他说：\\\"你好\\\"\"}]\n" +
                "  }]\n" +
                "}");
        assertTrue(r.isSuccess());
        String script = r.getCompiledScript();
        assertTrue(script.contains("msg = \"他说：\\\"你好\\\"\""));
    }

    // ========== 混合场景：conditionRoot + 每规则独立动作 ==========

    /** conditionRoot + 每规则独立动作，完整编译链 */
    @Test
    public void testConditionRoot加每规则独立动作_完整编译() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(100L, "age");
        varIdMap.put(200L, "result");
        VarContext ctx = new VarContext(varIdMap);

        CompileResult r = compile("{\n" +
                "  \"hitPolicy\": \"FIRST\",\n" +
                "  \"rules\": [\n" +
                "    {\n" +
                "      \"conditionRoot\": {\"type\":\"group\",\"op\":\"AND\",\"children\":[\n" +
                "        {\"type\":\"leaf\",\"_varId\":100,\"varCode\":\"ageTmp\",\"operator\":\">=\",\"value\":\"18\"},\n" +
                "        {\"type\":\"leaf\",\"varCode\":\"income\",\"operator\":\">=\",\"value\":\"5000\"}\n" +
                "      ]},\n" +
                "      \"actions\": [{\"_varId\":200,\"varCode\":\"resultTmp\",\"varType\":\"STRING\",\"value\":\"PASS\"}]\n" +
                "    }\n" +
                "  ]\n" +
                "}", ctx);
        assertTrue(r.isSuccess());
        String script = r.getCompiledScript();
        // age 通过 VarContext 解析为 age，result 通过 VarContext 解析
        assertTrue(script.contains("age"));
        assertTrue(script.contains("result"));
        assertTrue(script.contains("PASS"));
        assertTrue(script.contains(" && "));
    }

    @Test
    public void testHitPolicyUnique_多条命中时执行失败() {
        CompileResult r = compile("{\n" +
                "  \"hitPolicy\": \"UNIQUE\",\n" +
                "  \"rules\": [\n" +
                "    {\"conditionRoot\": {\"type\":\"leaf\",\"varCode\":\"score\",\"varType\":\"NUMBER\",\"operator\":\">=\",\"value\":\"60\"}, \"actions\": [{\"varCode\":\"decision\",\"varType\":\"STRING\",\"value\":\"REVIEW\"}]},\n" +
                "    {\"conditionRoot\": {\"type\":\"leaf\",\"varCode\":\"score\",\"varType\":\"NUMBER\",\"operator\":\">=\",\"value\":\"80\"}, \"actions\": [{\"varCode\":\"decision\",\"varType\":\"STRING\",\"value\":\"PASS\"}]}\n" +
                "  ]\n" +
                "}");
        assertTrue(r.isSuccess());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("score", 90);

        RuleResult result = engine.execute(r.getCompiledScript(), params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("UNIQUE"));
    }

    @Test
    public void testHitPolicyUnique_单条命中时返回结果() {
        CompileResult r = compile("{\n" +
                "  \"hitPolicy\": \"UNIQUE\",\n" +
                "  \"rules\": [\n" +
                "    {\"conditionRoot\": {\"type\":\"leaf\",\"varCode\":\"score\",\"varType\":\"NUMBER\",\"operator\":\">=\",\"value\":\"90\"}, \"actions\": [{\"varCode\":\"decision\",\"varType\":\"STRING\",\"value\":\"PASS\"}]},\n" +
                "    {\"conditionRoot\": {\"type\":\"leaf\",\"varCode\":\"score\",\"varType\":\"NUMBER\",\"operator\":\"<\",\"value\":\"60\"}, \"actions\": [{\"varCode\":\"decision\",\"varType\":\"STRING\",\"value\":\"REJECT\"}]}\n" +
                "  ]\n" +
                "}");
        assertTrue(r.isSuccess());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("score", 95);

        RuleResult result = engine.execute(r.getCompiledScript(), params);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getResult() instanceof Map);
        Map<?, ?> resultMap = (Map<?, ?>) result.getResult();
        assertEquals("PASS", resultMap.get("decision"));
    }
}
