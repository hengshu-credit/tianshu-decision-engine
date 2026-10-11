package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import org.junit.Test;
import java.util.LinkedHashSet;
import static org.junit.Assert.*;

public class RuleSideEffectReferencesTest {
    @Test public void explicitApiExposesItsRequestDependenciesWithoutAnApiVariable() {
        var analyzer = new RuleFieldAnalyzer();
        org.springframework.test.util.ReflectionTestUtils.setField(analyzer, "variableSourceResolver", new VariableSourceResolver() {
            @Override java.util.Set<String> collectVariableDependencies(com.hengshucredit.rule.model.entity.RuleVariable source) {
                assertEquals("API", source.getVarSource());
                assertEquals(Long.valueOf(12), JSON.parseObject(source.getSourceConfig()).getLong("apiConfigId"));
                assertEquals(Long.valueOf(3), source.getProjectId());
                return java.util.Set.of("mobile");
            }
        });
        var fields = analyzer.resolveDirectApiInputFields("{\"type\":\"api-call\",\"apiId\":12}", 3L, java.util.Map.of());
        assertEquals(1, fields.size());
        assertEquals("mobile", fields.get(0).getScriptName());
    }
    @Test public void externalResultsAreDependenciesButNotCallerInputs() {
        var operand = JSON.parseObject("{\"kind\":\"REFERENCE\",\"refType\":\"EXTERNAL_API\",\"refId\":12,\"code\":\"provider.status.outcome\",\"relativePath\":\"status.outcome\"}");
        var paths = new LinkedHashSet<String>();
        OperandDependencyCollector.collect(operand, paths);
        assertTrue(paths.isEmpty());
        var references = OperandDependencyCollector.collectReferences(operand);
        assertEquals("EXTERNAL_API", references.get(0).getRefType());
        assertEquals(Long.valueOf(12), references.get(0).getRefId());
    }
    @Test public void actionResourcesAndContentAreCollectedByStableIds() {
        var model = JSON.parse("{\"actions\":[{\"type\":\"api-call\",\"apiId\":12},{\"type\":\"list-write\",\"listIds\":[8,9],\"entries\":[{\"itemType\":\"MOBILE\",\"valueOperand\":{\"kind\":\"REFERENCE\",\"refId\":5,\"refType\":\"VARIABLE\",\"code\":\"mobile\"}}]}]}");
        var references = OperandDependencyCollector.collectReferences(model);
        var ids = references.stream().map(ref -> ref.getRefType() + ":" + ref.getRefId()).collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of("EXTERNAL_API:12", "LIST_LIBRARY:8", "LIST_LIBRARY:9", "VARIABLE:5"), ids);
    }
}
