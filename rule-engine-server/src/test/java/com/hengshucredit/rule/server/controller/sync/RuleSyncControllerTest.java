package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

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

        RulePublished enriched = ReflectionTestUtils.invokeMethod(
                controller, "withOutputScriptNames", published);

        assertEquals(Arrays.asList("decision", "notAssigned"), enriched.getOutputScriptNames());
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
}
