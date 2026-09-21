package com.hengshucredit.rule.server.derived;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.core.engine.RequestContext;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.model.dto.RuleTestSchemaRequest;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.*;
import com.hengshucredit.rule.server.service.*;
import org.junit.After;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.Assert.*;

public class DerivedVariableIntegrationTest {
    @After public void clearContext() { RuntimeContextBridge.clear(); }

    @Test
    public void resolvesMultiLevelByIdAfterRenameAndIgnoresForgedDerivedInput() {
        RuleVariable amount = variable(1, "newAmount", "INPUT", null);
        RuleVariable doubled = variable(2, "doubled", "DERIVED", expression(1, "oldAmount"));
        RuleVariable quadrupled = variable(3, "quadrupled", "DERIVED", expression(2, "staleDoubled"));
        Fixture fixture = new Fixture(List.of(quadrupled, doubled, amount));
        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setRequiredScriptNames(Set.of("quadrupled"));
        Map<String, Object> result = fixture.resolver.resolve(1L, Map.of("newAmount", 7, "doubled", 999, "quadrupled", -1), options);
        assertEquals(new BigDecimal("28"), result.get("quadrupled"));
        assertEquals(new BigDecimal("14"), result.get("doubled"));
    }

    @Test
    public void rejectsCyclesInsteadOfTreatingDerivedFieldsAsExternalInput() {
        Fixture fixture = new Fixture(List.of(variable(1, "one", "DERIVED", expression(2, "two")), variable(2, "two", "DERIVED", expression(1, "one"))));
        assertThrows(IllegalStateException.class, () -> fixture.resolver.resolve(1L, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> fixture.analyzer.resolveInputFields(List.of(field(1, "one")), 1L));
    }

    @Test
    public void variableAndRuleSchemasExposeOnlyLowestCurrentInputNotHistoricalFields() {
        RuleVariable input = variable(1, "requestAmount", "INPUT", null);
        RuleVariable derived = variable(2, "twice", "DERIVED", expression(1, "oldName"));
        String config = "{\"mode\":\"HISTORY\",\"scope\":\"PROJECT\",\"window\":30,\"windowUnit\":\"DAY\",\"aggregate\":\"SUM\",\"valueField\":" + ref(4, "historyAmount")
                + ",\"steps\":[{\"inputs\":[" + ref(2, "staleTwice") + "],\"fields\":[" + ref(5, "historyKey") + "]}]}";
        Fixture fixture = new Fixture(List.of(input, derived, variable(3, "historyTotal", "DERIVED", config), variable(4, "historyAmount", "INPUT", null), variable(5, "historyKey", "INPUT", null)));
        var fields = fixture.analyzer.resolveInputFields(List.of(field(3, "historyTotal")), 1L);
        assertEquals(List.of("requestAmount"), fields.stream().map(RuleDefinitionInputField::getScriptName).toList());
        FieldDependencyResolver dependency = new FieldDependencyResolver();
        ReflectionTestUtils.setField(dependency, "ruleFieldAnalyzer", fixture.analyzer);
        ReflectionTestUtils.setField(dependency, "variableService", fixture.variables);
        RuleTestSchemaService schemas = new RuleTestSchemaService();
        ReflectionTestUtils.setField(schemas, "fieldDependencyResolver", dependency);
        RuleTestSchemaRequest request = new RuleTestSchemaRequest();
        request.setTargetType("VARIABLE"); request.setTargetId(3L);
        assertEquals(Map.of("requestAmount", 0d), schemas.build(request).getSampleParams());
        var publishedSchema = new com.hengshucredit.rule.server.artifact.RuleSchemaService().build(fields, List.of());
        assertTrue(publishedSchema.getInputSchemaJson().contains("requestAmount"));
        assertFalse(publishedSchema.getInputSchemaJson().contains("historyTotal"));
        assertFalse(publishedSchema.getInputSchemaJson().contains("historyKey"));
    }

    @Test
    public void nestedAndWorkerContextsKeepRootRuleAndOneCutoff() {
        RequestContext context = new RequestContext();
        try (var ignored = RuntimeContextBridge.install(context)) {
            context.setRuleContext(Map.of("id", 11L, "projectId", 1L), List.of());
            context.setRuleContext(Map.of("id", 22L, "projectId", 2L), List.of());
            var captured = RuntimeContextBridge.captureContext();
            try (var worker = RuntimeContextBridge.installContext(captured, null)) {
                assertEquals(11L, RuntimeContextBridge.currentContext().rootRule().get("id"));
                assertEquals(context.startedAt(), RuntimeContextBridge.currentContext().startedAt());
                Fixture fixture = new Fixture(List.of(variable(1, "applications", "DERIVED", "{\"mode\":\"HISTORY\",\"scope\":\"RULE\",\"window\":30,\"windowUnit\":\"DAY\",\"aggregate\":\"COUNT\"}")));
                assertEquals(2L, fixture.resolver.resolve(2L, Map.of()).get("applications"));
                assertEquals(11L, fixture.repository.ruleId.longValue());
                assertEquals(1L, fixture.repository.projectId.longValue());
                assertEquals(context.startedAt(), fixture.repository.before);
                assertEquals(context.startedAt().minusDays(30), fixture.repository.from);
            }
        }
    }

    @Test
    public void historySnapshotsExtractNestedContactListsByStableFieldId() {
        Map<String, Object> snapshot = HistoryFieldValues.snapshot(Map.of("DATA_OBJECT:9", "application.contacts.phone"),
                Map.of("application", Map.of("contacts", List.of(Map.of("phone", "123"), Map.of("phone", "456")))));
        assertEquals(List.of("123", "456"), snapshot.get("DATA_OBJECT:9"));
    }

    private static String expression(long id, String staleCode) {
        return "{\"mode\":\"EXPRESSION\",\"expression\":{\"kind\":\"OPERATION\",\"terms\":[{\"operand\":" + ref(id, staleCode) + "},{\"operator\":\"*\",\"operand\":{\"kind\":\"LITERAL\",\"valueType\":\"NUMBER\",\"value\":2}}]}}";
    }
    private static String ref(long id, String code) { return "{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":" + id + ",\"code\":\"" + code + "\"}"; }
    private static RuleVariable variable(long id, String code, String source, String config) {
        RuleVariable value = new RuleVariable(); value.setId(id); value.setProjectId(1L); value.setScope("PROJECT");
        value.setVarCode(code); value.setVarLabel(code); value.setScriptName(code); value.setVarType("NUMBER"); value.setVarSource(source); value.setStatus(1); value.setSourceConfig(config); return value;
    }
    private static RuleDefinitionInputField field(long id, String code) {
        RuleDefinitionInputField field = new RuleDefinitionInputField(); field.setVarId(id); field.setRefType("VARIABLE"); field.setFieldName(code); field.setScriptName(code); field.setFieldType("NUMBER"); field.setStatus(1); return field;
    }

    private static class Fixture {
        final VariableSourceResolver resolver = new VariableSourceResolver();
        final RuleFieldAnalyzer analyzer = new RuleFieldAnalyzer();
        final FakeHistory repository = new FakeHistory();
        final RuleVariableService variables;
        Fixture(List<RuleVariable> source) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                    new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), ""),
                    com.hengshucredit.rule.model.entity.RuleModel.class);
            variables = new RuleVariableService() {
                @Override public List<RuleVariable> listByProject(Long projectId, String varSource) { return source; }
                @Override public RuleVariable getById(Serializable id) { return source.stream().filter(item -> item.getId().equals(id)).findFirst().orElse(null); }
                @Override public Map<String, String> buildRefScriptNameMap(Long projectId) {
                    Map<String, String> paths = new LinkedHashMap<>(); source.forEach(item -> paths.put("VARIABLE:" + item.getId(), item.getScriptName())); return paths;
                }
            };
            DerivedVariableService derived = new DerivedVariableService();
            ReflectionTestUtils.setField(derived, "variableService", variables);
            ReflectionTestUtils.setField(derived, "historyRepository", repository);
            ReflectionTestUtils.setField(resolver, "variableService", variables);
            ReflectionTestUtils.setField(resolver, "derivedVariableService", derived);
            ReflectionTestUtils.setField(analyzer, "ruleVariableMapper", mapper(RuleVariableMapper.class, source));
            ReflectionTestUtils.setField(analyzer, "dataObjectMapper", mapper(RuleDataObjectMapper.class, List.of()));
            ReflectionTestUtils.setField(analyzer, "dataObjectFieldMapper", mapper(RuleDataObjectFieldMapper.class, List.of()));
            ReflectionTestUtils.setField(analyzer, "modelMapper", mapper(RuleModelMapper.class, List.of()));
        }
    }
    private static class FakeHistory extends ApplicationHistoryRepository {
        Long projectId; Long ruleId; LocalDateTime from; LocalDateTime before;
        FakeHistory() { super(null); }
        @Override public List<HistoryQuery.Row> query(String scope, Long projectId, Long rootRuleId, LocalDateTime from, LocalDateTime before) {
            this.projectId = projectId; this.ruleId = rootRuleId; this.from = from; this.before = before;
            return List.of(new HistoryQuery.Row(1, from, Map.of()), new HistoryQuery.Row(2, from, Map.of()));
        }
    }
    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, List<?> rows) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> "selectList".equals(method.getName()) ? rows : null);
    }
}
