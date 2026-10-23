package com.hengshucredit.rule.server.common;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class PlatformResponseContractTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Endpoint())
            .setControllerAdvice(new GlobalExceptionHandler(), new PlatformResponseAdvice()).build();

    @Test public void businessDeclineIsSuccessButExecutionFailureIsNot() throws Exception {
        JSONObject success = body(get("/contract/business"), 200);
        assertEquals("000000", success.getString("code"));
        assertEquals("成功", success.getString("message"));
        assertEquals("REJECT", success.getJSONObject("data").getString("decision"));
        JSONObject failure = body(get("/contract/execution/FAILED"), 422);
        assertEquals("200001", failure.getString("code"));
        assertEquals("规则执行失败", failure.getString("message"));
        assertEquals("具体失败原因", failure.getString("detail"));
    }

    @Test public void waitingAndCapacityKeepTheirTransportAndTraceSemantics() throws Exception {
        JSONObject waiting = body(get("/contract/execution/WAITING_EXTERNAL"), 202);
        assertEquals("400006", waiting.getString("code"));
        assertEquals("root-trace", waiting.getString("traceId"));
        var response = mvc.perform(get("/contract/execution/THROTTLED")).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "2")).andReturn().getResponse();
        assertEquals("400001", JSON.parseObject(response.getContentAsString()).getString("code"));
    }

    @Test public void malformedJsonAndMissingResourcesUseTheSameEnvelope() throws Exception {
        assertEquals("100003", body(post("/contract/input").contentType("application/json").content("{"), 400).getString("code"));
        assertEquals("501001", body(get("/contract/missing"), 404).getString("code"));
    }

    @Test public void missingHeaderAndQueryParameterAreRequiredFieldErrors() throws Exception {
        assertEquals("100002", body(get("/contract/header"), 400).getString("code"));
        assertEquals("100002", body(get("/contract/query"), 400).getString("code"));
    }

    @Test public void unsupportedAcceptStillReceivesAPlatformJsonError() throws Exception {
        assertEquals("100008", body(get("/contract/business").accept("text/plain"), 406).getString("code"));
    }

    private JSONObject body(org.springframework.test.web.servlet.RequestBuilder request, int httpStatus) throws Exception {
        return JSON.parseObject(mvc.perform(request).andExpect(status().is(httpStatus)).andReturn()
                .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @RestController static class Endpoint {
        @GetMapping("/contract/header") public ApiResponse<?> header(@RequestHeader("X-Required") String value) { return ApiResponse.ok(value); }
        @GetMapping("/contract/query") public ApiResponse<?> query(@RequestParam String value) { return ApiResponse.ok(value); }
        @GetMapping("/contract/business") public ApiResponse<?> business() { return ApiResponse.ok(Map.of("decision", "REJECT")); }
        @GetMapping("/contract/missing") public ApiResponse<?> missing() { return ApiResponse.fail(PlatformCode.RESOURCE_NOT_FOUND, null); }
        @PostMapping("/contract/input") public ApiResponse<?> input(@RequestBody Map<String, Object> body) { return ApiResponse.ok(body); }
        @GetMapping("/contract/execution/{state}") public ApiResponse<?> execution(@PathVariable String state) {
            RuleResult result = new RuleResult();
            result.setSuccess(false);
            result.setExecutionStatus(state);
            result.setErrorMessage("具体失败原因");
            result.setTraceId("root-trace");
            result.setRetryAfterMs(1500L);
            return ApiResponse.ok(result);
        }
    }
}
