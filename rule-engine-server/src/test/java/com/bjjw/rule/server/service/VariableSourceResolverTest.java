package com.bjjw.rule.server.service;

import com.bjjw.rule.model.entity.RuleVariable;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class VariableSourceResolverTest {

    @Test
    public void apiVariableUsesParamMappingAndResultPath() throws Exception {
        RuleVariable variable = variable("riskScore", "API",
                "{\"apiConfigId\":7,\"paramMapping\":{\"cust\":\"$.customerId\"},\"resultPath\":\"body.data.score\"}");
        FakeApiService apiService = new FakeApiService(responseBody("data", singletonMap("score", 88)));
        VariableSourceResolver resolver = resolver(Collections.singletonList(variable), apiService, new FakeDbPools(Collections.emptyList()));

        Map<String, Object> params = singletonMap("customerId", "C001");
        Map<String, Object> resolved = resolver.resolve(1L, params);

        assertEquals(88, resolved.get("riskScore"));
        assertEquals(7L, apiService.lastApiConfigId.longValue());
        assertEquals("C001", apiService.lastParams.get("cust"));
    }

    @Test
    public void sourceVariableDoesNotOverwriteExistingInputByDefault() throws Exception {
        RuleVariable variable = variable("riskScore", "API", "{\"apiConfigId\":7,\"resultPath\":\"body.score\"}");
        FakeApiService apiService = new FakeApiService(responseBody("score", 88));
        VariableSourceResolver resolver = resolver(Collections.singletonList(variable), apiService, new FakeDbPools(Collections.emptyList()));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("riskScore", 99);
        Map<String, Object> resolved = resolver.resolve(1L, params);

        assertEquals(99, resolved.get("riskScore"));
        assertEquals(0, apiService.callCount);
    }

    @Test
    public void dbVariableUsesPreparedParamsAndFirstColumnByDefault() throws Exception {
        RuleVariable variable = variable("riskScore", "DB",
                "{\"datasourceId\":3,\"sql\":\"select score from t where id = ?\",\"params\":[\"$.customerId\"],\"maxRows\":1}");
        FakeDbPools dbPools = new FakeDbPools(Collections.singletonList(singletonMap("score", 72)));
        VariableSourceResolver resolver = resolver(Collections.singletonList(variable), new FakeApiService(Collections.emptyMap()), dbPools);

        Map<String, Object> params = singletonMap("customerId", "C002");
        Map<String, Object> resolved = resolver.resolve(1L, params);

        assertEquals(72, resolved.get("riskScore"));
        assertEquals(3L, dbPools.lastDatasourceId.longValue());
        assertEquals(Collections.singletonList("C002"), dbPools.lastParams);
    }

    @Test
    public void sourceVariableCanReturnDefaultWhenLookupFails() throws Exception {
        RuleVariable variable = variable("riskScore", "DB",
                "{\"datasourceId\":3,\"sql\":\"select score from t\",\"exceptionStrategy\":\"RETURN_DEFAULT\",\"fallbackValue\":\"12\"}");
        FakeDbPools dbPools = new FakeDbPools(new IllegalStateException("db down"));
        VariableSourceResolver resolver = resolver(Collections.singletonList(variable), new FakeApiService(Collections.emptyMap()), dbPools);

        Map<String, Object> resolved = resolver.resolve(1L, Collections.emptyMap());

        assertEquals(12, resolved.get("riskScore"));
    }

    private RuleVariable variable(String scriptName, String source, String sourceConfig) {
        RuleVariable variable = new RuleVariable();
        variable.setId(1L);
        variable.setProjectId(1L);
        variable.setScope("PROJECT");
        variable.setVarCode(scriptName);
        variable.setVarLabel(scriptName);
        variable.setScriptName(scriptName);
        variable.setVarType("NUMBER");
        variable.setVarSource(source);
        variable.setSourceConfig(sourceConfig);
        variable.setStatus(1);
        return variable;
    }

    private VariableSourceResolver resolver(List<RuleVariable> variables, ExternalApiInvokeService apiService,
                                            DBConnectPools dbPools) throws Exception {
        VariableSourceResolver resolver = new VariableSourceResolver();
        setField(resolver, "variableService", new FakeVariableService(variables));
        setField(resolver, "externalApiInvokeService", apiService);
        setField(resolver, "dbConnectPools", dbPools);
        return resolver;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Map<String, Object> responseBody(String key, Object value) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("body", singletonMap(key, value));
        return response;
    }

    private Map<String, Object> singletonMap(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static class FakeVariableService extends RuleVariableService {
        private final List<RuleVariable> variables;

        private FakeVariableService(List<RuleVariable> variables) {
            this.variables = variables;
        }

        @Override
        public List<RuleVariable> listByProject(Long projectId, String varSource) {
            return variables;
        }
    }

    private static class FakeApiService extends ExternalApiInvokeService {
        private final Map<String, Object> response;
        private Long lastApiConfigId;
        private Map<String, Object> lastParams;
        private int callCount;

        private FakeApiService(Map<String, Object> response) {
            this.response = response;
        }

        @Override
        public Map<String, Object> invoke(Long apiConfigId, Map<String, Object> params) {
            this.callCount++;
            this.lastApiConfigId = apiConfigId;
            this.lastParams = params;
            return response;
        }
    }

    private static class FakeDbPools extends DBConnectPools {
        private final List<Map<String, Object>> rows;
        private final RuntimeException error;
        private Long lastDatasourceId;
        private List<Object> lastParams;

        private FakeDbPools(List<Map<String, Object>> rows) {
            this.rows = rows;
            this.error = null;
        }

        private FakeDbPools(RuntimeException error) {
            this.rows = Collections.emptyList();
            this.error = error;
        }

        @Override
        public List<Map<String, Object>> query(Long datasourceId, String sql, List<Object> params, int maxRows) {
            if (error != null) {
                throw error;
            }
            this.lastDatasourceId = datasourceId;
            this.lastParams = params == null ? Collections.emptyList() : Arrays.asList(params.toArray());
            return rows;
        }
    }
}
