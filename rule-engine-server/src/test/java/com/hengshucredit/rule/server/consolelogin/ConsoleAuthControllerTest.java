package com.hengshucredit.rule.server.consolelogin;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ConsoleAuthControllerTest {

    @Test
    public void legacyStatusReportsUnauthenticatedWhenLoginIsEnabled() throws Exception {
        MockMvc mockMvc = mockMvc(true);

        JSONObject response = response(mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andReturn());

        assertEquals("000000", response.getString("code"));
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

    @Test
    public void databaseLoginStoresUserIdentityAndPermissionVersion() throws Exception {
        RuleEngineConsoleLoginProperties properties = properties(true);
        DatabaseConsoleAccountService accountService = accountService();
        MockMvc mockMvc = mockMvc(properties, accountService);

        MvcResult result = mockMvc.perform(post("/api/auth/console/login")
                        .contentType("application/json")
                        .content("{\"username\":\"reviewer\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        assertEquals(9L, session.getAttribute(
                properties.getSessionUserIdAttribute()));
        assertEquals("reviewer", session.getAttribute(
                properties.getSessionUsernameAttribute()));
        assertEquals(4L, session.getAttribute(
                properties.getSessionPermissionVersionAttribute()));
        JSONObject body = response(result).getJSONObject("data");
        assertEquals("reviewer", body.getString("username"));
        assertTrue(body.getJSONArray("permissions").contains("approval:view"));
        String loginTime = java.time.Instant.ofEpochMilli(session.getCreationTime()).toString();
        assertEquals(loginTime, body.getString("loginTime"));
        for (int i = 0; i < 2; i++) {
            JSONObject me = response(mockMvc.perform(get("/api/auth/console/me").session(session)).andReturn()).getJSONObject("data");
            assertEquals(loginTime, me.getString("loginTime"));
        }
        mockMvc.perform(post("/api/auth/console/logout").session(session));
        assertTrue(session.isInvalid());
        assertEquals("301001", response(mockMvc.perform(get("/api/auth/console/me")).andReturn()).getString("code"));
    }

    @Test
    public void currentUserReturnsRolesAndEffectivePermissions() throws Exception {
        RuleEngineConsoleLoginProperties properties = properties(true);
        MockMvc mockMvc = mockMvc(properties, accountService());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(properties.getSessionUserIdAttribute(), 9L);
        session.setAttribute(properties.getSessionUsernameAttribute(), "reviewer");
        session.setAttribute(properties.getSessionPermissionVersionAttribute(), 4L);

        JSONObject data = response(mockMvc.perform(
                        get("/api/auth/console/me").session(session))
                .andExpect(status().isOk())
                .andReturn()).getJSONObject("data");

        assertEquals(9L, data.getLongValue("userId"));
        assertEquals("reviewer", data.getString("displayName"));
        assertEquals(java.time.Instant.ofEpochMilli(session.getCreationTime()).toString(), data.getString("loginTime"));
        assertTrue(data.getJSONArray("roleCodes").contains("REVIEWER"));
        assertTrue(data.getJSONArray("permissions").contains("approval:view"));
    }

    @Test
    public void currentUserRejectsStalePermissionVersionWithoutDestroyingConcurrentSessionStorage() throws Exception {
        RuleEngineConsoleLoginProperties properties = properties(true);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(properties.getSessionUsernameAttribute(), "reviewer");
        session.setAttribute(properties.getSessionUserIdAttribute(), 9L);
        session.setAttribute(properties.getSessionPermissionVersionAttribute(), 3L);
        JSONObject body = response(mockMvc(properties, accountService()).perform(get("/api/auth/console/me").session(session)).andReturn());
        assertEquals("301001", body.getString("code"));
        assertFalse(session.isInvalid());
        org.junit.Assert.assertNull(session.getAttribute(properties.getSessionUsernameAttribute()));
    }

    private static MockMvc mockMvc(boolean loginEnabled) {
        return mockMvc(properties(loginEnabled));
    }

    private static MockMvc mockMvc(RuleEngineConsoleLoginProperties properties) {
        return mockMvc(properties, null);
    }

    private static MockMvc mockMvc(RuleEngineConsoleLoginProperties properties,
                                   DatabaseConsoleAccountService accountService) {
        ConsoleAuthController controller = new ConsoleAuthController();
        ReflectionTestUtils.setField(controller, "consoleLoginProperties", properties);
        if (accountService != null) {
            ReflectionTestUtils.setField(controller, "databaseAccountService", accountService);
        }
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static DatabaseConsoleAccountService accountService() {
        return new DatabaseConsoleAccountService() {
            @Override
            public ConsoleLoginResult authenticate(
                    String username, String rawPassword) {
                if (!"reviewer".equals(username) || !"secret".equals(rawPassword)) {
                    return ConsoleLoginResult.failure();
                }
                return result();
            }

            @Override
            public ConsoleLoginResult currentUser(Long userId) {
                return Long.valueOf(9L).equals(userId)
                        ? result() : ConsoleLoginResult.failure();
            }

            private ConsoleLoginResult result() {
                return new ConsoleLoginResult(
                        true, false, 9L, "reviewer", "reviewer", 4L,
                        List.of("REVIEWER"), Set.of("approval:view"));
            }
        };
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
