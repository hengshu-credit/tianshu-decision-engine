package com.hengshucredit.rule.server.functions;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RequestContext;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.server.service.ExternalApiInvokeService;
import com.hengshucredit.rule.server.service.FunctionRegistrar;
import org.junit.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;

public class ExternalApiFunctionsTest {
    private static class RecordingService extends ExternalApiInvokeService {
        int calls;
        Long id;
        Map<String, Object> params;

        @Override
        public Map<String, Object> invoke(Long apiId, Map<String, Object> request) {
            calls++;
            id = apiId;
            params = request;
            return Map.of("body", Map.of("found", true));
        }
    }

    @Test
    public void configuredBeanCallsExistingServiceOnlyWhenFlowBranchIsReached() {
        RecordingService service = new RecordingService();
        try (StaticApplicationContext beans = new StaticApplicationContext()) {
            beans.getBeanFactory().registerSingleton("externalApiFunctions", new ExternalApiFunctions(service));
            FunctionRegistrar registrar = new FunctionRegistrar();
            ReflectionTestUtils.setField(registrar, "applicationContext", beans);
            RuleFunction function = new RuleFunction();
            function.setFuncCode("queryConfiguredApi");
            function.setImplType("BEAN");
            function.setImplBeanName("externalApiFunctions");
            function.setImplMethod("invoke");
            function.setParamsJson("[{\"name\":\"apiId\",\"type\":\"OBJECT\"},"
                    + "{\"name\":\"params\",\"type\":\"OBJECT\"}]");
            QLExpressEngine engine = new QLExpressEngine();
            RequestContext request = new RequestContext();
            try (var ignored = request.bindFunctions(registrar.prepareFunctions(
                    Collections.singletonList(function), engine.getRunner()))) {
                RuleResult skipped = engine.execute(engine.prepare(
                        "if (false) { queryConfiguredApi(9, {\"name\": \"合成客户\"}); }\nreturn true;"),
                        Collections.emptyMap(), false, request);
                assertTrue(skipped.getErrorMessage(), skipped.isSuccess());
                assertEquals(0, service.calls);
                RuleResult result = engine.execute(engine.prepare(
                        "return queryConfiguredApi(9, {\"name\": \"合成客户\"});"),
                        Collections.emptyMap(), false, request);
                assertTrue(result.getErrorMessage(), result.isSuccess());
                assertEquals(Map.of("body", Map.of("found", true)), result.getResult());
                assertEquals(1, service.calls);
                assertEquals(Long.valueOf(9), service.id);
                assertEquals(Map.of("name", "合成客户"), service.params);
            }
        }
    }

    @Test
    public void rejectsInvalidIdsAndParameterShapesBeforeInvokingService() {
        RecordingService service = new RecordingService();
        ExternalApiFunctions functions = new ExternalApiFunctions(service);
        for (Object id : new Object[] { null, -1, 0, 1.5, "bad", new BigDecimal("9223372036854775808") }) {
            assertThrows(IllegalArgumentException.class, () -> functions.invoke(id, Map.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> functions.invoke(9, "{}"));
        assertThrows(IllegalArgumentException.class, () -> functions.invoke(9, Map.of(1, "bad key")));
        assertEquals(0, service.calls);
    }

    @Test
    public void propagatesProviderFailureWithoutInventingFallbackData() {
        IllegalStateException failure = new IllegalStateException("provider unavailable");
        ExternalApiFunctions functions = new ExternalApiFunctions(new ExternalApiInvokeService() {
            @Override
            public Map<String, Object> invoke(Long id, Map<String, Object> params) { throw failure; }
        });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> functions.invoke(9, Map.of())));
    }
}
