package com.hengshucredit.rule.server.controller.mgmt;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;

public class RuleDefinitionControllerTest {

    @Test
    public void normalizeModelJsonUnquotesJsonStringRequestBody() {
        RuleDefinitionController controller = new RuleDefinitionController();

        String normalized = ReflectionTestUtils.invokeMethod(
                controller, "normalizeModelJson", "\"{\\\"nodes\\\":[],\\\"edges\\\":[]}\"");

        assertEquals("{\"nodes\":[],\"edges\":[]}", normalized);
    }
}
