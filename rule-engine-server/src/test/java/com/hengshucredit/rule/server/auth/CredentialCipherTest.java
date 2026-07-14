package com.hengshucredit.rule.server.auth;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class CredentialCipherTest {

    @Test
    public void encryptsAndDecryptsWithActiveKey() {
        ProjectAuthProperties properties = properties("v2", "v2", "current-master-key");
        CredentialCipher cipher = new CredentialCipher(properties);

        String encrypted = cipher.encrypt("secret-value");

        assertTrue(encrypted.startsWith("v1:v2:"));
        assertNotEquals("secret-value", encrypted);
        assertEquals("secret-value", cipher.decrypt(encrypted));
    }

    @Test
    public void decryptsExistingValueAfterActiveKeyChanges() {
        ProjectAuthProperties oldProperties = properties("v1", "v1", "old-master-key");
        String encrypted = new CredentialCipher(oldProperties).encrypt("legacy-secret");

        ProjectAuthProperties rotatedProperties = properties("v2", "v1", "old-master-key");
        rotatedProperties.getMasterKeys().put("v2", "new-master-key");

        assertEquals("legacy-secret", new CredentialCipher(rotatedProperties).decrypt(encrypted));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTamperedCiphertext() {
        CredentialCipher cipher = new CredentialCipher(properties("v1", "v1", "master-key"));
        String encrypted = cipher.encrypt("secret");
        cipher.decrypt(encrypted.substring(0, encrypted.length() - 2) + "AA");
    }

    @Test
    public void lookupKeyIncludesAuthenticationType() {
        CredentialCipher cipher = new CredentialCipher(properties("v1", "v1", "master-key"));

        assertNotEquals(cipher.lookupKey("BASIC", "same-value"),
                cipher.lookupKey("API_KEY", "same-value"));
        assertEquals(cipher.lookupKey("BASIC", "same-value"),
                cipher.lookupKey("BASIC", "same-value"));
    }

    private ProjectAuthProperties properties(String activeKeyId, String keyId, String key) {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setActiveKeyId(activeKeyId);
        properties.getMasterKeys().put(keyId, key);
        return properties;
    }
}
