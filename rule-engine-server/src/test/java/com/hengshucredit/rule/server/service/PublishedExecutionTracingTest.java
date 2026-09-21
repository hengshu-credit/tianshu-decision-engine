package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RequestContext;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class PublishedExecutionTracingTest {
    @Test
    public void traceChoicePropagatesToChildrenWithoutChangingResultsLogsOrBilling() {
        RuleExecuteService service = new RuleExecuteService();
        RuleRuntimeInvoker invoker = new RuleRuntimeInvoker();
        List<Boolean> traceChoices = new ArrayList<>();
        QLExpressEngine engine = new QLExpressEngine() {
            @Override
            public RuleResult execute(PreparedScript script, Object context, boolean trace, RequestContext request) {
                traceChoices.add(trace);
                return super.execute(script, context, trace, request);
            }
        };
        RuleDefinitionService definitions = new RuleDefinitionService() {
            @Override public RuleDefinition getById(Serializable id) {
                RuleDefinition definition = new RuleDefinition();
                definition.setId((Long) id);
                definition.setProjectId(1L);
                definition.setScope("PROJECT");
                definition.setModelType("SCRIPT");
                definition.setRuleCode(Long.valueOf(1L).equals(id) ? "ROOT" : "CHILD");
                return definition;
            }
            @Override public List<RuleDefinitionInputField> listInputFields(Long id) { return List.of(); }
            @Override public List<RuleDefinitionOutputField> listOutputFields(Long id) { return List.of(); }
        };
        RuleProjectService projects = new RuleProjectService() {
            @Override public RuleProject getById(Serializable id) { return null; }
        };
        VariableSourceResolver resolver = new VariableSourceResolver() {
            @Override public Map<String, Object> resolveInto(Long id, Map<String, Object> values, VariableResolveOptions options) {
                return values;
            }
        };
        for (Object target : List.of(service, invoker)) {
            ReflectionTestUtils.setField(target, "definitionService", definitions);
            ReflectionTestUtils.setField(target, "projectService", projects);
            ReflectionTestUtils.setField(target, "qlExpressEngine", engine);
            ReflectionTestUtils.setField(target, "variableSourceResolver", resolver);
            ReflectionTestUtils.setField(target, "executionParameterBinder", new ExecutionParameterBinder());
        }
        List<RuleExecutionLog> logs = new ArrayList<>();
        List<Boolean> billings = new ArrayList<>();
        ReflectionTestUtils.setField(service, "runtimeRuleInvoker", invoker);
        ReflectionTestUtils.setField(service, "functionService", new RuleFunctionService() {
            @Override public List<RuleFunction> listByProject(Long id) { return List.of(); }
        });
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(service, "logService", new RuleExecutionLogService() {
            @Override public boolean save(RuleExecutionLog value) { logs.add(value); return true; }
        });
        ReflectionTestUtils.setField(service, "billingService", new RuleBillingService() {
            @Override public void recordEngineExecution(RuleDefinition definition, boolean success, Long time,
                                                         String error, ProjectAuthContext auth) { billings.add(success); }
        });
        RulePublished child = published(2L, "CHILD", "return age >= 18 ? \"PASS\" : \"REJECT\";");
        ReflectionTestUtils.setField(invoker, "publishedMapper", Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{RulePublishedMapper.class},
                (proxy, method, args) -> child));
        invoker.register(engine.getRunner());
        RulePublished root = published(1L, "ROOT", "return executeRuleById(\"2\");");

        for (boolean trace : new boolean[]{false, true, false}) {
            traceChoices.clear();
            RuleResult result = service.executePublished(root, new LinkedHashMap<>(Map.of("age", 18)),
                    1L, "business-service", null, trace, trace);
            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals("PASS", result.getResult());
            assertEquals(List.of(trace, trace), traceChoices);
            assertNotNull(result.getTraceId());
            RuleExecutionLog log = logs.get(logs.size() - 1);
            assertEquals(result.getTraceId(), log.getTraceId());
            assertEquals("\"PASS\"", log.getOutputResult());
            if (trace) {
                assertNotNull(result.getTraces());
                assertTrue(log.getTraceInfo().contains("CHILD"));
            } else {
                assertNull(result.getTraces());
                assertNull(log.getTraceInfo());
            }
            assertNull(invoker.currentSession());
        }
        assertEquals(3, logs.size());
        assertEquals(List.of(true, true, true), billings);
    }

    private RulePublished published(long id, String code, String script) {
        RulePublished value = new RulePublished();
        value.setDefinitionId(id);
        value.setRuleCode(code);
        value.setProjectCode("credit");
        value.setModelType("SCRIPT");
        value.setVersion(1);
        value.setCompiledScript(script);
        return value;
    }
}
