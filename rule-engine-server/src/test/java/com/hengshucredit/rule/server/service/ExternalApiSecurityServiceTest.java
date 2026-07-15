package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ExternalApiSecurityServiceTest {

    @Test
    public void tianchuangProfileAddsSortedLowercaseMd5TokenKey() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appId", "APP001");
        body.put("name", "张三");
        body.put("idCard", "110101199001011234");
        body.put("mobile", "13800138000");
        String config = "{\"securityProfile\":\"TIANCHUANG_MD5_SORTED\","
                + "\"securityConfig\":{\"tokenId\":\"TOKEN001\"}}";

        Map<?, ?> result = (Map<?, ?>) new ExternalApiSecurityService().prepareRequest(
                "https://api.tcredit.com/integration/jk13113", body, config);

        assertEquals("c22964f261f8e89c65f1dd75a1970279", result.get("tokenKey"));
        assertEquals("APP001", result.get("appId"));
    }

    @Test
    public void tianchuangProfileRejectsMissingAppIdWithoutLeakingToken() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "张三");
        String config = "{\"securityProfile\":\"TIANCHUANG_MD5_SORTED\","
                + "\"securityConfig\":{\"tokenId\":\"SENSITIVE_TOKEN\"}}";

        try {
            new ExternalApiSecurityService().prepareRequest(
                    "https://api.tcredit.com/integration/jk13113", body, config);
            fail("缺少appId时应拒绝生成tokenKey");
        } catch (IllegalArgumentException e) {
            assertEquals("天创appId不能为空", e.getMessage());
            assertFalse(e.getMessage().contains("SENSITIVE_TOKEN"));
        }
    }

    @Test
    public void baihangProfileEncryptsBusinessBodyAndSignsHead() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestRefId", "REQ202607150001");
        body.put("name", "张三");
        body.put("certNo", "110101199001011234");
        body.put("applyDate", "20260715120000");
        body.put("queryReason", "1");

        Map<?, ?> result = (Map<?, ?>) new ExternalApiSecurityService().prepareRequest(
                "https://127.0.0.1/openapi/queryData/FRAI001C", body, baihangConfig());

        Map<?, ?> head = (Map<?, ?>) result.get("head");
        assertEquals("REQ202607150001", head.get("requestRefId"));
        assertEquals("secret-id", head.get("secretId"));
        assertEquals("W8VWjl06VJpmaWFeTYNOsAQPXt0=", head.get("signature"));
        assertEquals("D/hfdf4tJ4rjnaWVQpKPeZNJuo51V85ItM8wJEn7cnktYKAVPte5v3jDAv23g1oyJGIhGX93w70Mh5IvWgz9EGoVPgOfrvHMfFiutTXvJJeZr7SamOzvtm99uFqWvkWC",
                result.get("request"));
    }

    @Test
    public void baihangProfileDecryptsResponseBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("response", "7tIn6QIzHDaXVCTpzEIwl7FmdP9vNGs8CoBZXbs1U7c=");

        Map<?, ?> result = (Map<?, ?>) new ExternalApiSecurityService().processResponse(body, baihangConfig());

        assertEquals("0", ((Map<?, ?>) result.get("response")).get("p2pEscapeDebtStatus"));
    }

    @Test
    public void baihangProfileGeneratesReferenceWhenMissing() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "张三");
        body.put("certNo", "110101199001011234");
        body.put("queryReason", "1");

        Map<?, ?> result = (Map<?, ?>) new ExternalApiSecurityService().prepareRequest(
                "https://127.0.0.1/openapi/queryData/FRAI001C", body, baihangConfig());

        Map<?, ?> head = (Map<?, ?>) result.get("head");
        assertTrue(String.valueOf(head.get("requestRefId")).matches("[0-9a-f]{32}"));
        assertTrue(String.valueOf(result.get("request")).length() > 20);
    }

    private String baihangConfig() {
        return "{\"securityProfile\":\"BAIHANG_HMAC_SHA1_3DES\",\"securityConfig\":{"
                + "\"secretId\":\"secret-id\","
                + "\"secretKey\":\"MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1u\"}}";
    }
}
