package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

@Component
public class ExternalApiCircuitBreakerRegistry {

    private final ExternalCallProperties properties;
    private final LongSupplier currentTimeMillis;
    private final LinkedHashMap<Long, CircuitState> states = new LinkedHashMap<>(16, 0.75F, true);

    @Autowired
    public ExternalApiCircuitBreakerRegistry(ExternalCallProperties properties) {
        this(properties, System::currentTimeMillis);
    }

    ExternalApiCircuitBreakerRegistry(ExternalCallProperties properties, LongSupplier currentTimeMillis) {
        this.properties = properties;
        this.currentTimeMillis = currentTimeMillis;
    }

    public CircuitPermit acquire(RuleExternalApiConfig config) {
        if (config == null || config.getId() == null
                || Integer.valueOf(0).equals(config.getCircuitBreakerEnabled())) {
            return CircuitPermit.disabled();
        }
        CircuitState state = state(config);
        return state.acquire(currentTimeMillis.getAsLong());
    }

    public synchronized void invalidate(Long apiConfigId) {
        if (apiConfigId != null) states.remove(apiConfigId);
    }

    private synchronized CircuitState state(RuleExternalApiConfig config) {
        CircuitSettings settings = CircuitSettings.from(config);
        CircuitState existing = states.get(config.getId());
        if (existing != null && existing.settings.equals(settings)) return existing;
        if (states.size() >= Math.max(1, properties.getApiGuardRegistryMaxEntries())) {
            Iterator<Map.Entry<Long, CircuitState>> iterator = states.entrySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        CircuitState created = new CircuitState(settings, currentTimeMillis);
        states.put(config.getId(), created);
        return created;
    }

    public static final class CircuitPermit {
        private CircuitState state;
        private final boolean halfOpen;
        private final LongSupplier currentTimeMillis;

        private CircuitPermit(CircuitState state, boolean halfOpen, LongSupplier currentTimeMillis) {
            this.state = state;
            this.halfOpen = halfOpen;
            this.currentTimeMillis = currentTimeMillis;
        }

        private static CircuitPermit disabled() {
            return new CircuitPermit(null, false, System::currentTimeMillis);
        }

        public void success() {
            complete(true);
        }

        public void failure() {
            complete(false);
        }

        public String getState() {
            if (state == null) return "DISABLED";
            return halfOpen ? "HALF_OPEN" : "CLOSED";
        }

        private void complete(boolean success) {
            CircuitState current = state;
            if (current == null) return;
            state = null;
            current.record(success, halfOpen, currentTimeMillis.getAsLong());
        }
    }

    public static class OpenException extends IllegalStateException {
        private final String state;

        OpenException(String state) {
            super("外数API熔断器处于" + state + "状态");
            this.state = state;
        }

        public String getState() {
            return state;
        }
    }

    private static final class CircuitState {
        private final CircuitSettings settings;
        private final LongSupplier currentTimeMillis;
        private final Deque<Boolean> outcomes = new ArrayDeque<>();
        private String state = "CLOSED";
        private long openUntil;
        private int halfOpenInFlight;
        private int halfOpenSuccesses;

        private CircuitState(CircuitSettings settings, LongSupplier currentTimeMillis) {
            this.settings = settings;
            this.currentTimeMillis = currentTimeMillis;
        }

        private synchronized CircuitPermit acquire(long now) {
            if ("OPEN".equals(state)) {
                if (now < openUntil) throw new OpenException("OPEN");
                state = "HALF_OPEN";
                halfOpenInFlight = 0;
                halfOpenSuccesses = 0;
            }
            if ("HALF_OPEN".equals(state)) {
                if (halfOpenInFlight >= settings.halfOpenCalls) throw new OpenException("HALF_OPEN");
                halfOpenInFlight++;
                return new CircuitPermit(this, true, currentTimeMillis);
            }
            return new CircuitPermit(this, false, currentTimeMillis);
        }

        private synchronized void record(boolean success, boolean halfOpen, long now) {
            if (halfOpen) {
                if (halfOpenInFlight > 0) halfOpenInFlight--;
                if (!success) {
                    open(now);
                } else if (++halfOpenSuccesses >= settings.halfOpenCalls) {
                    close();
                }
                return;
            }
            if (!"CLOSED".equals(state)) return;
            outcomes.addLast(success);
            while (outcomes.size() > settings.windowSize) outcomes.removeFirst();
            if (outcomes.size() < settings.minCalls) return;
            int failures = 0;
            for (Boolean outcome : outcomes) if (!outcome) failures++;
            if (failures * 100D / outcomes.size() >= settings.failureRate) open(now);
        }

        private void open(long now) {
            state = "OPEN";
            openUntil = now + settings.openSeconds * 1000L;
            halfOpenInFlight = 0;
            halfOpenSuccesses = 0;
        }

        private void close() {
            state = "CLOSED";
            outcomes.clear();
            halfOpenInFlight = 0;
            halfOpenSuccesses = 0;
        }
    }

    private static final class CircuitSettings {
        private final int failureRate;
        private final int minCalls;
        private final int windowSize;
        private final int openSeconds;
        private final int halfOpenCalls;

        private CircuitSettings(int failureRate, int minCalls, int windowSize,
                                int openSeconds, int halfOpenCalls) {
            this.failureRate = failureRate;
            this.minCalls = minCalls;
            this.windowSize = windowSize;
            this.openSeconds = openSeconds;
            this.halfOpenCalls = halfOpenCalls;
        }

        private static CircuitSettings from(RuleExternalApiConfig config) {
            int minCalls = value(config.getCircuitMinCalls(), 20);
            return new CircuitSettings(value(config.getCircuitFailureRate(), 50), minCalls,
                    Math.max(minCalls, value(config.getCircuitWindowSize(), 50)),
                    value(config.getCircuitOpenSeconds(), 10), value(config.getCircuitHalfOpenCalls(), 5));
        }

        private static int value(Integer value, int defaultValue) {
            return value == null ? defaultValue : Math.max(1, value);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CircuitSettings)) return false;
            CircuitSettings that = (CircuitSettings) other;
            return failureRate == that.failureRate && minCalls == that.minCalls
                    && windowSize == that.windowSize && openSeconds == that.openSeconds
                    && halfOpenCalls == that.halfOpenCalls;
        }

        @Override
        public int hashCode() {
            int result = failureRate;
            result = 31 * result + minCalls;
            result = 31 * result + windowSize;
            result = 31 * result + openSeconds;
            return 31 * result + halfOpenCalls;
        }
    }
}
