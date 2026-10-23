package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleSyncControllerTest {
    @Test public void syncPayloadCarriesArtifactFunctionDefinitionsIncludingKnownEmptySnapshot() {
        RuleSyncController controller = new RuleSyncController();
        var definition = new com.hengshucredit.rule.model.entity.RuleDefinition();
        definition.setId(10L); definition.setProjectId(1L);
        ReflectionTestUtils.setField(controller, "definitionService", new RuleDefinitionService() {
            @Override public com.hengshucredit.rule.model.entity.RuleDefinition getById(java.io.Serializable id) { return definition; }
        });
        var snapshot = new com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService.RuntimeSnapshot();
        var frozen = new com.hengshucredit.rule.model.entity.RuleFunction();
        frozen.setId(7L); frozen.setFuncCode("frozenScore"); frozen.setImplType("JAVA"); frozen.setImplMethod("oldVersion");
        snapshot.getFunctions().add(frozen);
        ReflectionTestUtils.setField(controller, "artifactSnapshotService", new com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService() {
            @Override public RuntimeSnapshot load(Long artifactId, Long definitionId, Long projectId) { return snapshot; }
        });
        RulePublished published = new RulePublished(); published.setDefinitionId(10L); published.setArtifactId(20L);
        published.setRevisionId(30L); published.setArtifactDigest("selected-artifact");
        RulePublished enriched = ReflectionTestUtils.invokeMethod(controller, "withOutputScriptNames", published);
        com.alibaba.fastjson.JSONObject payload = com.alibaba.fastjson.JSON.parseObject(com.alibaba.fastjson.JSON.toJSONString(enriched));
        assertTrue("规则同步必须携带选定制品的函数定义", payload.get("frozenFunctions") instanceof List<?>);
        assertEquals("oldVersion", payload.getJSONArray("frozenFunctions").getJSONObject(0).getString("implMethod"));
        snapshot.getFunctions().clear();
        enriched = ReflectionTestUtils.invokeMethod(controller, "withOutputScriptNames", published);
        payload = com.alibaba.fastjson.JSON.parseObject(com.alibaba.fastjson.JSON.toJSONString(enriched));
        assertEquals("冻结空函数集不能退回实时函数", List.of(), payload.get("frozenFunctions"));
    }


    @Test
    public void executionHonorsTraceChoiceAndKeepsDefaultEnabled() {
        RuleSyncController controller = new RuleSyncController();
        RulePublished published = new RulePublished();
        published.setDefinitionId(10L);
        com.hengshucredit.rule.server.mapper.RulePublishedMapper mapper =
                (com.hengshucredit.rule.server.mapper.RulePublishedMapper) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[]{com.hengshucredit.rule.server.mapper.RulePublishedMapper.class},
                        (proxy, method, args) -> published);
        ReflectionTestUtils.setField(controller, "publishedMapper", mapper);
        boolean[] flags = new boolean[2];
        ReflectionTestUtils.setField(controller, "executeService",
                new com.hengshucredit.rule.server.service.RuleExecuteService() {
                    @Override
                    public com.hengshucredit.rule.model.dto.RuleResult executePublished(
                            RulePublished rule, Map<String, Object> params, Long projectId, String appName,
                            com.hengshucredit.rule.server.auth.ProjectAuthContext auth,
                            boolean collectTrace, boolean recordTrace) {
                        assertEquals(Long.valueOf(1L), projectId);
                        assertEquals("business-service", appName);
                        assertEquals(18, params.get("age"));
                        flags[0] = collectTrace;
                        flags[1] = recordTrace;
                        return new com.hengshucredit.rule.model.dto.RuleResult();
                    }
                });
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute("projectId", 1L);
        request.setAttribute("projectCode", "credit");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("params", Collections.singletonMap("age", 18));
        body.put("clientAppName", "business-service");
        for (Object value : new Object[]{null, false, true, "false", "true"}) {
            body.put("traceEnabled", value);
            controller.execute("RISK", body, request);
            boolean expected = value == null || Boolean.parseBoolean(value.toString());
            assertEquals(expected, flags[0]);
            assertEquals(expected, flags[1]);
        }
        for (Object invalid : new Object[]{0, "off", Collections.singletonList(false)}) {
            body.put("traceEnabled", invalid);
            assertEquals("100001", controller.execute("RISK", body, request).getCode());
        }
    }

    @Test
    public void enrichesPublishedRuleWithOrderedRootOutputScriptNames() {
        RuleSyncController controller = new RuleSyncController();
        ReflectionTestUtils.setField(controller, "definitionService", new RuleDefinitionService() {
            @Override
            public List<RuleDefinitionOutputField> listOutputFields(Long definitionId) {
                RuleDefinitionOutputField decision = new RuleDefinitionOutputField();
                decision.setScriptName("decision");
                RuleDefinitionOutputField empty = new RuleDefinitionOutputField();
                empty.setScriptName(" ");
                RuleDefinitionOutputField notAssigned = new RuleDefinitionOutputField();
                notAssigned.setScriptName("notAssigned");
                return Arrays.asList(decision, empty, notAssigned);
            }
        });
        RulePublished published = new RulePublished();
        published.setDefinitionId(10L);
        published.setRevisionId(22L);
        published.setArtifactDigest("artifact-digest");

        RulePublished enriched = ReflectionTestUtils.invokeMethod(
                controller, "withOutputScriptNames", published);

        assertEquals(Arrays.asList("decision", "notAssigned"), enriched.getOutputScriptNames());
        assertEquals(Long.valueOf(22L), enriched.getRevisionId());
        assertEquals("artifact-digest", enriched.getArtifactDigest());
    }

    @Test
    public void allowsFunctionSyncOnlyForTokenProject() {
        assertTrue(RuleSyncController.isAuthorizedProject(100L, 100L));
    }

    @Test
    public void rejectsFunctionSyncForOtherProject() {
        assertFalse(RuleSyncController.isAuthorizedProject(101L, 100L));
    }

    @Test
    public void rejectsFunctionSyncWhenProjectIdMissing() {
        assertFalse(RuleSyncController.isAuthorizedProject(null, 100L));
        assertFalse(RuleSyncController.isAuthorizedProject(100L, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void multipartBodyRestoresNestedParamsAndFiles() {
        Map<String, String[]> fields = new LinkedHashMap<>();
        fields.put("clientAppName", new String[]{"api-doc-browser"});
        fields.put("params.customer.age", new String[]{"18"});
        fields.put("params.tags", new String[]{"a", "b"});
        MockMultipartFile file = new MockMultipartFile(
                "params.contract", "contract.txt", "text/plain",
                "demo".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> body = RuleSyncController.buildMultipartBody(
                fields, Collections.singletonMap("params.contract", file));

        assertEquals("api-doc-browser", body.get("clientAppName"));
        Map<String, Object> params = (Map<String, Object>) body.get("params");
        Map<String, Object> customer = (Map<String, Object>) params.get("customer");
        assertEquals("18", customer.get("age"));
        assertEquals(Arrays.asList("a", "b"), params.get("tags"));
        assertEquals(file, params.get("contract"));
    }
}
