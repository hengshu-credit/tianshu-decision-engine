package com.hengshucredit.rule.server.auth;

import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ProjectAuthBodyFilterTest {

    @Test
    public void openApiBodyIsCachedForHmacAndRemainsReadable() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/rule/openapi/RULE");
        byte[] body = "{\"age\":18}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        request.setContent(body);
        MockFilterChain chain = new MockFilterChain();
        new ProjectAuthBodyFilter().doFilter(request, new MockHttpServletResponse(), chain);
        org.junit.Assert.assertArrayEquals(body, CachedBodyHttpServletRequest.cachedBody(request));
        org.junit.Assert.assertArrayEquals(body, chain.getRequest().getInputStream().readAllBytes());
    }

    @Test
    public void rejectsOversizedOpenApiRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/rule/openapi/RULE");
        request.setContent(new byte[5 * 1024 * 1024 + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new ProjectAuthBodyFilter().doFilter(request, response, chain);
        assertEquals(413, response.getStatus());
        org.junit.Assert.assertTrue(response.getContentAsString().contains("100006"));
        assertNull(chain.getRequest());
    }

    @Test
    public void rejectsOversizedProtectedRequestBeforeCachingBody() throws Exception {
        ProjectAuthBodyFilter filter = new ProjectAuthBodyFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/rule/auth/token");
        request.setContent(new byte[5 * 1024 * 1024 + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        assertNull(chain.getRequest());
    }
}
