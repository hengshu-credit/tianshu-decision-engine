package com.bjjw.rule.server.config;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TokenAuthInterceptorTest {

    @Test
    public void protectsCurrentRuleSyncEndpoints() {
        assertTrue(TokenAuthInterceptor.isProtectedPath("/api/rule/sync"));
        assertTrue(TokenAuthInterceptor.isProtectedPath("/api/rule/sync/all"));
        assertTrue(TokenAuthInterceptor.isProtectedPath("/api/rule/sync/functions/1"));
    }

    @Test
    public void keepsLegacySyncEndpointProtected() {
        assertTrue(TokenAuthInterceptor.isProtectedPath("/api/sync"));
        assertTrue(TokenAuthInterceptor.isProtectedPath("/api/sync/all"));
    }

    @Test
    public void doesNotProtectSimilarNonSyncPaths() {
        assertFalse(TokenAuthInterceptor.isProtectedPath("/api/rule/sync-all"));
        assertFalse(TokenAuthInterceptor.isProtectedPath("/api/rule/syncfoo"));
        assertFalse(TokenAuthInterceptor.isProtectedPath("/api/rule/definition"));
    }
}
