package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ExternalApiMissingValueTest {
    private final ExternalApiInvokeService service = new ExternalApiInvokeService();

    @Test
    public void jsonTemplateKeepsPresenceNullAndLiteralStrings() {
        RuleExternalApiConfig config = config();
        config.setBodyTemplate("{\"absent\":\"${absent}\",\"nil\":\"${nil}\","
                + "\"empty\":\"${empty}\",\"blank\":\"${blank}\",\"text\":\"${text}\"}");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("nil", null);
        input.put("empty", "");
        input.put("blank", "  ");
        input.put("text", "null");
        Map<?, ?> body = ReflectionTestUtils.invokeMethod(service, "buildBody", config, input);
        assertFalse(body.containsKey("absent"));
        assertTrue(body.containsKey("nil"));
        assertNull(body.get("nil"));
        assertEquals("", body.get("empty"));
        assertEquals("  ", body.get("blank"));
        assertEquals("null", body.get("text"));
        config.setRequestMapping("{\"_nullPolicy\":\"SEND_MISSING_SEND_NULL\"}");
        body = ReflectionTestUtils.invokeMethod(service, "buildBody", config, input);
        assertTrue(body.containsKey("absent"));
        assertNull(body.get("absent"));
        config.setRequestMapping("{\"_nullPolicy\":\"OMIT_MISSING_OMIT_NULL\"}");
        body = ReflectionTestUtils.invokeMethod(service, "buildBody", config, input);
        assertFalse(body.containsKey("nil"));
    }

    @Test
    public void jsonTemplateSupportsUnquotedAndEmbeddedMissingPlaceholders() {
        RuleExternalApiConfig config = config();
        config.setBodyTemplate("{\"number\":${number},\"absent\":${absent},\"label\":\"ID-${absent}\"}");
        Map<?, ?> body = ReflectionTestUtils.invokeMethod(service, "buildBody", config, Map.of("number", 7));
        assertEquals(7, body.get("number"));
        assertFalse(body.containsKey("absent"));
        assertFalse(body.containsKey("label"));
    }

    @Test
    public void responseMappingRetainsNullAndOmitsMissingWithoutTryingAlternatePath() {
        RuleExternalApiConfig config = config();
        config.setResponseMapping("{\"nil\":{\"paths\":[\"body.nil\",\"body.backup\"],\"default\":9},"
                + "\"absent\":\"body.absent\",\"defaulted\":{\"path\":\"body.absent\",\"default\":3}}");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nil", null);
        body.put("backup", 5);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("body", body);
        service.applyResponseMapping(config, response);
        Map<?, ?> mapped = (Map<?, ?>) response.get("body");
        assertTrue(mapped.containsKey("nil"));
        assertNull(mapped.get("nil"));
        assertFalse(mapped.containsKey("absent"));
        assertEquals(3, mapped.get("defaulted"));
        assertEquals(Map.of("nil", true, "absent", false, "defaulted", true), response.get("mappedPresence"));
    }

    @Test
    public void nonJsonPositionsUseDefaultCompatibilityAndAllowConfiguredFailure() {
        Object policy = ReflectionTestUtils.invokeMethod(service, "requestNullPolicy", Map.of());
        HttpHeaders headers = new HttpHeaders();
        ReflectionTestUtils.invokeMethod(service,
                "applyJsonHeaders", headers, "{\"X-Required\":\"$.absent\"}", Map.of(), policy);
        assertFalse(headers.containsKey("X-Required"));
        Object failPolicy = ReflectionTestUtils.invokeMethod(service, "nonJsonNullPolicy",
                Map.of("_nonJsonNullPolicy", Map.of("header", "FAIL", "query", "FAIL", "auth", "FAIL")));
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(service,
                "applyJsonHeaders", new HttpHeaders(), "{\"X-Required\":\"$.absent\"}", Map.of(), policy, failPolicy));
        Map<String, Object> explicitNull = new LinkedHashMap<>();
        explicitNull.put("value", null);
        UriComponentsBuilder query = UriComponentsBuilder.fromUriString("https://example.test/api");
        Object defaultNonJsonPolicy = ReflectionTestUtils.invokeMethod(service, "nonJsonNullPolicy", Map.of());
        ReflectionTestUtils.invokeMethod(service, "applyQueryParams", query, "{\"q\":\"$.value\"}",
                explicitNull, policy, defaultNonJsonPolicy);
        assertTrue(query.build(false).getQueryParams().containsKey("q"));
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("nil", null);
        MultiValueMap<?, ?> encoded = (MultiValueMap<?, ?>) service.buildHttpRequestBody(
                form, MediaType.APPLICATION_FORM_URLENCODED);
        assertFalse(encoded.containsKey("nil"));
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(service,
                "resolveTemplate", "token=${absent}", Map.of()));
    }

    private RuleExternalApiConfig config() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setRequestMethod("POST");
        config.setContentType("application/json");
        return config;
    }
}
