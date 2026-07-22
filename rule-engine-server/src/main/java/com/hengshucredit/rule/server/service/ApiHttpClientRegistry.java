package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ApiHttpClientRegistry {

    private final ExternalCallProperties properties;
    private final LinkedHashMap<String, ClientEntry> clients = new LinkedHashMap<>(16, 0.75F, true);

    public ApiHttpClientRegistry() {
        this(new ExternalCallProperties());
    }

    @Autowired
    public ApiHttpClientRegistry(ExternalCallProperties properties) {
        this.properties = properties;
    }

    public ClientLease acquire(String key, int connectTimeoutMs, int readTimeoutMs) {
        return acquire(key, ClientSettings.defaults(properties, connectTimeoutMs, readTimeoutMs));
    }

    public synchronized ClientLease acquire(String key, ClientSettings settings) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("HTTP client key must not be blank");
        }
        ClientSettings normalized = settings.normalized();
        ClientEntry entry = clients.get(key);
        if (entry != null && entry.matches(normalized)) {
            entry.activeCount++;
            return new ClientLease(this, entry);
        }
        if (entry != null) {
            clients.remove(key);
            retire(entry);
        }
        if (!makeRoomForClient()) {
            throw new PoolBusyException();
        }
        ClientEntry created = createEntry(key, normalized);
        clients.put(key, created);
        created.activeCount++;
        return new ClientLease(this, created);
    }

    public synchronized void invalidate(String key) {
        ClientEntry entry = clients.remove(key);
        if (entry != null) {
            retire(entry);
        }
    }

    public synchronized PoolMetrics metrics(String key) {
        ClientEntry entry = clients.get(key);
        if (entry == null) {
            return new PoolMetrics(0, 0, 0, 0);
        }
        PoolStats stats = entry.connectionManager.getTotalStats();
        return new PoolMetrics(stats.getLeased(), stats.getAvailable(), stats.getPending(), stats.getMax());
    }

    synchronized int size() {
        return clients.size();
    }

    private boolean makeRoomForClient() {
        int maxEntries = Math.max(1, properties.getHttpClientRegistryMaxEntries());
        if (clients.size() < maxEntries) {
            return true;
        }
        Iterator<Map.Entry<String, ClientEntry>> iterator = clients.entrySet().iterator();
        while (iterator.hasNext()) {
            ClientEntry candidate = iterator.next().getValue();
            if (candidate.activeCount == 0) {
                iterator.remove();
                retire(candidate);
                return true;
            }
        }
        return false;
    }

    private ClientEntry createEntry(String key, ClientSettings settings) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(settings.connectTimeoutMs))
                .setSocketTimeout(Timeout.ofMilliseconds(settings.readTimeoutMs))
                .setTimeToLive(TimeValue.ofSeconds(settings.connectionTtlSeconds))
                .build();
        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(settings.maxConnections)
                        .setMaxConnPerRoute(settings.maxConnectionsPerRoute)
                        .setDefaultConnectionConfig(connectionConfig)
                        .build();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(settings.connectionRequestTimeoutMs))
                .setConnectTimeout(Timeout.ofMilliseconds(settings.connectTimeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(settings.readTimeoutMs))
                .build();
        CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofSeconds(settings.idleConnectionTimeoutSeconds))
                .build();
        RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(client));
        return new ClientEntry(key, settings, connectionManager, client, restTemplate);
    }

    private synchronized void release(ClientEntry entry) {
        if (entry.activeCount > 0) {
            entry.activeCount--;
        }
        if (entry.retired) {
            shutdown(entry);
        }
    }

    private void retire(ClientEntry entry) {
        entry.retired = true;
        if (entry.activeCount == 0) {
            shutdown(entry);
        }
    }

    private void shutdown(ClientEntry entry) {
        if (entry.closed) {
            return;
        }
        entry.closed = true;
        try {
            entry.client.close();
        } catch (IOException ignored) {
            // Closing an obsolete pool must not affect request processing.
        }
        entry.connectionManager.close();
    }

    @PreDestroy
    public synchronized void close() {
        for (ClientEntry entry : clients.values()) {
            entry.retired = true;
            shutdown(entry);
        }
        clients.clear();
    }

    public static final class ClientLease implements AutoCloseable {
        private final ApiHttpClientRegistry registry;
        private ClientEntry entry;

        private ClientLease(ApiHttpClientRegistry registry, ClientEntry entry) {
            this.registry = registry;
            this.entry = entry;
        }

        public RestTemplate getRestTemplate() {
            if (entry == null) {
                throw new IllegalStateException("HTTP client lease has been closed");
            }
            return entry.restTemplate;
        }

        @Override
        public void close() {
            ClientEntry current = entry;
            if (current != null) {
                entry = null;
                registry.release(current);
            }
        }
    }

    public static final class ClientSettings {
        private final int maxConnections;
        private final int maxConnectionsPerRoute;
        private final int connectionRequestTimeoutMs;
        private final int connectTimeoutMs;
        private final int readTimeoutMs;
        private final int idleConnectionTimeoutSeconds;
        private final int connectionTtlSeconds;

        public ClientSettings(int maxConnections, int maxConnectionsPerRoute,
                              int connectionRequestTimeoutMs, int connectTimeoutMs, int readTimeoutMs,
                              int idleConnectionTimeoutSeconds, int connectionTtlSeconds) {
            this.maxConnections = maxConnections;
            this.maxConnectionsPerRoute = maxConnectionsPerRoute;
            this.connectionRequestTimeoutMs = connectionRequestTimeoutMs;
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
            this.idleConnectionTimeoutSeconds = idleConnectionTimeoutSeconds;
            this.connectionTtlSeconds = connectionTtlSeconds;
        }

        public static ClientSettings from(RuleExternalApiConfig config, int remainingTimeoutMs) {
            int remaining = positive(remainingTimeoutMs, 3000);
            return new ClientSettings(
                    value(config.getMaxConnections(), 100),
                    value(config.getMaxConnectionsPerRoute(), 100),
                    Math.min(value(config.getConnectionRequestTimeoutMs(), 100), remaining),
                    Math.min(value(config.getConnectTimeoutMs(), 500), remaining),
                    Math.min(value(config.getReadTimeoutMs(), value(config.getTimeoutMs(), 3000)), remaining),
                    value(config.getIdleConnectionTimeoutSeconds(), 30),
                    value(config.getConnectionTtlSeconds(), 300));
        }

        private static ClientSettings defaults(ExternalCallProperties properties,
                                               int connectTimeoutMs, int readTimeoutMs) {
            int max = Math.max(1, properties.getHttpMaxRequests());
            return new ClientSettings(max, Math.max(1, Math.min(properties.getHttpMaxRequestsPerHost(), max)),
                    Math.max(1, connectTimeoutMs), Math.max(1, connectTimeoutMs), Math.max(1, readTimeoutMs),
                    (int) Math.max(1L, properties.getHttpKeepAliveSeconds()), 300);
        }

        private ClientSettings normalized() {
            int max = clamp(maxConnections, 1, 10000);
            return new ClientSettings(max, clamp(maxConnectionsPerRoute, 1, max),
                    clamp(connectionRequestTimeoutMs, 1, 600000), clamp(connectTimeoutMs, 1, 600000),
                    clamp(readTimeoutMs, 1, 600000), clamp(idleConnectionTimeoutSeconds, 1, 3600),
                    clamp(connectionTtlSeconds, 1, 86400));
        }

        private static int value(Integer value, int defaultValue) {
            return value == null ? defaultValue : value;
        }

        private static int positive(int value, int defaultValue) {
            return value <= 0 ? defaultValue : value;
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(value, max));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ClientSettings)) return false;
            ClientSettings that = (ClientSettings) other;
            return maxConnections == that.maxConnections
                    && maxConnectionsPerRoute == that.maxConnectionsPerRoute
                    && connectionRequestTimeoutMs == that.connectionRequestTimeoutMs
                    && connectTimeoutMs == that.connectTimeoutMs
                    && readTimeoutMs == that.readTimeoutMs
                    && idleConnectionTimeoutSeconds == that.idleConnectionTimeoutSeconds
                    && connectionTtlSeconds == that.connectionTtlSeconds;
        }

        @Override
        public int hashCode() {
            int result = maxConnections;
            result = 31 * result + maxConnectionsPerRoute;
            result = 31 * result + connectionRequestTimeoutMs;
            result = 31 * result + connectTimeoutMs;
            result = 31 * result + readTimeoutMs;
            result = 31 * result + idleConnectionTimeoutSeconds;
            return 31 * result + connectionTtlSeconds;
        }
    }

    public static final class PoolMetrics {
        private final int leased;
        private final int available;
        private final int pending;
        private final int max;

        private PoolMetrics(int leased, int available, int pending, int max) {
            this.leased = leased;
            this.available = available;
            this.pending = pending;
            this.max = max;
        }

        public int getLeased() { return leased; }
        public int getAvailable() { return available; }
        public int getPending() { return pending; }
        public int getMax() { return max; }
    }

    public static final class PoolBusyException extends IllegalStateException {
        private PoolBusyException() {
            super("HTTP client registry is full of active pools");
        }

        public String getErrorType() {
            return "API_CONNECTION_POOL_BUSY";
        }
    }

    private static final class ClientEntry {
        private final String key;
        private final ClientSettings settings;
        private final PoolingHttpClientConnectionManager connectionManager;
        private final CloseableHttpClient client;
        private final RestTemplate restTemplate;
        private int activeCount;
        private boolean retired;
        private boolean closed;

        private ClientEntry(String key, ClientSettings settings,
                            PoolingHttpClientConnectionManager connectionManager,
                            CloseableHttpClient client, RestTemplate restTemplate) {
            this.key = key;
            this.settings = settings;
            this.connectionManager = connectionManager;
            this.client = client;
            this.restTemplate = restTemplate;
        }

        private boolean matches(ClientSettings settings) {
            return !retired && !closed && this.settings.equals(settings);
        }
    }
}
