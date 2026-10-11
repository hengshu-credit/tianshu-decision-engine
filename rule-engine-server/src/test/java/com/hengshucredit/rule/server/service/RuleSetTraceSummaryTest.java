package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import org.junit.Test;

import static org.junit.Assert.*;

public class RuleSetTraceSummaryTest {
    @Test public void extractsOnlyEvaluatedItemsAndPreservesNestedFrameIdentity() {
        var summary = RuleSetTraceSummary.fromTrace("""
            [{"modelType":"FLOW","inputParams":{"mustNotCopy":"payload"},"children":[
              {"ruleId":9007199254740993,"ruleCode":"Same_Code","ruleName":"准入","modelType":"RULE_SET","status":"SUCCESS","durationMs":123,
               "events":[{"type":"RULE_SET_ITEM","ruleCode":"A","ruleName":"条件A","evaluated":true,"hit":true},
                         {"type":"RULE_SET_ITEM","ruleCode":"B","evaluated":false,"hit":false},
                         {"type":"RULE_SET_SUMMARY","evaluated":true,"hit":false}],
               "children":[{"ruleId":12,"ruleCode":"child","modelType":"RULE_SET","status":"FAILED","durationMs":-1,
               "events":[{"type":"RULE_SET_ITEM","ruleCode":"C","evaluated":true,"hit":true}]}]},
              {"ruleId":13,"modelType":"RULE_SET","events":[{"type":"RULE_SET_ITEM","evaluated":false}]}
            ]}]
            """);
        assertEquals("READY", summary.status());
        JSONArray frames = JSON.parseArray(summary.json());
        assertEquals(2, frames.size());
        assertEquals(9007199254740993L, frames.getJSONObject(0).getLongValue("ruleId"));
        assertEquals("Same_Code", frames.getJSONObject(0).getString("ruleCode"));
        assertEquals(0, frames.getJSONObject(0).getIntValue("hit"));
        assertEquals(1, frames.getJSONObject(0).getJSONArray("items").size());
        assertEquals("A", frames.getJSONObject(0).getJSONArray("items").getJSONObject(0).getString("ruleCode"));
        assertEquals(1, frames.getJSONObject(1).getIntValue("hit"));
        assertEquals(1, frames.getJSONObject(1).getIntValue("failed"));
        assertEquals(0, frames.getJSONObject(1).getLongValue("cost"));
        assertFalse(summary.json().contains("mustNotCopy"));
    }

    @Test public void missingCorruptAndTruncatedTracesRemainDistinguishable() {
        assertEquals("NO_TRACE", RuleSetTraceSummary.fromTrace(null).status());
        assertEquals("NO_TRACE", RuleSetTraceSummary.fromTrace(" ").status());
        assertEquals("INVALID", RuleSetTraceSummary.fromTrace("{oops").status());
        assertEquals("INVALID", RuleSetTraceSummary.fromTrace("null").status());
        assertEquals("TRUNCATED", RuleSetTraceSummary.fromTrace("{\"truncated\":true,\"originalBytes\":10000}").status());
        assertEquals("READY", RuleSetTraceSummary.fromTrace("[]").status());
        assertEquals("[]", RuleSetTraceSummary.fromTrace("{oops").json());
    }

    @Test public void structuralRulesKeepSeparateKeysEvenWhenNamesMatch() {
        var summary = RuleSetTraceSummary.fromTrace("""
            [{"ruleId":1,"ruleCode":"same","modelType":"RULE_SET","events":[{"type":"RULE_SET_SUMMARY","evaluated":true}]},
             {"ruleId":2,"ruleCode":"same","modelType":"RULE_SET","events":[{"type":"RULE_SET_SUMMARY","evaluated":true}]},
             {"ruleCode":"Old_Code","modelType":"RULE_SET","events":[{"type":"RULE_SET_SUMMARY","evaluated":true}]},
             {"ruleCode":"old_code","modelType":"RULE_SET","events":[{"type":"RULE_SET_SUMMARY","evaluated":true}]}]
            """);
        JSONArray frames = JSON.parseArray(summary.json());
        assertEquals(4, frames.stream().map(value -> ((com.alibaba.fastjson.JSONObject)value).getString("key")).distinct().count());
    }
}
