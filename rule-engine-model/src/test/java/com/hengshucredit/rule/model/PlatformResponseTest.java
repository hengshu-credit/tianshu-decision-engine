package com.hengshucredit.rule.model;

import org.junit.Test;
import java.util.HashSet;
import java.util.Map;
import static org.junit.Assert.*;

public class PlatformResponseTest {
    @Test public void failureCannotUseTheSuccessCode() {
        assertThrows(IllegalArgumentException.class, () -> ApiResponse.fail(PlatformCode.SUCCESS, "失败"));
    }
    @Test public void executionRequestValidationIsSharedAndPreservesFieldNames() {
        var missing = assertThrows(PlatformException.class, () -> RuleExecutionRequest.fromBody(Map.of()));
        assertEquals("100002", missing.getCode());
        var invalid = assertThrows(PlatformException.class, () -> RuleExecutionRequest.fromBody(Map.of("params", java.util.List.of())));
        assertEquals("100001", invalid.getCode());
        var request = RuleExecutionRequest.fromBody(Map.of("params", Map.of("Age_Value", 18), "traceEnabled", "false"));
        assertEquals(Map.of("Age_Value", 18), request.getParams());
        assertEquals(Boolean.FALSE, request.getTraceEnabled());
    }
    @Test public void codesAreUniqueAndOnlyOneCodeMeansSuccess() {
        var codes = new HashSet<String>();
        for (PlatformCode code : PlatformCode.values()) {
            assertTrue(code.code().matches("[0-9]{6}"));
            assertTrue("duplicate " + code, codes.add(code.code()));
            assertFalse(code.message().isBlank());
            assertSame(code, PlatformCode.find(code.code()));
            if (code != PlatformCode.SUCCESS) assertFalse(code.code().startsWith("0"));
        }
        assertEquals("000000", PlatformCode.SUCCESS.code());
    }

    @Test public void businessRejectionIsStillSuccessfulExecutionAndIsNotInspected() {
        var body = ApiResponse.ok(Map.of("success", false, "code", "DECLINED"));
        assertEquals("000000", body.getCode());
        assertTrue(body.isSuccess());
    }

    @Test public void errorsKeepCanonicalMessageAndSeparateDetails() {
        var body = ApiResponse.fail(PlatformCode.PARAMETER_INVALID, "年龄必须大于 0");
        assertEquals("100001", body.getCode());
        assertEquals("入参校验失败", body.getMessage());
        assertEquals("年龄必须大于 0", body.getDetail());
        assertEquals(400, body.status().httpStatus());
    }

    @Test public void waitingIsNotSuccessAndHasTheSameCodeInEveryTransport() {
        PlatformOutcome outcome = new PlatformOutcome() {
            public PlatformCode platformStatus() { return PlatformCode.WAITING_EXTERNAL; }
            public String platformDetail() { return "任务等待回调"; }
            public String platformTraceId() { return "trace-1"; }
        };
        var body = ApiResponse.ok(outcome);
        assertFalse(body.isSuccess());
        assertEquals("400006", body.getCode());
        assertEquals(202, body.status().httpStatus());
        assertEquals("trace-1", body.getTraceId());
    }
}
