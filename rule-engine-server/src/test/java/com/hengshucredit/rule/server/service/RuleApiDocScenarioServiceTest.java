package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ApiDocScenarioSaveRequest;
import com.hengshucredit.rule.model.entity.RuleApiDocScenario;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.server.mapper.RuleApiDocScenarioMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class RuleApiDocScenarioServiceTest {

    private RuleApiDocScenarioService service;
    private RecordingScenarioMapper scenarioHandler;

    @Before
    public void setUp() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(7L);
        definition.setCurrentVersion(4);
        definition.setPublishedVersion(3);

        scenarioHandler = new RecordingScenarioMapper();
        service = new RuleApiDocScenarioService();
        ReflectionTestUtils.setField(service, "scenarioMapper",
                mapper(RuleApiDocScenarioMapper.class, scenarioHandler));
        ReflectionTestUtils.setField(service, "definitionMapper",
                mapper(RuleDefinitionMapper.class, (proxy, method, args) -> {
                    if ("selectById".equals(method.getName()) && Long.valueOf(7L).equals(args[0])) {
                        return definition;
                    }
                    return defaultValue(method.getReturnType());
                }));
    }

    @Test
    public void createCapturesVersionAndExtractsCodes() {
        String requestJson = "{\"clientAppName\":\"api-doc-example\",\"params\":{\"age\":17}}";
        String responseJson = "{\"code\":200,\"message\":\"success\",\"data\":{\"success\":true,"
                + "\"result\":{\"code\":\"REJECT\"}}}";
        ApiDocScenarioSaveRequest request = request("风险拒绝", requestJson, responseJson);
        request.setBusinessCodePath("data.result.code");

        RuleApiDocScenario saved = service.create(7L, request);

        assertEquals(Integer.valueOf(4), saved.getRuleVersion());
        assertEquals(Integer.valueOf(200), saved.getOuterCode());
        assertEquals("REJECT", saved.getBusinessCode());
        assertEquals(requestJson, saved.getRequestJson());
        assertEquals(responseJson, saved.getResponseJson());
    }

    @Test
    public void createDoesNotInferBusinessCodeWithoutConfiguredPath() {
        ApiDocScenarioSaveRequest request = request("通过", "{}",
                "{\"code\":200,\"data\":{\"result\":{\"code\":\"PASS\"}}}");

        RuleApiDocScenario saved = service.create(7L, request);

        assertNull(saved.getBusinessCodePath());
        assertNull(saved.getBusinessCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createRejectsNonObjectRequestJson() {
        service.create(7L, request("非法", "[]", "{\"code\":200}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void createRejectsUnsupportedResponseSource() {
        ApiDocScenarioSaveRequest request = request("非法来源", "{}", "{\"code\":200}");
        request.setResponseSource("AUTO");
        service.create(7L, request);
    }

    @Test
    public void exportOnlyReturnsEnabledSelectedPublishedVersion() {
        RuleApiDocScenario published = scenario(31L, 7L, "通过", 3);
        scenarioHandler.selectListResult = Collections.singletonList(published);

        List<RuleApiDocScenario> result = service.listExportable(7L, 3);

        assertEquals(1, result.size());
        assertEquals("通过", result.get(0).getScenarioName());
    }

    @Test
    public void exportReturnsEmptyWhenRuleIsNotPublished() {
        assertEquals(Collections.emptyList(), service.listExportable(7L, null));
        assertFalse(scenarioHandler.selectListCalled);
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateRejectsScenarioFromAnotherRule() {
        scenarioHandler.byId.put(21L, scenario(21L, 7L, "拒绝", 4));
        service.update(8L, 21L, request("拒绝", "{}", "{\"code\":200}"));
    }

    @Test
    public void copyUsesCurrentVersionAndDoesNotSelectDocumentByDefault() {
        RuleApiDocScenario source = scenario(21L, 7L, "拒绝", 2);
        source.setRequestJson("{\"params\":{\"age\":17}}");
        source.setResponseJson("{\"code\":200}");
        source.setIncludeInDoc(1);
        scenarioHandler.byId.put(21L, source);

        RuleApiDocScenario copied = service.copy(7L, 21L, "拒绝-副本");

        assertEquals("拒绝-副本", copied.getScenarioName());
        assertEquals(Integer.valueOf(4), copied.getRuleVersion());
        assertEquals(Integer.valueOf(0), copied.getIncludeInDoc());
    }

    @Test
    public void sortOnlyUpdatesScenariosBelongingToDefinition() {
        scenarioHandler.byId.put(11L, scenario(11L, 7L, "场景一", 4));
        scenarioHandler.byId.put(12L, scenario(12L, 7L, "场景二", 4));

        service.sort(7L, Arrays.asList(12L, 11L));

        assertEquals(Integer.valueOf(0), scenarioHandler.byId.get(12L).getSortOrder());
        assertEquals(Integer.valueOf(1), scenarioHandler.byId.get(11L).getSortOrder());
        assertEquals(2, scenarioHandler.updated.size());
    }

    private static ApiDocScenarioSaveRequest request(String name, String requestJson, String responseJson) {
        ApiDocScenarioSaveRequest request = new ApiDocScenarioSaveRequest();
        request.setScenarioName(name);
        request.setRequestJson(requestJson);
        request.setResponseJson(responseJson);
        request.setResponseSource("MANUAL");
        request.setIncludeInDoc(0);
        request.setStatus(1);
        return request;
    }

    private static RuleApiDocScenario scenario(Long id, Long definitionId, String name, int version) {
        RuleApiDocScenario scenario = new RuleApiDocScenario();
        scenario.setId(id);
        scenario.setDefinitionId(definitionId);
        scenario.setScenarioName(name);
        scenario.setRuleVersion(version);
        scenario.setSortOrder(0);
        scenario.setStatus(1);
        scenario.setIncludeInDoc(1);
        return scenario;
    }

    private static class RecordingScenarioMapper implements InvocationHandler {
        private final Map<Long, RuleApiDocScenario> byId = new HashMap<>();
        private final List<RuleApiDocScenario> updated = new ArrayList<>();
        private List<RuleApiDocScenario> selectListResult = Collections.emptyList();
        private boolean selectListCalled;
        private long nextId = 100L;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "insert":
                    RuleApiDocScenario inserted = (RuleApiDocScenario) args[0];
                    if (inserted.getId() == null) inserted.setId(nextId++);
                    byId.put(inserted.getId(), inserted);
                    return 1;
                case "selectById":
                    return byId.get(args[0]);
                case "selectOne":
                    return null;
                case "selectList":
                    selectListCalled = true;
                    return selectListResult;
                case "updateById":
                    RuleApiDocScenario updatedScenario = (RuleApiDocScenario) args[0];
                    updated.add(updatedScenario);
                    byId.put(updatedScenario.getId(), updatedScenario);
                    return 1;
                case "deleteById":
                    return byId.remove(args[0]) == null ? 0 : 1;
                case "delete":
                    byId.clear();
                    return 1;
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }
}
