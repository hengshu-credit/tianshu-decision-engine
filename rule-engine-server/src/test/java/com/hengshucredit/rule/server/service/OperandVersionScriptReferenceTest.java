package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class OperandVersionScriptReferenceTest {
    @Test public void scriptVersionCallsRetainNamespaceAndNestedVersionedArguments() {
        var root = new JSONObject(); root.put("script", "return invokeFunctionVersionById('13', 'FUNCTION_HISTORY:92', resourceVersionValue('VARIABLE','7','BUSINESS_VERSION:91','x')) && resourceVersionStatus('EXTERNAL_API','8','93','CACHE_STATE','HIT');");
        var refs = OperandDependencyCollector.collectReferences(root);
        assertEquals(List.of("FUNCTION", "VARIABLE", "EXTERNAL_API"), refs.stream().map(OperandDependencyCollector.Reference::getRefType).toList());
        assertEquals(List.of(92L, 91L, 93L), refs.stream().map(OperandDependencyCollector.Reference::getVersionId).toList());
        assertEquals("FUNCTION_HISTORY", refs.get(0).getVersionSource()); assertEquals("BUSINESS_VERSION", refs.get(1).getVersionSource()); assertNull(refs.get(2).getVersionSource());
    }

    @Test public void commentsAndBusinessStringsDoNotInventDependencies() {
        var root = new JSONObject(); root.put("script", "// resourceVersionValue('VARIABLE','7','9','')\nreturn \"resourceVersionValue('VARIABLE','7','9','')\";");
        assertTrue(OperandDependencyCollector.collectReferences(root).isEmpty());
    }
}
