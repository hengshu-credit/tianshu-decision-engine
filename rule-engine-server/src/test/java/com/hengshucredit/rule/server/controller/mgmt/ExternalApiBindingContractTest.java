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
    private ExternalDatasourceController controller(RuleExternalApiConfig api) {
        var controller = new ExternalDatasourceController();
        ReflectionTestUtils.setField(controller, "apiConfigService", new RuleExternalApiConfigService() {
            @Override public RuleExternalApiConfig getById(Serializable id) { return api; }
        });
        return controller;
    }

    @Test public void legacyMappingsExposeInputPathsAndExactResponseNames() {
        var api = new RuleExternalApiConfig(); api.setId(7L); api.setStatus(1); api.setApiName("画像查询");
        api.setQueryConfig("{\"phone\":\"$.customer.mobile\"}");
        api.setRequestMapping("{\"limit\":\"$.limit\",\"phone\":\"$.customer.mobile\"}");
        api.setHeaderConfig("{\"Authorization\":\"$.secret\"}");
        api.setResponseMapping("{\"原始.字段\":\"$.score\"}");
        var result = controller(api).bindingContract(7L).getData();
        assertEquals("INPUT", result.get("bindingMode"));
        var inputs = JSON.parseArray(JSON.toJSONString(result.get("inputFields")));
        assertEquals(2, inputs.size());
        assertEquals("customer.mobile", inputs.getJSONObject(0).getString("id"));
        assertTrue(inputs.getJSONObject(0).getBooleanValue("overridable"));
        assertFalse(JSON.toJSONString(inputs).contains("secret"));
        assertTrue(JSON.toJSONString(result.get("resultFields")).contains("body[\\\"原始.字段\\\"]"));
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
