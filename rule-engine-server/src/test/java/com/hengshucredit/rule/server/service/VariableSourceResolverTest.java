package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    public void apiVariableCanUseBareParamMappingFieldName() throws Exception {
        RuleVariable variable = variable("riskScore", "API",
                "{\"apiConfigId\":7,\"paramMapping\":{\"cust\":\"customerId\"},\"resultPath\":\"body.score\"}");
        FakeApiService apiService = new FakeApiService(responseBody("score", 88));
        VariableSourceResolver resolver = resolver(Collections.singletonList(variable), apiService, new FakeDbPools(Collections.emptyList()));

        Map<String, Object> resolved = resolver.resolve(1L, singletonMap("customerId", "C001"));

        assertEquals(88, resolved.get("riskScore"));
        assertEquals("C001", apiService.lastParams.get("cust"));
    }

    @Test
    public void apiVariablesShareSameResponseWithinOneResolve() throws Exception {
        RuleVariable scoreV1 = variable("hscreditScoreV1", "API",
                "{\"apiConfigId\":7,\"paramMapping\":{\"request_id\":\"$.requestId\"},\"resultPath\":\"body.v1\"}");
        RuleVariable scoreV2 = variable("hscreditScoreV2", "API",
                "{\"apiConfigId\":7,\"paramMapping\":{\"request_id\":\"$.requestId\"},\"resultPath\":\"body.v2\"}");
        Map<String, Object> score = new LinkedHashMap<>();
        score.put("v1", 661.8);
        score.put("v2", 0.064);
        FakeApiService apiService = new FakeApiService(responseBody(score));
        VariableSourceResolver resolver = resolver(Arrays.asList(scoreV1, scoreV2), apiService, new FakeDbPools(Collections.emptyList()));

        Map<String, Object> resolved = resolver.resolve(1L, singletonMap("requestId", "REQ001"));

        assertEquals(661.8, ((Number) resolved.get("hscreditScoreV1")).doubleValue(), 0.000001);
        assertEquals(0.064, ((Number) resolved.get("hscreditScoreV2")).doubleValue(), 0.000001);
        assertEquals(1, apiService.callCount);
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
    public void testVariableForcesSourceLookupEvenWhenInputHasSameKey() throws Exception {
        RuleVariable variable = variable("riskScore", "API", "{\"apiConfigId\":7,\"resultPath\":\"body.score\"}");
        FakeApiService apiService = new FakeApiService(responseBody("score", 88));
        VariableSourceResolver resolver = resolver(Collections.singletonList(variable), apiService, new FakeDbPools(Collections.emptyList()));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("riskScore", 99);
        Map<String, Object> result = resolver.testVariable(1L, params);

        assertEquals(88, result.get("resolvedValue"));
        assertEquals(1, apiService.callCount);
    }

    @Test
    public void sourceVariableIsSkippedWhenNotRequiredByCurrentRule() throws Exception {
        RuleVariable variable = variable("riskScore", "API", "{\"apiConfigId\":7,\"resultPath\":\"body.score\"}");
        FakeApiService apiService = new FakeApiService(responseBody("score", 88));
        VariableSourceResolver resolver = resolver(Collections.singletonList(variable), apiService, new FakeDbPools(Collections.emptyList()));

        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setRequiredScriptNames(new LinkedHashSet<>(Collections.singletonList("amount")));
        Map<String, Object> resolved = resolver.resolve(1L, singletonMap("amount", 100), options);

        assertEquals(null, resolved.get("riskScore"));
        assertEquals(0, apiService.callCount);
    }

    @Test
    public void sourceVariableDependenciesResolveBeforeDependentApi() throws Exception {
        RuleVariable apiScore = variable("riskScore", "API",
                "{\"apiConfigId\":7,\"paramMapping\":{\"score\":\"$.dbScore\"},\"resultPath\":\"body.score\"}");
        RuleVariable dbScore = variable("dbScore", "DB",
                "{\"datasourceId\":3,\"sql\":\"select score from t\",\"maxRows\":1}");
        FakeApiService apiService = new FakeApiService(responseBody("score", 88));
        FakeDbPools dbPools = new FakeDbPools(Collections.singletonList(singletonMap("score", 72)));
        VariableSourceResolver resolver = resolver(Arrays.asList(apiScore, dbScore), apiService, dbPools);

        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setRequiredScriptNames(new LinkedHashSet<>(Collections.singletonList("riskScore")));
        Map<String, Object> resolved = resolver.resolve(1L, Collections.emptyMap(), options);

        assertEquals(72, resolved.get("dbScore"));
        assertEquals(88, resolved.get("riskScore"));
        assertEquals(72, apiService.lastParams.get("score"));
    }

    @Test
    public void apiVariableCanInferDependenciesFromApiConfigWithoutParamMapping() throws Exception {
        RuleVariable apiScore = variable("riskScore", "API", "{\"apiConfigId\":7,\"resultPath\":\"body.score\"}");
        RuleVariable customerId = variable("customerId", "CONSTANT", null);
        customerId.setVarType("STRING");
        customerId.setDefaultValue("C001");
        RuleExternalApiConfig apiConfig = new RuleExternalApiConfig();
        apiConfig.setId(7L);
        apiConfig.setQueryConfig("{\"customer\":\"${customerId}\"}");
        apiConfig.setBodyTemplate("{\"payload\":{\"customerId\":\"${customerId}\"}}");
        FakeApiService apiService = new FakeApiService(responseBody("score", 88));
        VariableSourceResolver resolver = resolver(Arrays.asList(apiScore, customerId), apiService,
                new FakeDbPools(Collections.emptyList()), new FakeRuleListService(false), null, apiConfig);

        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setRequiredScriptNames(new LinkedHashSet<>(Collections.singletonList("riskScore")));
        Map<String, Object> resolved = resolver.resolve(1L, Collections.emptyMap(), options);

        assertEquals("C001", resolved.get("customerId"));
        assertEquals(88, resolved.get("riskScore"));
        assertEquals("C001", apiService.lastParams.get("customerId"));
    }

    @Test
    public void apiVariableCollectsParamMappingAndApiConfigDependenciesTogether() throws Exception {
        RuleVariable apiScore = variable("riskScore", "API",
                "{\"apiConfigId\":7,\"paramMapping\":{\"request_id\":\"$.requestId\"},\"resultPath\":\"body.score\"}");
        RuleExternalApiConfig apiConfig = new RuleExternalApiConfig();
        apiConfig.setId(7L);
        apiConfig.setQueryConfig("{\"customer\":\"${customerId}\"}");
        VariableSourceResolver resolver = resolver(Collections.singletonList(apiScore),
                new FakeApiService(Collections.emptyMap()), new FakeDbPools(Collections.emptyList()),
                new FakeRuleListService(false), null, apiConfig);

        assertEquals(new LinkedHashSet<>(Arrays.asList("requestId", "customerId")),
                resolver.collectVariableDependencies(apiScore));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void modelOutputExecutesWithResolvedSourceInputs() throws Exception {
        RuleVariable hyField = variable("HY001", "DB",
                "{\"datasourceId\":3,\"sql\":\"select hy001 from t\",\"maxRows\":1}");
        FakeDbPools dbPools = new FakeDbPools(Collections.singletonList(singletonMap("hy001", 12.5)));
        FakeModelService modelService = new FakeModelService();
        VariableSourceResolver resolver = resolver(Collections.singletonList(hyField),
                new FakeApiService(Collections.emptyMap()), dbPools, new FakeRuleListService(false), modelService);

        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setRequiredScriptNames(new LinkedHashSet<>(Collections.singletonList("score_f1.score")));
        Map<String, Object> resolved = resolver.resolve(1L, Collections.emptyMap(), options);

        assertEquals(12.5, ((Number) resolved.get("HY001")).doubleValue(), 0.000001);
        assertEquals(12.5, ((Number) modelService.lastParams.get("HY001")).doubleValue(), 0.000001);
        Map<String, Object> modelOutput = (Map<String, Object>) resolved.get("score_f1");
        assertEquals(660, modelOutput.get("score"));
    }

    @Test
    public void modelParamsCanReadFieldsFromModelFieldsObject() {
        RuleModel model = modelDetail(100L, "score_f1",
                input("HYBASE_X115", "HYBASE_X115"),
                input("HYJH_X54", "HYJH_X54"));
        Map<String, Object> scoreFields = new LinkedHashMap<>();
        scoreFields.put("HYBASE_X115", 2);
        scoreFields.put("HYJH_X54", 3);
        Map<String, Object> resolvedParams = new LinkedHashMap<>();
        resolvedParams.put("score_f1_fields", scoreFields);

        Map<String, Object> modelParams = new VariableSourceResolver().buildModelParams(model, resolvedParams);

        assertEquals(2, modelParams.get("HYBASE_X115"));
        assertEquals(3, modelParams.get("HYJH_X54"));
    }

    @Test
    public void sourceVariableWaitsForRequiredModelOutput() throws Exception {
        RuleVariable riskBand = variable("riskBand", "API",
                "{\"apiConfigId\":7,\"paramMapping\":{\"score\":\"$.score_f1.score\"},\"resultPath\":\"body.band\"}");
        FakeApiService apiService = new FakeApiService(responseBody("band", "A"));
        SequencedModelService modelService = new SequencedModelService(
                Collections.singletonList(model(100L, "score_f1")),
                Collections.singletonMap(100L, modelDetail(100L, "score_f1")),
                Collections.singletonMap(100L, singletonMap("score", 660)));
        VariableSourceResolver resolver = resolver(Collections.singletonList(riskBand),
                apiService, new FakeDbPools(Collections.emptyList()), new FakeRuleListService(false), modelService);

        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setRequiredScriptNames(new LinkedHashSet<>(Collections.singletonList("riskBand")));
        Map<String, Object> resolved = resolver.resolve(1L, Collections.emptyMap(), options);

        assertEquals("A", resolved.get("riskBand"));
        assertEquals(660, apiService.lastParams.get("score"));
        assertEquals(Collections.singletonList("score_f1"), modelService.executeOrder);
    }

    @Test
    public void modelDependencyExecutesBeforeDependentModel() throws Exception {
        SequencedModelService modelService = new SequencedModelService(
                Arrays.asList(model(200L, "final_model"), model(100L, "score_f1")),
                mapOf(
                        200L, modelDetail(200L, "final_model", input("upstreamScore", "score_f1.score")),
                        100L, modelDetail(100L, "score_f1")
                ),
                mapOf(
                        200L, singletonMap("finalScore", 760),
                        100L, singletonMap("score", 660)
                ));
        VariableSourceResolver resolver = resolver(Collections.emptyList(),
                new FakeApiService(Collections.emptyMap()), new FakeDbPools(Collections.emptyList()),
                new FakeRuleListService(false), modelService);

        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setRequiredScriptNames(new LinkedHashSet<>(Collections.singletonList("final_model.finalScore")));
        Map<String, Object> resolved = resolver.resolve(1L, Collections.emptyMap(), options);

        assertEquals(Arrays.asList("score_f1", "final_model"), modelService.executeOrder);
        assertEquals(660, modelService.paramsByModelId.get(200L).get("upstreamScore"));
        assertEquals(760, ((Map<?, ?>) resolved.get("final_model")).get("finalScore"));
    }

    @Test
    public void apiVariableCanUseConstantDefaultAsMappedParam() throws Exception {
        RuleVariable threshold = variable("riskThreshold", "CONSTANT", null);
        threshold.setVarType("INTEGER");
        threshold.setDefaultValue("700");
        RuleVariable riskBand = variable("riskBand", "API",
                "{\"apiConfigId\":7,\"paramMapping\":{\"threshold\":\"$.riskThreshold\"},\"resultPath\":\"body.band\"}");
        FakeApiService apiService = new FakeApiService(responseBody("band", "A"));
        VariableSourceResolver resolver = resolver(Arrays.asList(riskBand, threshold),
                apiService, new FakeDbPools(Collections.emptyList()));

        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setRequiredScriptNames(new LinkedHashSet<>(Collections.singletonList("riskBand")));
        Map<String, Object> resolved = resolver.resolve(1L, Collections.emptyMap(), options);

        assertEquals(700, resolved.get("riskThreshold"));
        assertEquals(700, apiService.lastParams.get("threshold"));
        assertEquals("A", resolved.get("riskBand"));
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
    public void dbVariableCanUseBareParamFieldName() throws Exception {
        RuleVariable variable = variable("riskScore", "DB",
                "{\"datasourceId\":3,\"sql\":\"select score from t where id = ?\",\"params\":[\"customerId\"],\"maxRows\":1}");
        FakeDbPools dbPools = new FakeDbPools(Collections.singletonList(singletonMap("score", 72)));
        VariableSourceResolver resolver = resolver(Collections.singletonList(variable), new FakeApiService(Collections.emptyMap()), dbPools);

        Map<String, Object> resolved = resolver.resolve(1L, singletonMap("customerId", "C002"));

        assertEquals(72, resolved.get("riskScore"));
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

    @Test
    public void decimalSourceVariableUsesNumericDefaultWhenLookupReturnsNull() throws Exception {
        RuleVariable variable = variable("rate", "DB",
                "{\"datasourceId\":3,\"sql\":\"select rate from t\",\"maxRows\":1}");
        variable.setVarType("DECIMAL");
        variable.setDefaultValue("12.5");
        VariableSourceResolver resolver = resolver(Collections.singletonList(variable),
                new FakeApiService(Collections.emptyMap()), new FakeDbPools(Collections.emptyList()));

        Map<String, Object> resolved = resolver.resolve(1L, Collections.emptyMap());

        assertEquals(12.5, ((Number) resolved.get("rate")).doubleValue(), 0.000001);
    }

    @Test
    public void listVariableQueriesConfiguredFieldAndReturnsHitFlag() throws Exception {
        RuleVariable variable = variable("mobileBlackHit", "LIST",
                "{\"listId\":9,\"queryField\":\"request.mobile\",\"itemTypes\":[\"MOBILE\"]}");
        FakeRuleListService listService = new FakeRuleListService(true);
        VariableSourceResolver resolver = resolver(Collections.singletonList(variable),
                new FakeApiService(Collections.emptyMap()), new FakeDbPools(Collections.emptyList()), listService);

        Map<String, Object> request = singletonMap("request", singletonMap("mobile", "13800138000"));
        Map<String, Object> resolved = resolver.resolve(1L, request);

        assertEquals(1, resolved.get("mobileBlackHit"));
        assertEquals(9L, listService.lastListId.longValue());
        assertEquals("13800138000", listService.lastContent);
        assertEquals(Collections.singletonList("MOBILE"), listService.lastItemTypes);
        assertEquals("IN_LIST", listService.lastMatchMode);
    }

    @Test
    public void listVariablePassesConfiguredMatchMode() throws Exception {
        RuleVariable variable = variable("mobileNotBlack", "LIST",
                "{\"listId\":9,\"queryField\":\"mobile\",\"itemTypes\":[\"手机号\"],\"matchMode\":\"NOT_IN_LIST\"}");
        FakeRuleListService listService = new FakeRuleListService(true);
        VariableSourceResolver resolver = resolver(Collections.singletonList(variable),
                new FakeApiService(Collections.emptyMap()), new FakeDbPools(Collections.emptyList()), listService);

        Map<String, Object> request = singletonMap("mobile", "13800138000");
        Map<String, Object> resolved = resolver.resolve(1L, request);

        assertEquals(1, resolved.get("mobileNotBlack"));
        assertEquals("NOT_IN_LIST", listService.lastMatchMode);
    }

    @Test
    public void testGroupCanSkipApiSourceAndSetNull() throws Exception {
        RuleVariable variable = variable("riskScore", "API",
                "{\"apiConfigId\":7,\"resultPath\":\"body.score\",\"forceRefresh\":true}");
        FakeApiService apiService = new FakeApiService(responseBody("score", 88));
        VariableSourceResolver resolver = resolver(Collections.singletonList(variable), apiService, new FakeDbPools(Collections.emptyList()));

        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setSkipApiSources(true);
        Map<String, Object> resolved = resolver.resolve(1L, Collections.emptyMap(), options);

        assertEquals(null, resolved.get("riskScore"));
        assertEquals(0, apiService.callCount);
    }

    @Test
    public void testGroupListVariableUsesRequestTimeSnapshot() throws Exception {
        RuleVariable variable = variable("mobileBlackHit", "LIST",
                "{\"listId\":9,\"queryField\":\"mobile\",\"itemTypes\":[\"MOBILE\"]}");
        FakeRuleListService listService = new FakeRuleListService(true);
        VariableSourceResolver resolver = resolver(Collections.singletonList(variable),
                new FakeApiService(Collections.emptyMap()), new FakeDbPools(Collections.emptyList()), listService);

        LocalDateTime requestTime = LocalDateTime.of(2026, 7, 1, 10, 30);
        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setListMatchTime(requestTime);
        Map<String, Object> resolved = resolver.resolve(1L, singletonMap("mobile", "13800138000"), options);

        assertEquals(1, resolved.get("mobileBlackHit"));
        assertEquals(requestTime, listService.lastMatchTime);
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
        return resolver(variables, apiService, dbPools, new FakeRuleListService(false));
    }

    private VariableSourceResolver resolver(List<RuleVariable> variables, ExternalApiInvokeService apiService,
                                            DBConnectPools dbPools, RuleListService listService) throws Exception {
        return resolver(variables, apiService, dbPools, listService, null);
    }

    private VariableSourceResolver resolver(List<RuleVariable> variables, ExternalApiInvokeService apiService,
                                            DBConnectPools dbPools, RuleListService listService,
                                            RuleModelService modelService) throws Exception {
        return resolver(variables, apiService, dbPools, listService, modelService, null);
    }

    private VariableSourceResolver resolver(List<RuleVariable> variables, ExternalApiInvokeService apiService,
                                            DBConnectPools dbPools, RuleListService listService,
                                            RuleModelService modelService, RuleExternalApiConfig apiConfig) throws Exception {
        VariableSourceResolver resolver = new VariableSourceResolver();
        setField(resolver, "variableService", new FakeVariableService(variables));
        setField(resolver, "externalApiInvokeService", apiService);
        if (apiConfig != null) {
            setField(resolver, "apiConfigMapper", apiConfigMapper(apiConfig));
        }
        setField(resolver, "dbConnectPools", dbPools);
        setField(resolver, "ruleListService", listService);
        setField(resolver, "ruleModelService", modelService);
        return resolver;
    }

    private RuleExternalApiConfigMapper apiConfigMapper(RuleExternalApiConfig apiConfig) {
        return (RuleExternalApiConfigMapper) Proxy.newProxyInstance(
                RuleExternalApiConfigMapper.class.getClassLoader(),
                new Class<?>[]{RuleExternalApiConfigMapper.class},
                (proxy, method, args) -> "selectById".equals(method.getName()) ? apiConfig : null);
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

    private Map<String, Object> responseBody(Map<String, Object> body) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("body", body);
        return response;
    }

    private Map<String, Object> singletonMap(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private <K, V> Map<K, V> mapOf(K key1, V value1, K key2, V value2) {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private RuleModel model(Long id, String code) {
        RuleModel model = new RuleModel();
        model.setId(id);
        model.setModelCode(code);
        model.setStatus(1);
        return model;
    }

    private RuleModel modelDetail(Long id, String code, RuleModelInputField... inputs) {
        RuleModel model = model(id, code);
        model.setInputFields(inputs == null ? Collections.emptyList() : Arrays.asList(inputs));
        return model;
    }

    private RuleModelInputField input(String fieldName, String scriptName) {
        RuleModelInputField input = new RuleModelInputField();
        input.setFieldName(fieldName);
        input.setScriptName(scriptName);
        input.setStatus(1);
        return input;
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

        @Override
        public RuleVariable getById(java.io.Serializable id) {
            for (RuleVariable variable : variables) {
                if (variable.getId() != null && variable.getId().equals(id)) {
                    return variable;
                }
            }
            return null;
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

    private static class FakeRuleListService extends RuleListService {
        private final boolean hit;
        private Long lastListId;
        private Object lastContent;
        private List<String> lastItemTypes;
        private String lastMatchMode;
        private LocalDateTime lastMatchTime;

        private FakeRuleListService(boolean hit) {
            this.hit = hit;
        }

        @Override
        public boolean hit(Long listId, Object content, List<String> itemTypes) {
            return match(listId, content, itemTypes, "IN_LIST");
        }

        @Override
        public boolean match(Long listId, Object content, List<String> itemTypes, String matchMode) {
            this.lastListId = listId;
            this.lastContent = content;
            this.lastItemTypes = itemTypes;
            this.lastMatchMode = matchMode;
            return hit;
        }

        @Override
        public boolean matchAt(Long listId, Object content, List<String> itemTypes, String matchMode, LocalDateTime matchTime) {
            this.lastMatchTime = matchTime;
            return match(listId, content, itemTypes, matchMode);
        }
    }

    private static class FakeModelService extends RuleModelService {
        private Map<String, Object> lastParams;

        @Override
        public List<RuleModel> listByProject(Long projectId) {
            RuleModel model = new RuleModel();
            model.setId(100L);
            model.setModelCode("score_f1");
            model.setStatus(1);
            return Collections.singletonList(model);
        }

        @Override
        public RuleModel getDetail(Long modelId) {
            RuleModel model = new RuleModel();
            model.setId(modelId);
            model.setModelCode("score_f1");
            RuleModelInputField input = new RuleModelInputField();
            input.setFieldName("HY001");
            input.setScriptName("HY001");
            input.setStatus(1);
            model.setInputFields(Collections.singletonList(input));
            return model;
        }

        @Override
        public Map<String, Object> execute(Long modelId, Map<String, Object> params) {
            this.lastParams = params;
            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put("score", 660);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("outputs", outputs);
            return result;
        }
    }

    private static class SequencedModelService extends RuleModelService {
        private final List<RuleModel> models;
        private final Map<Long, RuleModel> details;
        private final Map<Long, Map<String, Object>> outputs;
        private final List<String> executeOrder = new java.util.ArrayList<>();
        private final Map<Long, Map<String, Object>> paramsByModelId = new LinkedHashMap<>();

        private SequencedModelService(List<RuleModel> models, Map<Long, RuleModel> details,
                                      Map<Long, Map<String, Object>> outputs) {
            this.models = models;
            this.details = details;
            this.outputs = outputs;
        }

        @Override
        public List<RuleModel> listByProject(Long projectId) {
            return models;
        }

        @Override
        public RuleModel getDetail(Long modelId) {
            return details.get(modelId);
        }

        @Override
        public Map<String, Object> execute(Long modelId, Map<String, Object> params) {
            RuleModel detail = details.get(modelId);
            executeOrder.add(detail == null ? String.valueOf(modelId) : detail.getModelCode());
            paramsByModelId.put(modelId, params);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("outputs", outputs.get(modelId));
            return result;
        }
    }
}
