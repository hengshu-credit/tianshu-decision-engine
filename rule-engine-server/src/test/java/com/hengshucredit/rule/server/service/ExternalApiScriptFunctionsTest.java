package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExternalApiScriptFunctionsTest {

    private final ExternalApiScriptFunctions functions = new ExternalApiScriptFunctions();

    @Test
    public void digestAndHmacFunctionsMatchStableVectors() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", functions.apiMd5("abc"));
        assertEquals("66c7f0f462eeedd9d1f2d46bdc10e4e2"
                        + "4167c4875cf2f7a2297da02b8f4ba8e0",
                functions.apiSm3("abc"));
        assertEquals("3nybhbi3iqa8ino29wqQcBydtNk=",
                functions.apiHmacSha1Base64("The quick brown fox jumps over the lazy dog", "key"));
        assertEquals("3nybhbi3iqa8ino29wqQcBydtNk=",
                functions.apiHmacSha1Base64Key("The quick brown fox jumps over the lazy dog", "a2V5"));
    }

    @Test
    public void sortedKeyValueSupportsCanonicalSigningWithoutVendorBranches() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", "张三");
        values.put("appId", "APP001");
        values.put("mobile", "13800138000");
        values.put("idCard", "110101199001011234");

        assertEquals("idCard=110101199001011234,mobile=13800138000,name=张三",
                functions.apiSortedKeyValue(values, ",", "appId,tokenKey"));
    }

    @Test
    public void secureRandomBase64CreatesRequestedKeyLength() {
        String first = functions.apiRandomBase64(24D);
        String second = functions.apiRandomBase64(24D);

        assertEquals(24, Base64.getDecoder().decode(first).length);
        assertFalse(first.equals(second));
    }

    @Test
    public void tripleDesRoundTripUsesBase64Key() {
        String key = "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1u";
        String encrypted = functions.apiTripleDesEncryptBase64("{\"name\":\"张三\"}", key);

        assertEquals("{\"name\":\"张三\"}", functions.apiTripleDesDecryptBase64(encrypted, key));
        assertFalse(encrypted.contains("张三"));
    }

    @Test
    public void invalidSecretDoesNotLeakItsValue() {
        String secret = "NOT_A_VALID_BASE64_SECRET";
        try {
            functions.apiTripleDesEncryptBase64("payload", secret);
        } catch (IllegalArgumentException e) {
            assertFalse(e.getMessage().contains(secret));
            assertTrue(e.getMessage().contains("3DES"));
            return;
        }
        throw new AssertionError("expected invalid 3DES key to fail");
    }
}
