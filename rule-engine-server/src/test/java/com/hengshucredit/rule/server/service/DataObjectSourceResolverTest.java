package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class DataObjectSourceResolverTest {
    @Test public void assembledResultModulePathIsAbsoluteEvenWithAnObjectSubRoot() {
        DataObjectSourceResolver resolver = new DataObjectSourceResolver();
        ReflectionTestUtils.setField(resolver, "externalApiInvokeService", new CountingApi(Map.of("body", Map.of("data", Map.of("score", 92)))));
        RuleDataObjectField score = field(10L, "score", "body.data.score");
        configureOwner(score, "{\"apiConfigId\":9,\"bindingMode\":\"FIELDS\",\"resultPath\":\"body.data\"}");
        Map<String, Object> values = new LinkedHashMap<>();
        resolver.resolve(null, List.of(direct(10L, "request.score")), values, VariableResolveOptions.defaults(), List.of(score));
        assertEquals(92, ExternalApiRequestPlan.read(values, "request.score"));
    }
    @Test public void childParameterOverrideRetainsOtherObjectOverridesAndMapsRawResponse() {
        DataObjectSourceResolver resolver = new DataObjectSourceResolver();
        ReflectionTestUtils.setField(resolver, "externalApiConsumerService", new ExternalApiConsumerService() {
            @Override public Map<String, Object> resolve(Long project, Long api, Map<String, Object> binding, Map<String, Object> values, VariableResolveOptions options) {
                assertEquals(Map.of("phone", "contact", "channel", "object-channel"), binding.get("requestOverrides"));
                return Map.of("body", Map.of(), "response", Map.of("body", Map.of("mobile", "222")));
            }
        });
        RuleDataObjectField field = field(10L, "mobile", "response.body.mobile");
        configureOwner(field, "{\"apiConfigId\":9,\"bindingMode\":\"OBJECT\",\"requestOverrides\":{\"phone\":\"applicant\",\"channel\":\"object-channel\"}}");
        field.setSourceConfig("{\"requestOverrides\":{\"phone\":\"contact\"}}");
        Map<String, Object> values = new LinkedHashMap<>();
        resolver.resolve(null, List.of(direct(10L, "request.mobile")), values, VariableResolveOptions.defaults(), List.of(field));
        assertEquals("222", ((Map<?, ?>) values.get("request")).get("mobile"));
    }
    @Test
    public void unselectedBranchAndOfflineReplayNeverStartApi() {
        DataObjectSourceResolver resolver = new DataObjectSourceResolver();
        CountingApi api = new CountingApi(Map.of("body", Map.of("name", "remote")));
        ReflectionTestUtils.setField(resolver, "externalApiInvokeService", api);
        RuleDataObjectField name = field(10L, "name", null);
        configureOwner(name, "{\"apiConfigId\":9,\"bindingMode\":\"OBJECT\"}");
        var options = VariableResolveOptions.defaults(); options.setRequiredScriptNames(java.util.Set.of());
        resolver.resolve(null, List.of(direct(10L, "request.name")), new LinkedHashMap<>(), options, List.of(name));
        options.setRequiredScriptNames(java.util.Set.of("request.name")); options.setOfflineReplay(true);
        resolver.resolve(null, List.of(direct(10L, "request.name")), new LinkedHashMap<>(), options, List.of(name));
        assertEquals(0, api.calls);
    }

    @Test
    public void cyclicObjectDependencyFailsBeforeAnyProviderCall() {
        DataObjectSourceResolver resolver = new DataObjectSourceResolver();
        CountingApi api = new CountingApi(Map.of());
        ReflectionTestUtils.setField(resolver, "externalApiInvokeService", api);
        RuleDataObjectField name = field(10L, "name", null);
        configureOwner(name, "{\"apiConfigId\":9,\"bindingMode\":\"OBJECT\"}");
        ReflectionTestUtils.setField(resolver, "variableSourceResolver", new VariableSourceResolver() {
            @Override void resolveApiDependencies(Long project, Map<String, Object> binding, Map<String, Object> values, VariableResolveOptions options) {
                resolver.resolve(project, List.of(direct(10L, "request.name")), values, options, List.of(name));
            }
        });
        var error = org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> resolver.resolve(null,
                List.of(direct(10L, "request.name")), new LinkedHashMap<>(), VariableResolveOptions.defaults(), List.of(name)));
        org.junit.Assert.assertTrue(error.getMessage().contains("循环"));
        assertEquals(0, api.calls);
    }

    @Test
    public void apiWholeResultMergesMissingFieldsAndKeepsExplicitValues() {
        DataObjectSourceResolver resolver = new DataObjectSourceResolver();
        CountingApi api = new CountingApi(Map.of(
                "body", Map.of("name", "remote", "age", 18)));
        ReflectionTestUtils.setField(resolver, "externalApiInvokeService", api);

        RuleDataObjectField name = field(10L, "name", null);
        RuleDataObjectField age = field(11L, "age", null);
        configureOwner(name, "{\"apiConfigId\":9,\"bindingMode\":\"OBJECT\",\"resultPath\":\"body\"}");
        configureOwner(age, name.getObjectSourceContent());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "explicit");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("request", request);

        resolver.resolve(null, List.of(direct(10L, "request.name"), direct(11L, "request.age")),
                values, VariableResolveOptions.defaults(), List.of(name, age));

        assertEquals("explicit", ((Map<?, ?>) values.get("request")).get("name"));
        assertEquals(18, ((Map<?, ?>) values.get("request")).get("age"));
        assertEquals(1, api.calls);
    }

    @Test
    public void apiFieldModeUsesEachFieldPath() {
        DataObjectSourceResolver resolver = new DataObjectSourceResolver();
        ReflectionTestUtils.setField(resolver, "externalApiInvokeService",
                new CountingApi(Map.of("body", Map.of("data", Map.of("id", "C-1", "score", 92)))));
        RuleDataObjectField id = field(20L, "id", "data.id");
        RuleDataObjectField score = field(21L, "score", "data.score");
        configureOwner(id, "{\"apiConfigId\":9,\"bindingMode\":\"FIELDS\",\"resultPath\":\"body\"}");
        configureOwner(score, id.getObjectSourceContent());
        Map<String, Object> values = new LinkedHashMap<>();

        resolver.resolve(null, List.of(direct(20L, "request.customerId"), direct(21L, "request.score")),
                values, VariableResolveOptions.defaults(), List.of(id, score));

        Map<?, ?> request = (Map<?, ?>) values.get("request");
        assertEquals("C-1", request.get("customerId"));
        assertEquals(92, request.get("score"));
    }

    @Test
    public void databaseSourceBindsFirstRowByColumnName() {
        DataObjectSourceResolver resolver = new DataObjectSourceResolver();
        ReflectionTestUtils.setField(resolver, "dbConnectPools", new DBConnectPools() {
            @Override
            public List<Map<String, Object>> query(Long id, String sql, List<Object> params,
                                                    int maxRows, int timeoutSeconds) {
                return List.of(Map.of("customer_id", "C-2", "risk_score", 80));
            }
        });
        RuleDataObjectField id = field(30L, "customerId", "customer_id");
        RuleDataObjectField score = field(31L, "score", "risk_score");
        configureOwner(id, "{\"dbDatasourceId\":8,\"sql\":\"select customer_id,risk_score from customer\",\"bindingMode\":\"FIELDS\"}");
        configureOwner(score, id.getObjectSourceContent());
        Map<String, Object> values = new LinkedHashMap<>();

        resolver.resolve(null, List.of(direct(30L, "request.customerId"), direct(31L, "request.score")),
                values, VariableResolveOptions.defaults(), List.of(id, score));

        Map<?, ?> request = (Map<?, ?>) values.get("request");
        assertEquals("C-2", request.get("customerId"));
        assertEquals(80, request.get("score"));
    }

    @Test
    public void databaseSourcesWithSameObjectIdButDifferentConfigsDoNotShareResults() {
        DataObjectSourceResolver resolver = new DataObjectSourceResolver();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        ReflectionTestUtils.setField(resolver, "dbConnectPools", new DBConnectPools() {
            @Override
            public List<Map<String, Object>> query(Long id, String sql, List<Object> params,
                                                    int maxRows, int timeoutSeconds) {
                calls.incrementAndGet();
                return List.of(Map.of("value", sql.contains("first") ? "A" : "B"));
            }
        });
        RuleDataObjectField first = field(40L, "first", "value");
        RuleDataObjectField second = field(41L, "second", "value");
        String base = "{\"dbDatasourceId\":8,\"sql\":\"select value from base\",\"bindingMode\":\"FIELDS\"}";
        configureOwner(first, base);
        configureOwner(second, base);
        first.setSourceConfig("{\"sql\":\"select value from first\"}");
        second.setSourceConfig("{\"sql\":\"select value from second\"}");
        Map<String, Object> values = new LinkedHashMap<>();

        resolver.resolve(null, List.of(direct(40L, "request.first"), direct(41L, "request.second")),
                values, VariableResolveOptions.defaults(), List.of(first, second));

        Map<?, ?> request = (Map<?, ?>) values.get("request");
        assertEquals("A", request.get("first"));
        assertEquals("B", request.get("second"));
        assertEquals(2, calls.get());
    }

    @Test
    public void waitingApiObjectSourcePreservesCauseAndStatus() {
        DataObjectSourceResolver resolver = new DataObjectSourceResolver();
        RuleDataObjectField field = field(50L, "score", "body.score");
        configureOwner(field, "{\"apiConfigId\":9,\"bindingMode\":\"FIELDS\"}");
        VariableResolutionInvocationCache cache = new VariableResolutionInvocationCache();
        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setInvocationCache(cache);
        ReflectionTestUtils.setField(resolver, "externalApiConsumerService", new ExternalApiConsumerService() {
            @Override
            public Map<String, Object> resolve(Long project, Long api, Map<String, Object> binding,
                                                Map<String, Object> values, VariableResolveOptions current) {
                throw new ExternalApiInvokeService.ApiInvokeException("pending", new java.util.concurrent.TimeoutException("pending"),
                        false, "DISABLED", false, Map.of("taskId", "task-50"), "WAIT");
            }
        });

        var error = org.junit.Assert.assertThrows(ExternalApiWaitingException.class, () -> resolver.resolve(null,
                List.of(direct(50L, "request.score")), new LinkedHashMap<>(), options, List.of(field)));
        assertEquals("task-50", ((ExternalApiInvokeService.ApiInvokeException) error.getCause()).getPendingAsync().get("taskId"));
        assertEquals("WAITING_EXTERNAL", options.getSourceStates().get("DATA_OBJECT:50").get("OUTCOME"));
    }

    private RuleDataObjectField field(Long id, String code, String sourcePath) {
        RuleDataObjectField field = new RuleDataObjectField();
        field.setId(id);
        field.setObjectId(100L);
        field.setVarCode(code);
        field.setSourcePath(sourcePath);
        return field;
    }

    private void configureOwner(RuleDataObjectField field, String content) {
        field.setObjectSourceType(content.startsWith("{\"api") ? "API" : "DB");
        field.setObjectSourceContent(content);
        field.setObjectScriptName("request");
    }

    private RuleDefinitionInputField direct(Long id, String path) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setVarId(id);
        field.setRefType("DATA_OBJECT");
        field.setScriptName(path);
        field.setFieldName(path);
        return field;
    }

    private static final class CountingApi extends ExternalApiInvokeService {
        private final Map<String, Object> response;
        private int calls;

        private CountingApi(Map<String, Object> response) {
            this.response = response;
        }

        @Override
        public Map<String, Object> invoke(Long apiConfigId, Map<String, Object> params) {
            calls++;
            return response;
        }
    }
}
