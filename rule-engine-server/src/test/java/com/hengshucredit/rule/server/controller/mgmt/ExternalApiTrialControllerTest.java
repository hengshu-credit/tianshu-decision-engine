package com.hengshucredit.rule.server.controller.mgmt;

import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.Assert.assertEquals;

/** 新建试运行是独立会话入口，不能依赖一个虚构的正式 API ID。 */
public class ExternalApiTrialControllerTest {
    @Test public void invocationDistinguishesInvalidInputFromProviderFailure() {
        for (RuntimeException failure : java.util.List.of(new IllegalArgumentException("请求字段缺失"),
                new IllegalStateException("供应商不可用"))) {
            var controller = new ExternalDatasourceController();
            org.springframework.test.util.ReflectionTestUtils.setField(controller, "externalApiInvokeService",
                    new com.hengshucredit.rule.server.service.ExternalApiInvokeService() {
                        @Override public java.util.Map<String, Object> invoke(Long id, java.util.Map<String, Object> params) {
                            throw failure;
                        }
                    });
            var response = controller.invokeApiConfig(7L, java.util.Map.of());
            assertEquals(failure instanceof IllegalArgumentException ? "100001" : "601001", response.getCode());
            assertEquals(failure.getMessage(), response.getDetail());
        }
    }

    @Test public void missingApiConfigIsARequiredFieldFailure() {
        var response = new ExternalDatasourceController().invokeApiConfigPreview(7L, java.util.Map.of());
        assertEquals("100002", response.getCode());
        assertEquals("API接口配置不能为空", response.getDetail());
    }

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
        assertEquals("900001", response.getCode());
        assertEquals(evidence, response.getData());
    }

    @Test
    public void rejectsNonObjectTrialParamsBeforeAnyInvocation() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ExternalDatasourceController())
                .setControllerAdvice(new com.hengshucredit.rule.server.common.PlatformResponseAdvice()).build();
        var response = mvc.perform(post("/api/rule/datasource/api-config/trials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestKey\":\"531f0726-7b4c-460e-918a-b144cba5d9ac\",\"config\":{},\"params\":[]}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse();
        var payload = com.alibaba.fastjson.JSON.parseObject(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("100001", payload.getString("code"));
        assertEquals("试运行参数必须是 JSON 对象", payload.getString("detail"));
    }
}
