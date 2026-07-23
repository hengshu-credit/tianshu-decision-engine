package com.hengshucredit.rule.server.consolelogin;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ConsoleAuthControllerTest {

    @Test
    public void legacyStatusReportsUnauthenticatedWhenLoginIsEnabled() throws Exception {
        MockMvc mockMvc = mockMvc(true);

        JSONObject response = response(mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andReturn());

        assertEquals(200, response.getIntValue("code"));
        assertTrue(response.getJSONObject("data").getBooleanValue("loginEnabled"));
        assertFalse(response.getJSONObject("data").getBooleanValue("authenticated"));
    }

    @Test
    public void legacyStatusReportsAuthenticatedSessionUsername() throws Exception {
        RuleEngineConsoleLoginProperties properties = properties(true);
        MockMvc mockMvc = mockMvc(properties);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(properties.getSessionUsernameAttribute(), "admin");

        JSONObject response = response(mockMvc.perform(get("/api/auth/status").session(session))
                .andExpect(status().isOk())
                .andReturn());

        assertTrue(response.getJSONObject("data").getBooleanValue("authenticated"));
        assertEquals("admin", response.getJSONObject("data").getString("username"));
    }

    @Test
    public void legacyStatusTreatsDisabledLoginAsAuthenticated() throws Exception {
        MockMvc mockMvc = mockMvc(false);

        JSONObject response = response(mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andReturn());

        assertFalse(response.getJSONObject("data").getBooleanValue("loginEnabled"));
        assertTrue(response.getJSONObject("data").getBooleanValue("authenticated"));
    }

    private static MockMvc mockMvc(boolean loginEnabled) {
        return mockMvc(properties(loginEnabled));
    }

    private static MockMvc mockMvc(RuleEngineConsoleLoginProperties properties) {
        ConsoleAuthController controller = new ConsoleAuthController();
        ReflectionTestUtils.setField(controller, "consoleLoginProperties", properties);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static RuleEngineConsoleLoginProperties properties(boolean loginEnabled) {
        RuleEngineConsoleLoginProperties properties = new RuleEngineConsoleLoginProperties();
        properties.setEnabled(loginEnabled);
        return properties;
    }

    private static JSONObject response(MvcResult result) throws Exception {
        return JSON.parseObject(result.getResponse().getContentAsString());
    }
}
