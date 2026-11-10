package com.hengshucredit.rule.server.dashboard;

import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.ConsoleUserPreferenceMapper;
import com.hengshucredit.rule.server.service.ConsoleUserPreferenceService;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static com.hengshucredit.rule.server.dashboard.DashboardMapping.JsonSource.INPUT;
import static com.hengshucredit.rule.server.dashboard.DashboardMapping.RefType.VARIABLE;
import static com.hengshucredit.rule.server.dashboard.DashboardMetricField.AMOUNT;
import static com.hengshucredit.rule.server.dashboard.DashboardMetricField.LONGITUDE;
import static com.hengshucredit.rule.server.dashboard.DashboardMetricField.PERIOD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class DashboardMappingServiceTest {
    @Test
    public void decisionOperandsRoundTripAndResolveCurrentIdsWithoutPersistingEvaluatedValues() {
        var decision = variable(500L, 0L, "GLOBAL", "decision", "STRING");
        var constant = constant(501L, "old_code", "allow");
        var source = variable(502L, 0L, "GLOBAL", "payload", "OBJECT");
        var references = repository(decision, constant, source);
        var service = service(new HashMap<>(), references);
        var values = operandValues(
                "{kind:'REFERENCE',refType:'CONSTANT',refId:501,code:'old_code'}",
                "{kind:'PATH',refType:'VARIABLE',refId:502,code:'old_payload',relativePath:'.review'}",
                "{kind:'OPERATION',terms:[{operand:{kind:'LITERAL',value:'100',valueType:'NUMBER'}},{operator:'+',operand:{kind:'LITERAL',value:'1',valueType:'NUMBER'}}]}");
        var mapping = new DashboardMapping.StoredMapping(VARIABLE, 500L, INPUT, values);
        service.saveSystemDefaults("admin", 0L, Map.of(DashboardMetricField.DECISION_RESULT, mapping));
        assertFalse(references.findSystemDefaults().mappingsJson().contains("allow"));
        constant.setDefaultValue("APPROVE");
        constant.setVarCode("renamed");
        source.setVarCode("new_payload");
        var resolved = service.resolve(8L, null).get(DashboardMetricField.DECISION_RESULT);
        org.junit.Assert.assertTrue(resolved.errorMessage(), resolved.valid());
        assertEquals(java.util.Set.of("APPROVE"), resolved.decisionValues().pass());
        assertEquals(java.util.Set.of("101"), resolved.decisionValues().reject());
        assertEquals(java.util.Set.of("REVIEW"), resolved.decisionExpressions().values(
                Map.of("new_payload", Map.of("review", "review")), false).get(1));
        var settings = service.systemSettings().fields().stream()
                .filter(field -> field.metricField() == DashboardMetricField.DECISION_RESULT).findFirst().orElseThrow();
        assertEquals(values.passOperands(), settings.mapping().decisionValues().passOperands());
        assertEquals(java.util.Set.of(), settings.mapping().decisionValues().pass());
    }

    @Test
    public void decisionOperandsRejectOverlapsUnboundPathsAndUnauthorizedReferences() {
        var decision = variable(500L, 0L, "GLOBAL", "decision", "STRING");
        var foreign = variable(501L, 9L, "PROJECT", "private", "STRING");
        var service = service(new HashMap<>(), repository(decision, foreign));
        for (String pass : java.util.List.of(
                "{kind:'LITERAL',value:'review',valueType:'STRING'}",
                "{kind:'PATH',code:'secret'}",
                "{kind:'PATH',refType:'VARIABLE',refId:501,code:'private'}",
                "{kind:'FUNCTION',functionId:1,functionCode:'invoke'}",
                "{kind:'OPERATION',terms:[{operand:{kind:'LITERAL',value:'1',valueType:'NUMBER'}},{operator:'/',operand:{kind:'LITERAL',value:'0',valueType:'NUMBER'}}]}",
                "{kind:'REFERENCE',refType:'VARIABLE',refId:500,code:'decision',versionMode:'FIXED',versionId:1}",
                "{kind:'PATH',refType:'VARIABLE',refId:500,code:'decision',relativePath:'[*]'}")) {
            var values = operandValues(pass, "{kind:'LITERAL',value:'REVIEW',valueType:'STRING'}", "{kind:'LITERAL',value:'REJECT',valueType:'STRING'}");
            assertThrows(pass, IllegalArgumentException.class, () -> service.saveSystemDefaults("admin", 0L,
                    Map.of(DashboardMetricField.DECISION_RESULT, new DashboardMapping.StoredMapping(VARIABLE, 500L, INPUT, values))));
        }
    }

    private DashboardMapping.DecisionValues operandValues(String pass, String review, String reject) {
        return new DashboardMapping.DecisionValues(null, null, null, null, null, null,
                java.util.List.of(com.alibaba.fastjson.JSON.parseObject(pass)),
                java.util.List.of(com.alibaba.fastjson.JSON.parseObject(review)),
                java.util.List.of(com.alibaba.fastjson.JSON.parseObject(reject)));
    }

    @Test
    public void decisionConstantsAreStoredByIdAndFollowCurrentValuesWithoutNameMatching() {
        var decision = variable(500L, 0L, "GLOBAL", "decision", "STRING");
        var pass = constant(501L, "PASS_CODE", "PASS");
        var review = constant(502L, "REVIEW_CODE", "REVIEW");
        var reject = constant(503L, "REJECT_CODE", "REJECT");
        var references = repository(decision, pass, review, reject);
        var service = service(new HashMap<>(), references);
        var values = new DashboardMapping.DecisionValues(java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
                java.util.Set.of(501L), java.util.Set.of(502L), java.util.Set.of(503L));
        var mapping = new DashboardMapping.StoredMapping(VARIABLE, 500L, DashboardMapping.JsonSource.OUTPUT, values);
        service.saveSystemDefaults("admin", 0L, Map.of(DashboardMetricField.DECISION_RESULT, mapping));
        String stored = references.findSystemDefaults().mappingsJson();
        org.junit.Assert.assertTrue(stored.contains("passRefIds"));
        assertFalse(stored.contains("PASS_CODE"));
        assertFalse(stored.contains("\"PASS\""));
        pass.setVarCode("RENAMED_PASS");
        pass.setDefaultValue("ALLOW");
        var resolved = service.resolve(8L, null).get(DashboardMetricField.DECISION_RESULT);
        org.junit.Assert.assertTrue(resolved.errorMessage() + ": " + stored, resolved.valid());
        assertEquals(java.util.Set.of("ALLOW"), resolved.decisionValues().pass());
        assertEquals(java.util.Set.of(501L), resolved.decisionValues().passRefIds());
        review.setDefaultValue("ALLOW");
        assertFalse(service.resolve(8L, null).get(DashboardMetricField.DECISION_RESULT).valid());
        assertThrows(IllegalArgumentException.class, () -> service.saveSystemDefaults("admin", 1L,
                Map.of(DashboardMetricField.DECISION_RESULT, mapping)));
        review.setDefaultValue("REVIEW");
        pass.setStatus(0);
        assertFalse(service.resolve(8L, null).get(DashboardMetricField.DECISION_RESULT).valid());
    }

    @Test
    public void decisionGroupsRejectNonConstantAndInaccessibleReferences() {
        var decision = variable(500L, 0L, "GLOBAL", "decision", "STRING");
        var notConstant = variable(501L, 0L, "GLOBAL", "PASS", "STRING");
        var service = service(new HashMap<>(), repository(decision, notConstant));
        for (long id : new long[]{501L, 999L}) {
            var values = new DashboardMapping.DecisionValues(java.util.Set.of(), java.util.Set.of("REVIEW"), java.util.Set.of("REJECT"),
                    java.util.Set.of(id), java.util.Set.of(), java.util.Set.of());
            assertThrows(IllegalArgumentException.class, () -> service.saveSystemDefaults("admin", 0L, Map.of(
                    DashboardMetricField.DECISION_RESULT, new DashboardMapping.StoredMapping(VARIABLE, 500L, INPUT, values))));
        }
    }

    private RuleVariable constant(Long id, String code, String value) {
        var constant = variable(id, 0L, "GLOBAL", code, "STRING");
        constant.setVarSource("CONSTANT");
        constant.setDefaultValue(value);
        return constant;
    }
    @Test
    public void newAccountMustNotGuessReferencesFromLegacySeedIds() {
        var mappings = service(new HashMap<>(), repository(
                variable(3L, 0L, "GLOBAL", "EMPTY_STRING", "STRING")))
                .resolve(8L, null);
        assertFalse(mappings.get(DashboardMetricField.REQUEST_ID).valid());
        assertEquals(null, mappings.get(DashboardMetricField.REQUEST_ID).refId());
    }

    @Test
    public void projectFieldsFallBackIndependentlyToUserThenSystemDefaults() {
        Map<String, String> preferences = new HashMap<>();
        preferences.put("7:DASHBOARD_MAPPING", "{"
                + "\"schemaVersion\":1,"
                + "\"defaults\":{\"AMOUNT\":{\"refType\":\"VARIABLE\",\"refId\":88,\"jsonSource\":\"INPUT\"}},"
                + "\"projects\":{\"9\":{\"PERIOD\":{\"refType\":\"VARIABLE\",\"refId\":99,\"jsonSource\":\"INPUT\"}}}} ");
        DashboardMappingRepository references = repository("{\"LONGITUDE\":{\"refType\":\"VARIABLE\",\"refId\":73,\"jsonSource\":\"INPUT\"}}",
                variable(73L, 0L, "GLOBAL", "gps_longitude", "NUMBER"),
                variable(88L, 0L, "GLOBAL", "loan_amount", "NUMBER"),
                variable(99L, 9L, "PROJECT", "loan_period", "NUMBER"));

        DashboardMapping.ResolvedMappings mappings = service(
                preferences, references).resolve(7L, 9L);

        assertEquals(Long.valueOf(99L), mappings.get(PERIOD).refId());
        assertEquals(DashboardMapping.MappingSource.PROJECT_OVERRIDE,
                mappings.get(PERIOD).mappingSource());
        assertEquals(Long.valueOf(88L), mappings.get(AMOUNT).refId());
        assertEquals(DashboardMapping.MappingSource.USER_DEFAULT,
                mappings.get(AMOUNT).mappingSource());
        assertEquals(Long.valueOf(73L), mappings.get(LONGITUDE).refId());
        assertEquals(DashboardMapping.MappingSource.SYSTEM_DEFAULT,
                mappings.get(LONGITUDE).mappingSource());
    }

    @Test
    public void invalidExplicitReferenceReportsConfigurationErrorWithoutGuessingFallback() {
        Map<String, String> preferences = new HashMap<>();
        preferences.put("7:DASHBOARD_MAPPING", "{"
                + "\"schemaVersion\":1,"
                + "\"defaults\":{\"AMOUNT\":{\"refType\":\"VARIABLE\",\"refId\":88,\"jsonSource\":\"INPUT\"}},"
                + "\"projects\":{\"9\":{\"AMOUNT\":{\"refType\":\"VARIABLE\",\"refId\":100,\"jsonSource\":\"INPUT\"}}}} ");
        DashboardMappingRepository references = repository(
                variable(88L, 0L, "GLOBAL", "loan_amount", "NUMBER"),
                variable(100L, 10L, "PROJECT", "wrong_project_amount", "NUMBER"));

        DashboardMapping.ResolvedMapping amount = service(
                preferences, references).resolve(7L, 9L).get(AMOUNT);

        assertFalse(amount.valid());
        assertEquals(Long.valueOf(100L), amount.refId());
        assertEquals("字段引用不属于当前项目", amount.errorMessage());
    }

    @Test
    public void personalDefaultsRejectProjectScopedReferences() {
        Map<String, String> preferences = new HashMap<>();
        DashboardMappingRepository references = repository(
                variable(99L, 9L, "PROJECT", "loan_period", "NUMBER"));
        DashboardMapping.StoredMapping mapping = new DashboardMapping.StoredMapping(
                VARIABLE, 99L, INPUT, null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service(preferences, references).saveScope(
                        7L, "alice", null, Map.of(PERIOD, mapping)));

        assertEquals("默认配置只能引用全局字段", error.getMessage());
    }

    @Test
    public void sharedDefaultsApplyToOtherAccountsAndPreservePersonalOverrides() {
        Map<String, String> preferences = new HashMap<>();
        preferences.put("7:DASHBOARD_MAPPING", "{\"schemaVersion\":1,\"defaults\":{\"AMOUNT\":{\"refType\":\"VARIABLE\",\"refId\":88,\"jsonSource\":\"INPUT\"}}}");
        var shared = variable(200L, 0L, "GLOBAL", "public_amount", "NUMBER");
        var references = repository(shared, variable(88L, 0L, "GLOBAL", "personal_amount", "NUMBER"));
        var service = service(preferences, references);
        var result = service.saveSystemDefaults("admin", 0L, Map.of(AMOUNT,
                new DashboardMapping.StoredMapping(VARIABLE, 200L, INPUT, null)));
        assertEquals(Long.valueOf(1), result.defaultVersion());
        assertEquals(Long.valueOf(200), service.resolve(8L, null).get(AMOUNT).refId());
        assertEquals(Long.valueOf(88), service.resolve(7L, null).get(AMOUNT).refId());
        shared.setVarCode("RenamedAmount");
        assertEquals("$.\"RenamedAmount\"", service.resolve(8L, null).get(AMOUNT).jsonPath());
        assertEquals(null, preferences.get("8:DASHBOARD_MAPPING"));
        assertThrows(IllegalArgumentException.class, () -> service.saveSystemDefaults("admin", 0L, Map.of()));
        service.saveSystemDefaults("admin", 1L, Map.of());
        assertEquals(null, service.resolve(8L, null).get(AMOUNT).refId());
        assertEquals(Long.valueOf(88), service.resolve(7L, null).get(AMOUNT).refId());
    }

    @Test
    public void sharedDefaultsCannotExposeHiddenReferencesOrUseProjectFields() {
        var references = repository("{\"AMOUNT\":{\"refType\":\"VARIABLE\",\"refId\":200,\"jsonSource\":\"INPUT\"}}",
                variable(99L, 9L, "PROJECT", "project_amount", "NUMBER"));
        var service = service(new HashMap<>(), references);
        var hidden = service.resolve(8L, null).get(AMOUNT);
        assertFalse(hidden.valid());
        assertEquals(null, hidden.jsonPath());
        assertEquals(null, hidden.referenceLabel());
        assertThrows(IllegalArgumentException.class, () -> service.saveSystemDefaults("admin", 0L,
                Map.of(AMOUNT, new DashboardMapping.StoredMapping(VARIABLE, 99L, INPUT, null))));
        assertEquals(0, references.findSystemDefaults().version());
    }

    private DashboardMappingService service(
            Map<String, String> preferences,
            DashboardMappingRepository references) {
        ConsoleUserPreferenceMapper mapper = (ConsoleUserPreferenceMapper)
                Proxy.newProxyInstance(
                        ConsoleUserPreferenceMapper.class.getClassLoader(),
                        new Class<?>[]{ConsoleUserPreferenceMapper.class},
                        (proxy, method, args) -> {
                            if ("findValue".equals(method.getName())) {
                                return preferences.get(key(args[0], args[1]));
                            }
                            if ("upsertValue".equals(method.getName())) {
                                preferences.put(key(args[0], args[1]),
                                        String.valueOf(args[2]));
                                return 1;
                            }
                            if ("deleteValue".equals(method.getName())) {
                                preferences.remove(key(args[0], args[1]));
                                return 1;
                            }
                            if ("toString".equals(method.getName())) {
                                return "DashboardPreferenceMapper";
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
        return new DashboardMappingService(
                new ConsoleUserPreferenceService(mapper), references);
    }

    private DashboardMappingRepository repository(RuleVariable... variables) {
        return repository("{}", variables);
    }

    private DashboardMappingRepository repository(String defaults, RuleVariable... variables) {
        Map<Long, RuleVariable> values = new HashMap<>();
        for (RuleVariable variable : variables) values.put(variable.getId(), variable);
        return new DashboardMappingRepository() {
            private String storedDefaults = defaults;
            private long version;
            @Override public SystemDefaults findSystemDefaults() { return new SystemDefaults(storedDefaults, version); }
            @Override public void saveSystemDefaults(String json, long expected, String actor) {
                if (expected != version) throw new IllegalArgumentException("版本已更新");
                storedDefaults = json;
                version++;
            }
            @Override
            public RuleVariable findVariable(Long id) {
                return values.get(id);
            }
        };
    }

    private RuleVariable variable(Long id, Long projectId, String scope,
                                  String code, String type) {
        RuleVariable variable = new RuleVariable();
        variable.setId(id);
        variable.setProjectId(projectId);
        variable.setScope(scope);
        variable.setVarCode(code);
        variable.setVarLabel(code);
        variable.setVarType(type);
        variable.setStatus(1);
        return variable;
    }

    private String key(Object userId, Object preferenceKey) {
        return userId + ":" + preferenceKey;
    }
}
