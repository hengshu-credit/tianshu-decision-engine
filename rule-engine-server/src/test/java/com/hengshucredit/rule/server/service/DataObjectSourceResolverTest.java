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
