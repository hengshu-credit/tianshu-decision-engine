package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class RuleCompileServiceTest {

    @Test
    public void previewCompilationRejectsBlankModelJson() {
        RuleCompileService service = new RuleCompileService();
        ReflectionTestUtils.setField(service, "definitionService", new RuleDefinitionService() {
            @Override
            public RuleDefinition getById(Serializable id) {
                RuleDefinition definition = new RuleDefinition();
                definition.setId(10L);
                definition.setProjectId(1L);
                definition.setModelType("SCRIPT");
                return definition;
            }
        });

        CompileResult result = service.compilePreview(10L, "  ", "SCRIPT");

        assertFalse(result.isSuccess());
    }

    @Test
    public void expressionCompilationUsesStableReferencesWithoutReadingOrUpdatingRuleContent() {
        RuleCompileService service = expressionService(true);
        Map<String, Object> operand = operation(
                reference(9L, "VARIABLE", "age"),
                "+",
                function(31L, "oldProjectRisk", reference(10L, "VARIABLE", "score")));

        CompileResult result = service.compileExpression(7L, operand);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals("(current_age + projectRisk(current_score))", result.getCompiledScript());
        assertEquals("QLEXPRESS", result.getCompiledType());
    }

    @Test
    public void expressionCompilationRejectsMissingOrStaleManagedReferences() {
        RuleCompileService service = expressionService(true);

        assertFalse(service.compileExpression(7L, Collections.<String, Object>emptyMap()).isSuccess());
        assertFalse(service.compileExpression(7L, reference(999L, "VARIABLE", "age")).isSuccess());
        assertFalse(service.compileExpression(7L, function(999L, "missing", reference(9L, "VARIABLE", "age"))).isSuccess());
        assertFalse(service.compileExpression(7L, function(31L, "projectRisk",
                reference(9L, "VARIABLE", "age"), reference(10L, "VARIABLE", "score"))).isSuccess());
        assertFalse(expressionService(false).compileExpression(7L, reference(9L, "VARIABLE", "age")).isSuccess());
    }

    private RuleCompileService expressionService(final boolean definitionExists) {
        RuleCompileService service = new RuleCompileService();
        ReflectionTestUtils.setField(service, "definitionService", new RuleDefinitionService() {
            @Override
            public RuleDefinition getById(Serializable id) {
                if (!definitionExists) return null;
                RuleDefinition definition = new RuleDefinition();
                definition.setId(7L);
                definition.setProjectId(1L);
                return definition;
            }

            @Override
            public com.hengshucredit.rule.model.entity.RuleDefinitionContent getContent(Long definitionId) {
                throw new AssertionError("表达式编译不应读取或修改规则内容");
            }
        });
        ReflectionTestUtils.setField(service, "variableService", new RuleVariableService() {
            @Override
            public Map<Long, String> buildVarIdScriptNameMap(Long projectId) {
                Map<Long, String> values = new LinkedHashMap<>();
                values.put(9L, "current_age");
                values.put(10L, "current_score");
                return values;
            }

            @Override
            public Map<String, String> buildVarCodeScriptNameMap(Long projectId) {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, String> buildRefScriptNameMap(Long projectId) {
                Map<String, String> values = new LinkedHashMap<>();
                values.put("VARIABLE:9", "current_age");
                values.put("VARIABLE:10", "current_score");
                return values;
            }

            @Override
            public Map<Long, String> buildRefConstantExpressionMap(Long projectId) {
                return Collections.emptyMap();
            }
        });
        ReflectionTestUtils.setField(service, "functionService", new RuleFunctionService() {
            @Override
            public Map<Long, String> buildFunctionCodeMap(Long projectId) {
                return Collections.singletonMap(31L, "projectRisk");
            }

            @Override
            public Map<Long, Integer> buildFunctionArityMap(Long projectId) {
                return Collections.singletonMap(31L, 1);
            }
        });
        return service;
    }

    private Map<String, Object> reference(Long id, String refType, String code) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("kind", "REFERENCE");
        value.put("refId", id);
        value.put("refType", refType);
        value.put("code", code);
        return value;
    }

    private Map<String, Object> function(Long id, String code, Map<String, Object>... args) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("kind", "FUNCTION");
        value.put("functionId", id);
        value.put("functionCode", code);
        value.put("args", Arrays.asList(args));
        return value;
    }

    private Map<String, Object> operation(Map<String, Object> left, String operator, Map<String, Object> right) {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("operand", left);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("operator", operator);
        second.put("operand", right);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("kind", "OPERATION");
        value.put("terms", Arrays.asList(first, second));
        return value;
    }
}
