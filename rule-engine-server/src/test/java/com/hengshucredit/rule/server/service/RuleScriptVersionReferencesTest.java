package com.hengshucredit.rule.server.service;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class RuleScriptVersionReferencesTest {
    @Test public void literalIdCallsAreCollectedButCommentsAndStringsAreNotCalls() {
        JSONObject model = new JSONObject();
        model.put("script", "// executeRuleById('99')\n text = \"executeRuleById('98')\"; executeRuleVersionById('22', '81'); executeRuleById(dynamicId);");
        var references = OperandDependencyCollector.collectReferences(model);
        assertEquals(1, references.size());
        assertEquals(Long.valueOf(22), references.get(0).getRefId());
        assertEquals("FIXED", references.get(0).getVersionMode());
        assertEquals(Long.valueOf(81), references.get(0).getVersionBindingId());
    }
}
