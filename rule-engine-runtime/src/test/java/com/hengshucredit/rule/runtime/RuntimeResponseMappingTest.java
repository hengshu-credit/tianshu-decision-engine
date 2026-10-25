package com.hengshucredit.rule.runtime;

import com.hengshucredit.rule.client.RuleEngineClient;
import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.ExternalCodeMapping;
import com.hengshucredit.rule.model.PlatformCode;
import org.junit.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.Assert.*;

public class RuntimeResponseMappingTest {
    @Test public void runtimeAuthenticationErrorsUseSdkMappingWithoutChangingHttpStatus() {
        var mapping = new ExternalCodeMapping(9, true, Map.of("300001", new ExternalCodeMapping.Entry("E_AUTH", "认证失败"),
                "300002", new ExternalCodeMapping.Entry("E_AUTH", "认证失败")));
        RuleEngineClient client = RuleEngineClient.builder().serverUrl("http://127.0.0.1:8080")
                .token("test-only-token").serverSideExecution(true).build();
        try {
            ReflectionTestUtils.setField(client, "codeMapping", mapping);
            var beans = new StaticListableBeanFactory(); beans.addBean("sdkClient", client);
            var advice = new RuntimeResponseMapping(beans.getBeanProvider(RuleEngineClient.class));
            for (PlatformCode code : new PlatformCode[]{PlatformCode.TOKEN_INVALID, PlatformCode.CREDENTIAL_INVALID}) {
                var body = ApiResponse.fail(code, null); var servlet = new MockHttpServletResponse(); servlet.setStatus(401);
                var response = new ServletServerHttpResponse(servlet);
                advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, null, response);
                assertEquals("E_AUTH", body.getCode()); assertFalse(body.isSuccess()); assertEquals(401, servlet.getStatus());
                assertEquals(code.code(), response.getHeaders().getFirst(ExternalCodeMapping.STANDARD_CODE_HEADER));
                assertEquals("9", response.getHeaders().getFirst(ExternalCodeMapping.VERSION_HEADER));
            }
        } finally { client.close(); }
    }
}
