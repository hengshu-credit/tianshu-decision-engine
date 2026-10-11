package com.hengshucredit.rule.server.controller.mgmt;

import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.Assert.assertEquals;

/** 新建试运行是独立会话入口，不能依赖一个虚构的正式 API ID。 */
public class ExternalApiTrialControllerTest {
    @Test public void formalBillingFailureKeepsObservedResponseWithFailureEnvelope() {
        var controller = new ExternalDatasourceController();
        var evidence = java.util.Map.<String, Object>of("success", false, "sourceOutcome", "UNKNOWN", "httpStatus", 200,
                "body", java.util.Map.of("score", 88), "errorPhase", "BILLING");
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "externalApiInvokeService",
                new com.hengshucredit.rule.server.service.ExternalApiInvokeService() {
                    @Override public java.util.Map<String, Object> invoke(Long id, java.util.Map<String, Object> params) {
                        throw new BillingPersistenceException("计费未确认", new IllegalStateException(), evidence);
                    }
                });
        var response = controller.invokeApiConfig(7L, java.util.Map.of());
        assertEquals(500, response.getCode());
        assertEquals(evidence, response.getData());
    }

    @Test
    public void rejectsNonObjectTrialParamsBeforeAnyInvocation() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ExternalDatasourceController()).build();
        var response = mvc.perform(post("/api/rule/datasource/api-config/trials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestKey\":\"531f0726-7b4c-460e-918a-b144cba5d9ac\",\"config\":{},\"params\":[]}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        var payload = com.alibaba.fastjson.JSON.parseObject(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(500, payload.getIntValue("code"));
        assertEquals("试运行参数必须是 JSON 对象", payload.getString("message"));
    }
}
