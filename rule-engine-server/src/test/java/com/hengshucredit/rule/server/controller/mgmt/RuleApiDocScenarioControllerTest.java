package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.ApiDocScenarioCopyRequest;
import com.hengshucredit.rule.model.dto.ApiDocScenarioSaveRequest;
import com.hengshucredit.rule.model.dto.ApiDocScenarioSortRequest;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleApiDocScenario;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleApiDocScenarioService;
import com.hengshucredit.rule.server.service.RuleExecuteService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class RuleApiDocScenarioControllerTest {

    private RuleApiDocScenarioController controller;
    private FakeScenarioService scenarioService;
    private FakeExecuteService executeService;

    @Before
    public void setUp() {
        controller = new RuleApiDocScenarioController();
        scenarioService = new FakeScenarioService();
        executeService = new FakeExecuteService();
        ReflectionTestUtils.setField(controller, "scenarioService", scenarioService);
        ReflectionTestUtils.setField(controller, "executeService", executeService);
    }

    @Test
    public void listAndCreateUseDefinitionFromPath() {
        RuleApiDocScenario existing = new RuleApiDocScenario();
        existing.setId(11L);
        scenarioService.scenarios = Collections.singletonList(existing);
        ApiDocScenarioSaveRequest request = new ApiDocScenarioSaveRequest();

        R<List<RuleApiDocScenario>> listResponse = controller.list(9L);
        R<RuleApiDocScenario> createResponse = controller.create(9L, request);

        assertSame(scenarioService.scenarios, listResponse.getData());
        assertSame(scenarioService.saved, createResponse.getData());
        assertEquals(Long.valueOf(9L), scenarioService.definitionId);
        assertSame(request, scenarioService.saveRequest);
    }

    @Test
    public void updateCopySortAndDeleteDelegateScopedIdentifiers() {
        ApiDocScenarioSaveRequest saveRequest = new ApiDocScenarioSaveRequest();
        ApiDocScenarioCopyRequest copyRequest = new ApiDocScenarioCopyRequest();
        copyRequest.setScenarioName("复制场景");
        ApiDocScenarioSortRequest sortRequest = new ApiDocScenarioSortRequest();
        sortRequest.setScenarioIds(Arrays.asList(13L, 12L));

        controller.update(9L, 12L, saveRequest);
        controller.copy(9L, 12L, copyRequest);
        controller.sort(9L, sortRequest);
        controller.delete(9L, 12L);

        assertEquals(Long.valueOf(9L), scenarioService.definitionId);
        assertEquals(Long.valueOf(12L), scenarioService.scenarioId);
        assertEquals("复制场景", scenarioService.copyName);
        assertEquals(Arrays.asList(13L, 12L), scenarioService.sortedIds);
        assertEquals(Long.valueOf(12L), scenarioService.deletedScenarioId);
    }

    @Test
    public void executeReturnsFullPlatformEnvelope() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientAppName", "api-doc-example");
        body.put("params", Collections.singletonMap("age", 17));
        RuleResult ruleResult = new RuleResult();
        ruleResult.setSuccess(true);
        executeService.result = ruleResult;

        R<RuleResult> response = controller.execute(9L, body);

        assertEquals(200, response.getCode());
        assertEquals("success", response.getMessage());
        assertSame(ruleResult, response.getData());
        assertEquals(Long.valueOf(9L), executeService.definitionId);
        assertEquals(17, executeService.params.get("age"));
        assertEquals(1, executeService.params.size());
    }

    @Test
    public void invalidServiceRequestReturnsBadRequestEnvelope() {
        scenarioService.error = new IllegalArgumentException("请求报文不是合法 JSON");

        R<RuleApiDocScenario> response = controller.create(9L, new ApiDocScenarioSaveRequest());

        assertEquals(400, response.getCode());
        assertEquals("请求报文不是合法 JSON", response.getMessage());
    }

    private static class FakeScenarioService extends RuleApiDocScenarioService {
        private Long definitionId;
        private Long scenarioId;
        private Long deletedScenarioId;
        private String copyName;
        private List<Long> sortedIds;
        private List<RuleApiDocScenario> scenarios = Collections.emptyList();
        private final RuleApiDocScenario saved = new RuleApiDocScenario();
        private ApiDocScenarioSaveRequest saveRequest;
        private IllegalArgumentException error;

        @Override
        public List<RuleApiDocScenario> listByDefinition(Long definitionId) {
            failIfNeeded();
            this.definitionId = definitionId;
            return scenarios;
        }

        @Override
        public RuleApiDocScenario create(Long definitionId, ApiDocScenarioSaveRequest request) {
            failIfNeeded();
            this.definitionId = definitionId;
            this.saveRequest = request;
            return saved;
        }

        @Override
        public RuleApiDocScenario update(Long definitionId, Long scenarioId,
                                         ApiDocScenarioSaveRequest request) {
            failIfNeeded();
            this.definitionId = definitionId;
            this.scenarioId = scenarioId;
            this.saveRequest = request;
            return saved;
        }

        @Override
        public RuleApiDocScenario copy(Long definitionId, Long scenarioId, String scenarioName) {
            failIfNeeded();
            this.definitionId = definitionId;
            this.scenarioId = scenarioId;
            this.copyName = scenarioName;
            return saved;
        }

        @Override
        public void sort(Long definitionId, List<Long> scenarioIds) {
            failIfNeeded();
            this.definitionId = definitionId;
            this.sortedIds = scenarioIds;
        }

        @Override
        public void delete(Long definitionId, Long scenarioId) {
            failIfNeeded();
            this.definitionId = definitionId;
            this.deletedScenarioId = scenarioId;
        }

        private void failIfNeeded() {
            if (error != null) throw error;
        }
    }

    private static class FakeExecuteService extends RuleExecuteService {
        private Long definitionId;
        private Map<String, Object> params;
        private RuleResult result;

        @Override
        public RuleResult testExecute(Long definitionId, Map<String, Object> params) {
            this.definitionId = definitionId;
            this.params = params;
            return result;
        }
    }
}
