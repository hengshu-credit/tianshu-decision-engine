package com.hengshucredit.rule.server.controller.mgmt;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.server.service.RuleExternalApiConfigService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class ExternalApiBindingContractTest {
    @Test public void availableDirectoryRouteUsesTheRequestedProjectAndKeepsStringIds() throws Exception {
        var controller = new ExternalDatasourceController();
        ReflectionTestUtils.setField(controller, "sourceReferenceValidator", new com.hengshucredit.rule.server.governance.VariableSourceReferenceValidator() {
            @Override public List<Map<String, Object>> apiReferenceCatalog(Long projectId) {
                assertEquals(Long.valueOf(7), projectId);
                return List.of(Map.of("id", "9007199254740993", "apiCode", "GlobalCredit", "resultFields", List.of()));
            }
        });
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/rule/datasource/api-config/available").param("projectId", "7"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals("9007199254740993", JSON.parseObject(response).getJSONArray("data").getJSONObject(0).get("id"));
    }
    private ExternalDatasourceController controller(RuleExternalApiConfig api) {
        var controller = new ExternalDatasourceController();
        ReflectionTestUtils.setField(controller, "apiConfigService", new RuleExternalApiConfigService() {
            @Override public RuleExternalApiConfig getById(Serializable id) { return api; }
        });
        return controller;
    }

    @Test public void v2ContractExposesRequestAndResponseFieldsWithoutLegacyInputs() {
        var api = new RuleExternalApiConfig(); api.setId(7L); api.setStatus(1); api.setApiName("画像查询");
        api.setExecutionConfig(JSON.toJSONString(Map.of("version", 2,
                "requestFields", List.of(Map.of("id", "phone", "path", "customer.mobile", "location", "JSON", "overridable", true)),
                "responseBranches", List.of(Map.of("id", "success", "sample", Map.of("score", 80), "mode", "FIELDS",
                        "outputFields", List.of(Map.of("id", "score", "path", "score", "value", Map.of("kind", "PATH", "value", "response.body.score", "protocolPath", true))))))));
        var result = controller(api).bindingContract(7L).getData();
        assertEquals("FIELDS", result.get("bindingMode"));
        assertFalse(result.containsKey("inputFields"));
        assertEquals("customer.mobile", JSON.parseArray(JSON.toJSONString(result.get("requestFields"))).getJSONObject(0).getString("path"));
        assertTrue(JSON.toJSONString(result.get("resultFields")).contains("body.score"));
    }

    @Test public void lockedFieldsStayVisibleAndHeadersDoNotExposeDefaults() {
        var api = new RuleExternalApiConfig(); api.setId(7L); api.setStatus(1);
        api.setExecutionConfig(JSON.toJSONString(Map.of("version", 2, "requestFields", List.of(
                Map.of("id", "token", "path", "Authorization", "location", "HEADER", "overridable", true,
                        "value", Map.of("kind", "LITERAL", "value", "private-token")),
                Map.of("id", "locked", "path", "score", "location", "JSON", "overridable", false)),
                "steps", List.of(Map.of("id", "step1", "name", "提交", "type", "HTTP", "authApiConfig", "private-config")))));
        var result = controller(api).bindingContract(7L).getData();
        assertEquals("FIELDS", result.get("bindingMode"));
        var fields = JSON.parseArray(JSON.toJSONString(result.get("requestFields")));
        assertEquals(2, fields.size());
        assertFalse(fields.getJSONObject(0).getBooleanValue("overridable"));
        assertFalse(fields.getJSONObject(0).containsKey("value"));
        assertFalse(JSON.toJSONString(result).contains("private-"));
        assertTrue(api.getExecutionConfig().contains("private-token"));
    }
}
