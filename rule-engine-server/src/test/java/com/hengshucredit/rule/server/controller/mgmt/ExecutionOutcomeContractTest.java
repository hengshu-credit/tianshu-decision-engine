package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.server.service.RuleFunctionService;
import com.hengshucredit.rule.server.service.ExternalApiInvokeService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.Assert.*;

public class ExecutionOutcomeContractTest {
    @Test public void failedFunctionTestKeepsDiagnosticsAndDoesNotReportSuccess() {
        var data = Map.<String, Object>of("success", false, "errorMessage", "除数为零", "traces", java.util.List.of());
        var service = new RuleFunctionService() {
            @Override public Map<String, Object> testFunction(Long id, Map<String, Object> params) { return data; }
        };
        var controller = new RuleFunctionController();
        ReflectionTestUtils.setField(controller, "functionService", service);
        var response = controller.test(1L, Map.of());
        assertFalse(response.isSuccess());
        assertEquals("204001", response.getCode());
        assertEquals("除数为零", response.getDetail());
        assertSame(data, response.getData());
    }

    @Test public void externalFallbackIsNotReportedAsSuccessfulSupplierCall() {
        var data = Map.<String, Object>of("success", false, "errorMessage", "供应商不可用", "fallback", true, "body", 0);
        var service = new ExternalApiInvokeService() {
            @Override public Map<String, Object> invoke(Long id, Map<String, Object> params) { return data; }
        };
        var controller = new ExternalDatasourceController();
        ReflectionTestUtils.setField(controller, "externalApiInvokeService", service);
        var response = controller.invokeApiConfig(1L, Map.of());
        assertEquals("601001", response.getCode());
        assertEquals("供应商不可用", response.getDetail());
        assertSame(data, response.getData());
    }
}
