package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ExternalApiConditionOperandTest {
    private final ExternalApiInvokeService service = new ExternalApiInvokeService();

    @Test public void missingOperandPathIsDistinctFromExplicitNullAndEmptyValue() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nullValue", null);
        body.put("empty", "");
        body.put("zero", 0);
        body.put("flag", false);
        Map<String, Object> response = Map.of("response", Map.of("body", body));
        assertTrue(matches("response.body.absent", "missing", response));
        assertFalse(matches("response.body.absent", "exists", response));
        assertFalse(matches("response.body.absent", "is_null", response));
        assertTrue(matches("response.body.nullValue", "exists", response));
        assertTrue(matches("response.body.nullValue", "is_null", response));
        assertFalse(matches("response.body.nullValue", "missing", response));
        assertTrue(matches("response.body.empty", "is_empty", response));
        assertFalse(matches("response.body.zero", "is_empty", response));
        assertFalse(matches("response.body.flag", "is_empty", response));
    }

    @Test public void bracketAndArrayPathsPreserveNullPresence() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("score.value", null);
        Map<String, Object> response = Map.of("response", Map.of("body", Map.of("items", List.of(item))));
        assertTrue(matches("response.body.items[0]['score.value']", "exists", response));
        assertFalse(matches("response.body.items[0]['score.value']", "missing", response));
        assertTrue(matches("response.body.items[1]['score.value']", "missing", response));
    }

    @Test public void noValueOperatorIgnoresStaleRightOperand() {
        String tree = """
            {"left":{"kind":"PATH","value":"response.body.code"},"operator":"missing",
             "right":{"kind":"REFERENCE","refType":"VARIABLE","refId":999}}
            """;
        assertTrue(service.matchesResponseCondition(tree, Map.of("response", Map.of("body", Map.of()))));
    }

    @Test public void missingRightPathIsNotEqualToExplicitNull() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("score", null);
        String tree = """
            {"left":{"kind":"PATH","value":"response.body.score"},"operator":"==",
             "right":{"kind":"PATH","value":"response.body.absent"}}
            """;
        assertFalse(service.matchesResponseCondition(tree, Map.of("response", Map.of("body", body))));
    }

    @Test public void typeConditionFromOperandEditorCanBeSavedAndExecuted() {
        String tree = """
            {"type":"group","operator":"AND","children":[
             {"left":{"kind":"PATH","value":"response.body.score"},"operator":"type_changed",
              "right":{"kind":"LITERAL","valueType":"STRING","value":"NUMBER"}}]}
            """;
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setExceptionCondition(tree);
        ExternalApiConfigValidator.validate(config);
        assertFalse(service.matchesResponseCondition(tree, Map.of("response", Map.of("body", Map.of("score", 88)))));
        assertTrue(service.matchesResponseCondition(tree, Map.of("response", Map.of("body", Map.of("score", "88")))));
    }

    @Test public void requestPlanPresenceKeepsExplicitNullAndBracketPaths() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("score.value", null);
        Map<String, Object> response = Map.of("response", Map.of("body", Map.of("items", List.of(item))));
        assertTrue(ExternalApiRequestPlan.present(response, "response.body.items[0]['score.value']"));
        assertFalse(ExternalApiRequestPlan.present(response, "response.body.items[1]['score.value']"));
    }

    private boolean matches(String path, String operator, Map<String, Object> response) {
        return service.matchesResponseCondition(JSON.toJSONString(Map.of(
                "left", Map.of("kind", "PATH", "value", path), "operator", operator)), response);
    }
}
