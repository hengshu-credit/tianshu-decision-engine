package com.hengshucredit.rule.runtime;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.client.RuleEngineClient;
import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import com.hengshucredit.rule.client.http.RuleHttpClient;
import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.ExternalCodeMapping;
import com.hengshucredit.rule.model.PlatformCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletResponse;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RuntimeResponseMappingTest {
    @Test public void runtimeAuthenticationErrorsUseSharedMappingWithoutChangingHttpStatus() throws Exception {
        var mapping = new ExternalCodeMapping(9, true, Map.of("300001", new ExternalCodeMapping.Entry("E_AUTH", "认证失败"), "300002", new ExternalCodeMapping.Entry("E_AUTH", "认证失败")));
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        upstream.createContext("/api/rule/sync/system-code-mapping", exchange -> {
            requests.incrementAndGet(); byte[] bytes = JSON.toJSONBytes(ApiResponse.ok(mapping));
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        upstream.start();
        try (RuleHttpClient client = RuleHttpClient.builder().serverUrl("http://127.0.0.1:" + upstream.getAddress().getPort())
                .appName("mapping-test").authConfig(ClientAuthConfig.legacyToken("test-only-token")).timeoutMs(1000).build()) {
            var beans = new StaticListableBeanFactory(); beans.addBean("httpClient", client);
            var advice = new RuntimeResponseMapping(beans.getBeanProvider(RuleEngineClient.class), beans.getBeanProvider(RuleHttpClient.class));
            for (PlatformCode code : new PlatformCode[]{PlatformCode.TOKEN_INVALID, PlatformCode.CREDENTIAL_INVALID}) {
                var body = ApiResponse.fail(code, null); var servlet = new MockHttpServletResponse(); servlet.setStatus(401);
                var response = new ServletServerHttpResponse(servlet);
                advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, null, response);
                assertEquals("E_AUTH", body.getCode()); assertFalse(body.isSuccess()); assertEquals(401, servlet.getStatus());
                assertEquals(code.code(), response.getHeaders().getFirst(ExternalCodeMapping.STANDARD_CODE_HEADER));
                assertEquals("9", response.getHeaders().getFirst(ExternalCodeMapping.VERSION_HEADER));
            }
            assertEquals(1, requests.get());
        } finally { upstream.stop(0); }
    }
}
