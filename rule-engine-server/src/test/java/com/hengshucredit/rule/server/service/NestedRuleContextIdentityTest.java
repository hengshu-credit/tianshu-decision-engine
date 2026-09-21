package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class NestedRuleContextIdentityTest {
    @Test
    public void parentBranchSeesChildHitsImmediatelyWithAndWithoutTrace() {
        for (boolean trace : new boolean[]{false, true}) {
            RuleRuntimeInvoker invoker = new RuleRuntimeInvoker();
            QLExpressEngine engine = new QLExpressEngine();
            RuleDefinition definition = new RuleDefinition();
            definition.setId(2L); definition.setRuleCode("CHILD"); definition.setProjectId(0L); definition.setModelType("SCRIPT");
            RuleDefinitionInputField field = new RuleDefinitionInputField();
            field.setScriptName("request.amount"); field.setFieldType("NUMBER");
            ReflectionTestUtils.setField(invoker, "definitionService", new RuleDefinitionService() {
                @Override public RuleDefinition getById(Serializable id) { return definition; }
                @Override public List<RuleDefinitionInputField> listInputFields(Long id) { return List.of(field); }
            });
            ReflectionTestUtils.setField(invoker, "projectService", new RuleProjectService() {
                @Override public RuleProject getById(Serializable id) { return null; }
            });
            ReflectionTestUtils.setField(invoker, "variableSourceResolver", new VariableSourceResolver() {
                @Override public Map<String, Object> resolveInto(Long project, Map<String, Object> values, VariableResolveOptions options) { return values; }
            });
            ReflectionTestUtils.setField(invoker, "executionParameterBinder", new ExecutionParameterBinder());
            ReflectionTestUtils.setField(invoker, "qlExpressEngine", engine);
            RulePublished published = new RulePublished();
            published.setDefinitionId(2L); published.setRuleCode("CHILD"); published.setVersion(1); published.setStatus(1);
            published.setCompiledScript("result.hits = [\"RB0001\"]; setRuntimeValue(\"result.hits\", result.hits); return result.hits;");
            ReflectionTestUtils.setField(invoker, "publishedMapper", Proxy.newProxyInstance(
                    RulePublishedMapper.class.getClassLoader(), new Class<?>[]{RulePublishedMapper.class},
                    (proxy, method, args) -> published));
            invoker.register(engine.getRunner());
            Map<String, Object> request = new LinkedHashMap<>(Map.of("amount", "12.5"));
            Map<String, Object> values = new LinkedHashMap<>(Map.of("request", request));
            invoker.enter("ROOT", 0L, null, values, true);
            try {
                RuleResult result = engine.execute("result = jsonParse(\"{}\"); setRuntimeValue(\"result\", result);\n"
                        + "result.hits = []; setRuntimeValue(\"result.hits\", result.hits);\n"
                        + "executeRuleById(\"2\");\n"
                        + "if (isBlank(result.hits)) { return \"PASS\"; }\nreturn \"REJECT\";", values, trace);
                assertTrue(result.getErrorMessage(), result.isSuccess());
                assertEquals("REJECT", result.getResult());
                assertSame(request, values.get("request"));
                assertEquals(12.5, ((Number) request.get("amount")).doubleValue(), 0.0);
            } finally { invoker.exit(); }
        }
    }
}
