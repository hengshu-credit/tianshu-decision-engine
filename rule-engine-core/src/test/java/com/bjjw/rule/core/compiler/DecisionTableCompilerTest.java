package com.bjjw.rule.core.compiler;

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

    @Before
    public void setUp() {
        compiler = new DecisionTableCompiler();
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
}
