package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import org.junit.Test;
import org.springframework.http.MediaType;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class ExternalApiAuthConfigTest {
    @Test public void nestedObjectMayHaveAFieldNamedKind() {
        String text = JSON.toJSONString(Map.of("body", Map.of("payload", Map.of("kind", ExternalApiTestConfig.literal("consumer")))));
        ExternalApiAuthConfig.validate(text, Map.of());
        assertEquals(Map.of("payload", Map.of("kind", "consumer")), new ExternalApiInvokeService().buildTokenRequestBody(
                JSON.parseObject(text).getJSONObject("body"), Map.of(), MediaType.APPLICATION_JSON, Map.of()));
    }
    @Test public void nestedAuthenticationBodyResolvesIdsWithoutReinterpretingLiteralText() {
        Map<String, Object> credential = Map.of("kind", "REFERENCE", "refType", "VARIABLE", "refId", "9007199254740993", "code", "oldName");
        Object body = new ExternalApiInvokeService().buildTokenRequestBody(Map.of(
                "client", Map.of("X.Client Id", credential),
                "literal", Map.of("kind", "LITERAL", "valueType", "STRING", "value", "$.literalText")),
                Map.of("renamed", "credential", "literalText", "wrong"), MediaType.APPLICATION_JSON,
                Map.of("VARIABLE:9007199254740993", "renamed"));
        assertEquals(Map.of("client", Map.of("X.Client Id", "credential"), "literal", "$.literalText"), body);
    }

    @Test public void authenticationRejectsNameBindingsAndUnavailableIds() {
        assertThrows(IllegalArgumentException.class, () -> ExternalApiAuthConfig.validate("{\"token\":\"$.token\"}", Map.of()));
        String config = JSON.toJSONString(Map.of("token", Map.of("kind", "REFERENCE", "refType", "VARIABLE", "refId", 7)));
        assertThrows(IllegalArgumentException.class, () -> ExternalApiAuthConfig.validate(config, Map.of("VARIABLE:8", "token")));
        ExternalApiAuthConfig.validate(config, Map.of("VARIABLE:7", "renamed"));
    }

    @Test public void scriptVariablesUseTheSameStableOperands() {
        String text = JSON.toJSONString(Map.of("scriptVariables", List.of(Map.of("name", "appId", "value",
                Map.of("kind", "REFERENCE", "refType", "VARIABLE", "refId", 7, "code", "oldName")))));
        assertEquals(Map.of("appId", "value"), ExternalApiAuthConfig.resolveVariables(text, Map.of("newName", "value"), Map.of("VARIABLE:7", "newName")));
    }
}
