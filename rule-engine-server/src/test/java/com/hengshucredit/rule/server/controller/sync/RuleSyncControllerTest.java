package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleSyncControllerTest {

    @Test
    public void enrichesPublishedRuleWithOrderedRootOutputScriptNames() {
        RuleSyncController controller = new RuleSyncController();
        ReflectionTestUtils.setField(controller, "definitionService", new RuleDefinitionService() {
            @Override
            public List<RuleDefinitionOutputField> listOutputFields(Long definitionId) {
                RuleDefinitionOutputField decision = new RuleDefinitionOutputField();
                decision.setScriptName("decision");
                RuleDefinitionOutputField empty = new RuleDefinitionOutputField();
                empty.setScriptName(" ");
                RuleDefinitionOutputField notAssigned = new RuleDefinitionOutputField();
                notAssigned.setScriptName("notAssigned");
                return Arrays.asList(decision, empty, notAssigned);
            }
        });
        RulePublished published = new RulePublished();
        published.setDefinitionId(10L);
        published.setRevisionId(22L);
        published.setArtifactDigest("artifact-digest");

        RulePublished enriched = ReflectionTestUtils.invokeMethod(
                controller, "withOutputScriptNames", published);

        assertEquals(Arrays.asList("decision", "notAssigned"), enriched.getOutputScriptNames());
        assertEquals(Long.valueOf(22L), enriched.getRevisionId());
        assertEquals("artifact-digest", enriched.getArtifactDigest());
    }

    @Test
    public void allowsFunctionSyncOnlyForTokenProject() {
        assertTrue(RuleSyncController.isAuthorizedProject(100L, 100L));
    }

    @Test
    public void rejectsFunctionSyncForOtherProject() {
        assertFalse(RuleSyncController.isAuthorizedProject(101L, 100L));
    }

    @Test
    public void rejectsFunctionSyncWhenProjectIdMissing() {
        assertFalse(RuleSyncController.isAuthorizedProject(null, 100L));
        assertFalse(RuleSyncController.isAuthorizedProject(100L, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void multipartBodyRestoresNestedParamsAndFiles() {
        Map<String, String[]> fields = new LinkedHashMap<>();
        fields.put("clientAppName", new String[]{"api-doc-browser"});
        fields.put("params.customer.age", new String[]{"18"});
        fields.put("params.tags", new String[]{"a", "b"});
        MockMultipartFile file = new MockMultipartFile(
                "params.contract", "contract.txt", "text/plain",
                "demo".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> body = RuleSyncController.buildMultipartBody(
                fields, Collections.singletonMap("params.contract", file));

        assertEquals("api-doc-browser", body.get("clientAppName"));
        Map<String, Object> params = (Map<String, Object>) body.get("params");
        Map<String, Object> customer = (Map<String, Object>) params.get("customer");
        assertEquals("18", customer.get("age"));
        assertEquals(Arrays.asList("a", "b"), params.get("tags"));
        assertEquals(file, params.get("contract"));
    }
}
