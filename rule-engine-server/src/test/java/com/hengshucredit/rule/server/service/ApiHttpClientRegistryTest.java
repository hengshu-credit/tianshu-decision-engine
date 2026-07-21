package com.hengshucredit.rule.server.service;

import org.junit.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class ApiHttpClientRegistryTest {

    @Test
    public void reusesClientForSameConfigurationAndReplacesItWhenTimeoutChanges() {
        ExternalCallProperties properties = new ExternalCallProperties();
        properties.setHttpClientRegistryMaxEntries(8);
        ApiHttpClientRegistry registry = new ApiHttpClientRegistry(properties);

        RestTemplate first;
        try (ApiHttpClientRegistry.ClientLease lease = registry.acquire("api:1", 1000, 2000)) {
            first = lease.getRestTemplate();
        }
        try (ApiHttpClientRegistry.ClientLease lease = registry.acquire("api:1", 1000, 2000)) {
            assertSame(first, lease.getRestTemplate());
        }
        try (ApiHttpClientRegistry.ClientLease lease = registry.acquire("api:1", 1000, 3000)) {
            assertNotSame(first, lease.getRestTemplate());
        }

        registry.close();
    }

    @Test
    public void evictsLeastRecentlyUsedIdleClientWhenRegistryIsFull() {
        ExternalCallProperties properties = new ExternalCallProperties();
        properties.setHttpClientRegistryMaxEntries(2);
        ApiHttpClientRegistry registry = new ApiHttpClientRegistry(properties);

        try (ApiHttpClientRegistry.ClientLease ignored = registry.acquire("api:1", 1000, 1000)) {
            // release as an idle eviction candidate
        }
        try (ApiHttpClientRegistry.ClientLease ignored = registry.acquire("api:2", 1000, 1000)) {
            // release as an idle eviction candidate
        }
        try (ApiHttpClientRegistry.ClientLease ignored = registry.acquire("api:3", 1000, 1000)) {
            assertEquals(2, registry.size());
        }

        registry.close();
    }

    @Test
    public void rejectsNewPoolWhenRegistryIsFullOfInUseClients() {
        ExternalCallProperties properties = new ExternalCallProperties();
        properties.setHttpClientRegistryMaxEntries(1);
        ApiHttpClientRegistry registry = new ApiHttpClientRegistry(properties);

        try (ApiHttpClientRegistry.ClientLease active = registry.acquire("api:1", 1000, 1000)) {
            RestTemplate activeClient = active.getRestTemplate();
            try {
                registry.acquire("api:2", 1000, 1000);
                org.junit.Assert.fail("Expected bounded registry rejection");
            } catch (ApiHttpClientRegistry.PoolBusyException expected) {
                assertEquals("API_CONNECTION_POOL_BUSY", expected.getErrorType());
            }
            assertEquals(1, registry.size());
            try (ApiHttpClientRegistry.ClientLease same = registry.acquire("api:1", 1000, 1000)) {
                assertSame(activeClient, same.getRestTemplate());
            }
        }

        registry.close();
    }
}
