package com.hengshucredit.rule.server.consolelogin;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.server.mapper.ConsoleUserPreferenceMapper;
import com.hengshucredit.rule.server.service.ConsoleUserPreferenceService;
import org.junit.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ConsoleThemePreferenceControllerTest {

    @Test
    public void loggedInUserCanSaveAndReadTheirOwnTheme() throws Exception {
        Fixture fixture = fixture();
        MockHttpSession session = fixture.loggedInSession(9L, "reviewer");

        JSONObject saved = response(fixture.mockMvc.perform(
                        put("/api/auth/console/preferences/theme")
                                .session(session)
                                .contentType("application/json")
                                .content(validThemeJson("DARK", "NEBULA_GRADIENT")))
                .andExpect(status().isOk())
                .andReturn());
        JSONObject loaded = response(fixture.mockMvc.perform(
                        get("/api/auth/console/preferences/theme").session(session))
                .andExpect(status().isOk())
                .andReturn());

        assertEquals("000000", saved.getString("code"));
        assertEquals("NEBULA_GRADIENT",
                loaded.getJSONObject("data").getString("accentPreset"));
        assertEquals("DARK",
                loaded.getJSONObject("data").getString("colorScheme"));
        assertEquals("TOP",
                loaded.getJSONObject("data").getString("navigationLayout"));
    }

    @Test
    public void missingSessionIdentityReturnsBusinessUnauthorized() throws Exception {
        Fixture fixture = fixture();

        JSONObject body = response(fixture.mockMvc.perform(
                        get("/api/auth/console/preferences/theme"))
                .andExpect(status().isOk())
                .andReturn());

        assertEquals("300001", body.getString("code"));
        assertEquals("未登录", body.getString("detail"));
    }

    @Test
    public void requestCannotSelectAnotherUserOrSubmitUnknownFields() throws Exception {
        Fixture fixture = fixture();
        MockHttpSession session = fixture.loggedInSession(9L, "reviewer");
        JSONObject request = JSON.parseObject(validThemeJson("LIGHT", "THEME_BLUE"));
        request.put("userId", 10L);

        JSONObject body = response(fixture.mockMvc.perform(
                        put("/api/auth/console/preferences/theme")
                                .session(session)
                                .contentType("application/json")
                                .content(request.toJSONString()))
                .andExpect(status().isOk())
                .andReturn());

        assertEquals("100001", body.getString("code"));
        assertEquals(0, fixture.rows.size());
    }

    private static Fixture fixture() {
        Map<String, String> rows = new HashMap<>();
        ConsoleUserPreferenceMapper mapper =
                (ConsoleUserPreferenceMapper) Proxy.newProxyInstance(
                        ConsoleUserPreferenceMapper.class.getClassLoader(),
                        new Class<?>[]{ConsoleUserPreferenceMapper.class},
                        (proxy, method, args) -> {
                            if ("findValue".equals(method.getName())) {
                                return rows.get(key(args[0], args[1]));
                            }
                            if ("upsertValue".equals(method.getName())) {
                                rows.put(key(args[0], args[1]), (String) args[2]);
                                return 1;
                            }
                            if ("deleteValue".equals(method.getName())) {
                                return rows.remove(key(args[0], args[1])) == null
                                        ? 0 : 1;
                            }
                            if ("toString".equals(method.getName())) {
                                return "InMemoryConsoleUserPreferenceMapper";
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
        ConsoleThemePreferenceService themeService =
                new ConsoleThemePreferenceService(
                        new ConsoleUserPreferenceService(mapper));
        RuleEngineConsoleLoginProperties properties =
                new RuleEngineConsoleLoginProperties();
        properties.setEnabled(true);
        ConsoleThemePreferenceController controller =
                new ConsoleThemePreferenceController(properties, themeService);
        return new Fixture(rows, properties,
                MockMvcBuilders.standaloneSetup(controller).build());
    }

    private static String validThemeJson(String colorScheme,
                                         String accentPreset) {
        return "{\"schemaVersion\":2,\"colorScheme\":\"" + colorScheme
                + "\",\"accentMode\":\"PRESET\","
                + "\"accentPreset\":\"" + accentPreset
                + "\",\"customSolidColor\":\"#2639E9\","
                + "\"customGradientColors\":[\"#2639E9\",\"#873FF2\"],"
                + "\"customGradientType\":\"LINEAR\","
                + "\"customGradientAngle\":135,\"navigationLayout\":\"TOP\""
                + ",\"sidebarTheme\":\"DARK\","
                + "\"contentWidth\":\"FLUID\",\"fixedSidebar\":true,"
                + "\"colorWeak\":false}";
    }

    private static JSONObject response(MvcResult result) throws Exception {
        return JSON.parseObject(result.getResponse().getContentAsString());
    }

    private static String key(Object userId, Object preferenceKey) {
        return userId + ":" + preferenceKey;
    }

    private record Fixture(Map<String, String> rows,
                           RuleEngineConsoleLoginProperties properties,
                           MockMvc mockMvc) {
        private MockHttpSession loggedInSession(Long userId, String username) {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute(properties.getSessionUserIdAttribute(), userId);
            session.setAttribute(properties.getSessionUsernameAttribute(), username);
            return session;
        }
    }
}
