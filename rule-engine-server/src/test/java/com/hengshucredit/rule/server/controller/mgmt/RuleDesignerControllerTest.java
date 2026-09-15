package com.hengshucredit.rule.server.controller.mgmt;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.dto.RuleDesignerCompileRequest;
import com.hengshucredit.rule.model.dto.RuleDesignerCompileResponse;
import com.hengshucredit.rule.server.common.GlobalExceptionHandler;
import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.security.ConsolePermissionInterceptor;
import com.hengshucredit.rule.server.security.ConsolePermissionService;
import com.hengshucredit.rule.server.service.RuleDesignerService;
import com.hengshucredit.rule.server.service.RuleLifecycleService;
import org.junit.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class RuleDesignerControllerTest {
    @Test
    public void compileReturnsSyntaxFailureInEnvelopeAndDeleteRequiresLock() throws Exception {
        RuleDesignerController controller = new RuleDesignerController();
        ReflectionTestUtils.setField(controller, "designerService", new RuleDesignerService() {
            @Override public RuleDesignerCompileResponse compile(Long id, RuleDesignerCompileRequest request) {
                assertEquals(Long.valueOf(30L), id);
                assertEquals("bad", request.getModelJson());
                RuleDesignerCompileResponse result = new RuleDesignerCompileResponse();
                result.setCompileSuccess(false);
                result.setCompileMessage("syntax");
                return result;
            }
        });
        long[] deleted = new long[2];
        ReflectionTestUtils.setField(controller, "lifecycleService", new RuleLifecycleService() {
            @Override public void deleteDraft(Long id, Long revisionId, Integer lock) {
                if (lock == null) {
                    super.deleteDraft(id, revisionId, null);
                    return;
                }
                assertEquals(Long.valueOf(30L), id);
                deleted[0] = revisionId;
                deleted[1] = lock;
            }
        });
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String body = mvc.perform(post("/api/rule/definition/30/designer/compile")
                        .contentType("application/json").content("{\"modelJson\":\"bad\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(JSON.parseObject(body).getJSONObject("data").getBooleanValue("compileSuccess"));
        mvc.perform(delete("/api/rule/definition/30/revisions/8")).andExpect(status().isBadRequest());
        assertEquals(0L, deleted[0]);
        mvc.perform(delete("/api/rule/definition/30/revisions/8").param("lockVersion", "3"))
                .andExpect(status().isOk());
        assertArrayEquals(new long[]{8, 3}, deleted);
    }

    @Test
    public void allDesignerActionsRequireRuleEditPermission() throws Exception {
        RuleEngineConsoleLoginProperties properties = new RuleEngineConsoleLoginProperties();
        ConsolePermissionService permissions = new ConsolePermissionService() {
            @Override public boolean hasPermission(Long userId, String permission) { return false; }
        };
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new RuleDesignerController())
                .addInterceptors(new ConsolePermissionInterceptor(properties, permissions)).build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(properties.getSessionUserIdAttribute(), 9L);
        for (String action : new String[]{"compile", "drafts"}) {
            mvc.perform(post("/api/rule/definition/30/designer/" + action).session(session)
                    .contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(delete("/api/rule/definition/30/revisions/8").session(session)
                .param("lockVersion", "0")).andExpect(status().isForbidden());
    }
}
