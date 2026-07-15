package com.hengshucredit.rule.server.auth;

import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ProjectAuthBodyFilterTest {

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
