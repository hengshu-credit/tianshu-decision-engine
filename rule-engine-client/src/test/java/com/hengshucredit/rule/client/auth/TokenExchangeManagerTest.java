package com.hengshucredit.rule.client.auth;

import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.Assert.assertEquals;

public class TokenExchangeManagerTest {

    @Test
    public void exchangesOnceAndReusesTokenBeforeRefreshWindow() throws Exception {
        Instant issued = Instant.parse("2026-07-15T00:00:00Z");
        FakeTokenExchangeManager manager = manager(issued);
        manager.responses.add(state("token-a", issued.plusSeconds(7200), issued.plusSeconds(7800)));

        assertEquals("token-a", manager.getAccessToken());
        manager.now = issued.plusSeconds(3600);
        assertEquals("token-a", manager.getAccessToken());
        assertEquals(1, manager.exchangeCount);
    }

    @Test
    public void refreshesSixtySecondsBeforeExpiration() throws Exception {
        Instant issued = Instant.parse("2026-07-15T00:00:00Z");
        FakeTokenExchangeManager manager = manager(issued);
        manager.responses.add(state("token-a", issued.plusSeconds(7200), issued.plusSeconds(7800)));
        manager.responses.add(state("token-b", issued.plusSeconds(14400), issued.plusSeconds(15000)));

        assertEquals("token-a", manager.getAccessToken());
        manager.now = issued.plusSeconds(7140);

        assertEquals("token-b", manager.getAccessToken());
        assertEquals(2, manager.exchangeCount);
    }

    @Test
    public void failedRefreshKeepsOldTokenThroughGracePeriod() throws Exception {
        Instant issued = Instant.parse("2026-07-15T00:00:00Z");
        FakeTokenExchangeManager manager = manager(issued);
        manager.responses.add(state("token-a", issued.plusSeconds(7200), issued.plusSeconds(7800)));

        assertEquals("token-a", manager.getAccessToken());
        manager.now = issued.plusSeconds(7500);
        manager.failure = new IOException("temporary outage");

        assertEquals("token-a", manager.getAccessToken());
        assertEquals(2, manager.exchangeCount);

        manager.now = manager.now.plusSeconds(1);
        assertEquals("token-a", manager.getAccessToken());
        assertEquals(2, manager.exchangeCount);

        manager.now = manager.now.plusSeconds(4);
        assertEquals("token-a", manager.getAccessToken());
        assertEquals(3, manager.exchangeCount);
    }

    @Test(expected = IOException.class)
    public void failedRefreshAfterGraceDoesNotSendExpiredToken() throws Exception {
        Instant issued = Instant.parse("2026-07-15T00:00:00Z");
        FakeTokenExchangeManager manager = manager(issued);
        manager.responses.add(state("token-a", issued.plusSeconds(7200), issued.plusSeconds(7800)));
        manager.getAccessToken();
        manager.now = issued.plusSeconds(7801);
        manager.failure = new IOException("temporary outage");

        manager.getAccessToken();
    }

    private FakeTokenExchangeManager manager(Instant now) {
        ClientAuthConfig config = ClientAuthConfig.basic("partner", "secret");
        config.setRefreshAheadSeconds(60);
        return new FakeTokenExchangeManager(config, now);
    }

    private TokenExchangeManager.TokenState state(String token, Instant expiresAt,
                                                   Instant graceExpiresAt) {
        return new TokenExchangeManager.TokenState(token, expiresAt, graceExpiresAt);
    }

    private static class FakeTokenExchangeManager extends TokenExchangeManager {
        private final Queue<TokenState> responses = new ArrayDeque<>();
        private Instant now;
        private IOException failure;
        private int exchangeCount;

        private FakeTokenExchangeManager(ClientAuthConfig config, Instant now) {
            super("http://localhost:8080", 1000, config);
            this.now = now;
        }

        @Override
        protected TokenState requestToken() throws IOException {
            exchangeCount++;
            if (failure != null) throw failure;
            return responses.remove();
        }

        @Override
        protected Instant currentInstant() {
            return now;
        }
    }
}
