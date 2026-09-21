package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class GraphDraftConditionTest {
    private JSONObject model() {
        return JSON.parseObject("""
                {"nodes":[{"id":"s","type":"start"},{"id":"d","type":"decision"},
                  {"id":"hit","type":"end"},{"id":"miss","type":"end"}],
                 "edges":[{"source":"s","target":"d"},
                  {"source":"d","target":"hit","conditionExpression":"(true)",
                   "conditionConfig":{"type":"group","op":"AND","children":[
                    {"type":"leaf","leftOperand":null,"operator":"==","rightOperand":null}]}},
                  {"source":"d","target":"miss"}]}
                """);
    }

    @Test
    public void halfConfiguredConditionCannotBecomeAnAlwaysTrueOrDefaultBranch() {
        for (RuleCompiler compiler : new RuleCompiler[]{new DecisionFlowCompiler(), new DecisionTreeCompiler()}) {
            CompileResult result = compiler.compile(model().toJSONString());
            assertFalse("未完成条件不得编译成放行分支", result.isSuccess());
            assertTrue(result.getErrorMessage().contains("条件") || result.getErrorMessage().contains("参数"));
        }
    }

    @Test
    public void emptyGroupIsStillDefaultButPartiallySelectedOperandsAreNot() {
        JSONObject model = model();
        JSONObject edge = model.getJSONArray("edges").getJSONObject(1);
        JSONObject group = edge.getJSONObject("conditionConfig");
        group.getJSONArray("children").clear();
        edge.put("conditionExpression", "");
        model.getJSONArray("edges").getJSONObject(2).put("conditionExpression", "age > 22");
        for (RuleCompiler compiler : new RuleCompiler[]{new DecisionFlowCompiler(), new DecisionTreeCompiler()}) {
            assertTrue(compiler.compile(model.toJSONString()).isSuccess());
        }
        group.getJSONArray("children").add(JSON.parseObject("""
                {"type":"leaf","operator":">","leftOperand":{"kind":"PATH","value":"age"},"rightOperand":null}
                """));
        for (RuleCompiler compiler : new RuleCompiler[]{new DecisionFlowCompiler(), new DecisionTreeCompiler()}) {
            assertFalse(compiler.compile(model.toJSONString()).isSuccess());
        }
    }
}
